#!/usr/bin/env bash
# Executed as root by AWS Systems Manager. Configuration reaches this script as
# base64 JSON in NIUMA_CONFIG_B64; it is never persisted on the workstation.
set -Eeuo pipefail
IFS=$'\n\t'

dnf install -y jq awscli java-11-amazon-corretto-headless docker nginx
systemctl enable --now docker nginx

CONFIG_JSON="$(printf '%s' "${NIUMA_CONFIG_B64:?missing deployment configuration}" | base64 -d)"
value() { jq -er --arg key "$1" '.[$key]' <<< "$CONFIG_JSON"; }

REGION="$(value region)"
BUCKET="$(value bucket)"
WEB_JAR_KEY="$(value webJarKey)"
SCHEMA_KEY="$(value schemaKey)"
MIGRATION_PREFIX="$(value migrationPrefix)"
MIGRATION_MANIFEST_KEY="$(value migrationManifestKey)"
MIGRATION_BASELINE="$(value migrationBaseline)"
DB_ENDPOINT="$(value databaseEndpoint)"
DB_SECRET_ARN="$(value databaseSecretArn)"
REDIS_SECRET_ARN="$(value redisSecretArn)"
RABBIT_SECRET_ARN="$(value rabbitSecretArn)"
APP_SECRET_ARN="$(value applicationSecretArn)"
API_DOMAIN="$(value apiDomain)"
TLS_EMAIL="$(value tlsEmail)"
INITIALIZE_DATABASE="$(value initializeDatabase)"
RESET_PLAYER_DATA="$(value resetPlayerData)"
export AWS_DEFAULT_REGION="$REGION"

secret_value() {
  aws secretsmanager get-secret-value --secret-id "$1" --query SecretString --output text
}
DB_SECRET="$(secret_value "$DB_SECRET_ARN")"
REDIS_SECRET="$(secret_value "$REDIS_SECRET_ARN")"
RABBIT_SECRET="$(secret_value "$RABBIT_SECRET_ARN")"
APP_SECRET="$(secret_value "$APP_SECRET_ARN")"
DB_USER="$(jq -er '.username' <<< "$DB_SECRET")"
DB_PASSWORD="$(jq -er '.password' <<< "$DB_SECRET")"
REDIS_PASSWORD="$(jq -er '.password' <<< "$REDIS_SECRET")"
RABBIT_PASSWORD="$(jq -er '.password' <<< "$RABBIT_SECRET")"
TOKEN_SECRET="$(jq -er '.tokenSecret' <<< "$APP_SECRET")"

install -d -m 0750 /opt/niuma/web /opt/niuma/data/redis /opt/niuma/data/rabbit /opt/niuma/upload
aws s3 cp "s3://${BUCKET}/${WEB_JAR_KEY}" /opt/niuma/web/niuma-admin.jar
chmod 0640 /opt/niuma/web/niuma-admin.jar

# Redis and RabbitMQ intentionally live on this small web EC2 in phase 1. Their
# ports are reachable only from the game security group, never from the internet.
remove_container_if_exists() {
  local name="$1"
  for _ in $(seq 1 10); do
    if ! docker ps -a --format '{{.Names}}' | grep -Fxq "$name"; then
      return 0
    fi
    docker rm -f "$name" >/dev/null 2>&1 || true
    sleep 1
  done
  printf '容器名称仍被占用，无法重建: %s\n' "$name" >&2
  docker ps -a --filter "name=^/${name}$" >&2 || true
  exit 1
}
remove_container_if_exists niuma-redis
remove_container_if_exists niuma-rabbit
docker run -d --name niuma-redis --restart unless-stopped \
  -p 6379:6379 -v /opt/niuma/data/redis:/data \
  redis:7.4-alpine redis-server --appendonly yes --requirepass "$REDIS_PASSWORD"
docker run -d --name niuma-rabbit --restart unless-stopped \
  -p 5672:5672 -v /opt/niuma/data/rabbit:/var/lib/rabbitmq \
  -e RABBITMQ_DEFAULT_USER=niuma \
  -e RABBITMQ_DEFAULT_PASS="$RABBIT_PASSWORD" \
  -e RABBITMQ_DEFAULT_VHOST=niuma \
  rabbitmq:3.13-management

for _ in $(seq 1 30); do
  docker exec niuma-rabbit rabbitmq-diagnostics -q ping >/dev/null 2>&1 && break
  sleep 2
done
docker exec niuma-rabbit rabbitmq-diagnostics -q ping >/dev/null
# The broker can accept AMQP connections before the management HTTP API used by
# rabbitmqadmin has finished binding. Wait for that endpoint as well.
rabbitmq_admin() {
  docker exec niuma-rabbit rabbitmqadmin \
    --username niuma --password "$RABBIT_PASSWORD" --vhost niuma "$@"
}
for _ in $(seq 1 30); do
  rabbitmq_admin list exchanges >/dev/null 2>&1 && break
  sleep 2
done
rabbitmq_admin list exchanges >/dev/null
# The game service binds its own queues, but its two shared exchanges must
# already exist when it starts.
rabbitmq_admin declare exchange name=game.fanout type=fanout durable=true
rabbitmq_admin declare exchange name=game.direct type=direct durable=true
# These listener queues are consumed by the Java application. They are kept
# unbound here because their producers use dedicated routing rules outside the
# game server's shared `web_server_001` direct route.
for queue_name in game.settle.queue game.settle.dlq risk.data.queue risk.data.dlq; do
  rabbitmq_admin declare queue name="$queue_name" durable=true
done

mysql_client() {
  # `docker run -i` inherits this function's standard input. Keep it only for
  # SQL-file imports; otherwise an `-e` query inside a `while read` loop would
  # consume the remaining migration manifest.
  local docker_args=(--rm --network host -e MYSQL_PWD)
  if [[ "$#" -eq 0 ]]; then
    docker_args+=(-i)
  fi
  MYSQL_PWD="$DB_PASSWORD" docker run "${docker_args[@]}" \
    mysql:8.0 mysql --default-character-set=utf8mb4 --ssl-mode=REQUIRED \
    -h "$DB_ENDPOINT" -u "$DB_USER" niuma "$@"
}

if [[ "$INITIALIZE_DATABASE" == '1' ]]; then
  # The supplied dump contains DROP TABLE statements. This is deliberately an
  # explicit first-install action, not a normal release action.
  install -d -m 0700 /opt/niuma/sql
  aws s3 cp "s3://${BUCKET}/${SCHEMA_KEY}" /opt/niuma/sql/niuma.sql
  mysql_client < /opt/niuma/sql/niuma.sql
fi

# 正常发布也执行新增迁移。旧部署没有迁移记录时，先登记至 v13 基线，
# 以免对既有数据库重复执行早期非幂等脚本；v14 及之后的脚本会自动执行一次。
install -d -m 0700 /opt/niuma/sql/migrations
aws s3 cp "s3://${BUCKET}/${MIGRATION_MANIFEST_KEY}" /opt/niuma/sql/migrations.txt
mysql_client -e "CREATE TABLE IF NOT EXISTS schema_migration (migration_name varchar(128) NOT NULL, applied_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (migration_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布 SQL 迁移记录'"
MIGRATION_BASELINE_APPLIED="$(mysql_client -N -B -e "SELECT COUNT(*) FROM schema_migration WHERE migration_name = '${MIGRATION_BASELINE}'")"

# A failed bootstrap may leave the tracker table with just its first marker.
# Treat any existing database without the complete baseline identically to a
# database without a tracker, so retries do not run old migrations half-way.
if [[ "$INITIALIZE_DATABASE" != '1' && "$MIGRATION_BASELINE_APPLIED" == '0' ]]; then
  baseline_found=0
  while IFS= read -r migration_name; do
    [[ -n "$migration_name" && "$migration_name" != \#* ]] || continue
    [[ "$migration_name" =~ ^[A-Za-z0-9._-]+\.sql$ ]] || {
      printf '非法 SQL 迁移文件名: %s\n' "$migration_name" >&2
      exit 1
    }
    mysql_client -e "INSERT IGNORE INTO schema_migration (migration_name) VALUES ('${migration_name}')"
    if [[ "$migration_name" == "$MIGRATION_BASELINE" ]]; then
      baseline_found=1
      break
    fi
  done < /opt/niuma/sql/migrations.txt
  [[ "$baseline_found" == '1' ]] || {
    printf '未在迁移清单中找到基线 SQL: %s\n' "$MIGRATION_BASELINE" >&2
    exit 1
  }
  printf '已为存量数据库登记迁移基线：%s\n' "$MIGRATION_BASELINE"
fi

while IFS= read -r migration_name; do
  [[ -n "$migration_name" && "$migration_name" != \#* ]] || continue
  [[ "$migration_name" =~ ^[A-Za-z0-9._-]+\.sql$ ]] || {
    printf '非法 SQL 迁移文件名: %s\n' "$migration_name" >&2
    exit 1
  }
  applied="$(mysql_client -N -B -e "SELECT COUNT(*) FROM schema_migration WHERE migration_name = '${migration_name}'")"
  if [[ "$applied" != '0' ]]; then
    printf 'SQL 迁移已执行，跳过：%s\n' "$migration_name"
    continue
  fi
  migration="/opt/niuma/sql/migrations/${migration_name}"
  aws s3 cp "s3://${BUCKET}/${MIGRATION_PREFIX}/${migration_name}" "$migration"
  printf '执行 SQL 迁移：%s\n' "$migration_name"
  mysql_client < "$migration"
  mysql_client -e "INSERT IGNORE INTO schema_migration (migration_name) VALUES ('${migration_name}')"
done < /opt/niuma/sql/migrations.txt

if [[ "$RESET_PLAYER_DATA" == '1' ]]; then
  printf '已执行玩家数据重置迁移；确认该操作仅用于新规则重置场景。\n'
fi

write_env() { printf 'export %s=%q\n' "$1" "$2"; }
{
  # application-druid.yml contains the shared Druid pool defaults; the
  # database endpoint and credentials below override its local values.
  write_env SPRING_PROFILES_ACTIVE druid
  write_env SPRING_DEVTOOLS_RESTART_ENABLED false
  write_env SPRING_DATASOURCE_DRUID_MASTER_URL "jdbc:mysql://${DB_ENDPOINT}:3306/niuma?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=UTC"
  write_env SPRING_DATASOURCE_DRUID_MASTER_USERNAME "$DB_USER"
  write_env SPRING_DATASOURCE_DRUID_MASTER_PASSWORD "$DB_PASSWORD"
  write_env SPRING_RABBITMQ_HOST 127.0.0.1
  write_env SPRING_RABBITMQ_PORT 5672
  write_env SPRING_RABBITMQ_USERNAME niuma
  write_env SPRING_RABBITMQ_PASSWORD "$RABBIT_PASSWORD"
  write_env SPRING_RABBITMQ_VIRTUAL_HOST niuma
  write_env SPRING_REDIS_HOST 127.0.0.1
  write_env SPRING_REDIS_PORT 6379
  write_env SPRING_REDIS_PASSWORD "$REDIS_PASSWORD"
  write_env SPRING_REDIS_DATABASE 9
  write_env RABBITMQ_GAME_EXCHANGE game.direct
  write_env RABBITMQ_GAME_QUEUE game.direct.queue.web001
  write_env RABBITMQ_GAME_ROUTINGKEY web_server_001
  write_env TOKEN_SECRET "$TOKEN_SECRET"
  write_env RUOYI_PROFILE /opt/niuma/upload
  write_env RATE_LIMIT_ENABLED true
  write_env SWAGGER_ENABLED false
} > /opt/niuma/web/application.env
chmod 0600 /opt/niuma/web/application.env

cat > /etc/systemd/system/niuma-web.service <<'UNIT'
[Unit]
Description=NiuMa Java Web service
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/niuma/web
ExecStart=/bin/bash -lc 'source /opt/niuma/web/application.env; exec /usr/bin/java -Xms512m -Xmx2g -jar /opt/niuma/web/niuma-admin.jar'
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
# `enable --now` leaves an already-active service on the previous JAR. Every
# application release must restart it after the new artifact is downloaded.
systemctl enable niuma-web
systemctl restart niuma-web

# First expose the ACME HTTP challenge. The full TLS proxy is written only after
# a certificate has been successfully issued.
rm -f /etc/nginx/conf.d/default.conf
install -d -m 0755 /var/lib/letsencrypt
cat > /etc/nginx/conf.d/niuma-web.conf <<EOF
server {
    listen 80;
    server_name ${API_DOMAIN};
    location /.well-known/acme-challenge/ { root /var/lib/letsencrypt; }
    location / { return 404; }
}
EOF
nginx -t && systemctl reload nginx

CERTBOT_CONTACT_ARGS=(--non-interactive --agree-tos)
if [[ -n "$TLS_EMAIL" ]]; then
  CERTBOT_CONTACT_ARGS+=(--email "$TLS_EMAIL")
else
  CERTBOT_CONTACT_ARGS+=(--register-unsafely-without-email)
fi
docker run --rm \
  -v /etc/letsencrypt:/etc/letsencrypt \
  -v /var/lib/letsencrypt:/var/www/certbot \
  certbot/certbot:latest certonly --webroot -w /var/www/certbot \
  "${CERTBOT_CONTACT_ARGS[@]}" \
  -d "$API_DOMAIN"

cat > /etc/nginx/conf.d/niuma-web.conf <<EOF
server {
    listen 80;
    server_name ${API_DOMAIN};
    location /.well-known/acme-challenge/ { root /var/lib/letsencrypt; }
    location / { return 301 https://\$host\$request_uri; }
}
server {
    listen 443 ssl http2;
    server_name ${API_DOMAIN};
    ssl_certificate /etc/letsencrypt/live/${API_DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${API_DOMAIN}/privkey.pem;
    location / {
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_http_version 1.1;
        proxy_pass http://127.0.0.1:18080;
    }
}
EOF
cat > /usr/local/sbin/renew-niuma-web-cert.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/lib/letsencrypt:/var/www/certbot certbot/certbot:latest renew --webroot -w /var/www/certbot
systemctl reload nginx
EOF
chmod 0750 /usr/local/sbin/renew-niuma-web-cert.sh
install -d -m 0755 /etc/cron.d
printf '23 3 * * * root /usr/local/sbin/renew-niuma-web-cert.sh\n' > /etc/cron.d/niuma-web-certbot
nginx -t && systemctl reload nginx
systemctl --no-pager --full status niuma-web
