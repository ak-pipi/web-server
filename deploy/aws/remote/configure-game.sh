#!/usr/bin/env bash
# Executed as root by AWS Systems Manager after the game image has been pushed
# to ECR. Public WSS is Nginx:443, with Nginx:9098 kept for compatibility;
# C++ listens privately on 19098.
set -Eeuo pipefail
IFS=$'\n\t'

dnf install -y jq awscli docker nginx
systemctl enable --now docker nginx

CONFIG_JSON="$(printf '%s' "${NIUMA_CONFIG_B64:?missing deployment configuration}" | base64 -d)"
value() { jq -er --arg key "$1" '.[$key]' <<< "$CONFIG_JSON"; }

REGION="$(value region)"
DB_ENDPOINT="$(value databaseEndpoint)"
DB_SECRET_ARN="$(value databaseSecretArn)"
REDIS_SECRET_ARN="$(value redisSecretArn)"
RABBIT_SECRET_ARN="$(value rabbitSecretArn)"
WEB_PRIVATE_IP="$(value webPrivateIp)"
GAME_IMAGE="$(value gameImage)"
GAME_DOMAIN="$(value gameDomain)"
TLS_EMAIL="$(value tlsEmail)"
export AWS_DEFAULT_REGION="$REGION"

secret_value() {
  aws secretsmanager get-secret-value --secret-id "$1" --query SecretString --output text
}
DB_SECRET="$(secret_value "$DB_SECRET_ARN")"
REDIS_SECRET="$(secret_value "$REDIS_SECRET_ARN")"
RABBIT_SECRET="$(secret_value "$RABBIT_SECRET_ARN")"
DB_USER="$(jq -er '.username' <<< "$DB_SECRET")"
DB_PASSWORD="$(jq -er '.password' <<< "$DB_SECRET")"
REDIS_PASSWORD="$(jq -er '.password' <<< "$REDIS_SECRET")"
RABBIT_PASSWORD="$(jq -er '.password' <<< "$RABBIT_SECRET")"

install -d -m 0750 /opt/niuma/game/config /opt/niuma/game/log
cat > /opt/niuma/game/config/server.ini <<EOF
[Server]
server_id=game_server_001
port=10086
thread_num=4
timer_threads=3
access_address=${GAME_DOMAIN}:10086
ws_address=wss://${GAME_DOMAIN}/
outter_threads=3
inner_threads=4

[Websocket]
port=19098
thread_num=2

[Mysql]
host=tcp://${DB_ENDPOINT}:3306
username=${DB_USER}
password=${DB_PASSWORD}
schema=niuma
keep_connections=3
max_connections=20
thread_num=2

[Redis]
host=${WEB_PRIVATE_IP}
port=6379
password=${REDIS_PASSWORD}
database=9
keep_connections=3
max_connections=20

[RabbitMQ]
host=${WEB_PRIVATE_IP}
port=5672
vhost=niuma
username=niuma
password=${RABBIT_PASSWORD}
fanout_exchange=game.fanout
fanout_queue=game.fanout.queue.001
fanout_consumer_tag=game.fanout.tag
direct_exchange=game.direct
direct_queue=game.direct.queue.001
direct_consumer_tag=game.direct.tag.001
EOF
chmod 0600 /opt/niuma/game/config/server.ini

cat > /opt/niuma/game/game.env <<EOF
export AWS_DEFAULT_REGION=$(printf '%q' "$REGION")
export GAME_IMAGE=$(printf '%q' "$GAME_IMAGE")
EOF
chmod 0600 /opt/niuma/game/game.env
cat > /usr/local/sbin/run-niuma-game.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
source /opt/niuma/game/game.env
registry="${GAME_IMAGE%%/*}"
aws ecr get-login-password --region "$AWS_DEFAULT_REGION" | docker login --username AWS --password-stdin "$registry"
docker pull "$GAME_IMAGE"
docker rm -f niuma-game >/dev/null 2>&1 || true
exec docker run --name niuma-game --network host --restart unless-stopped \
  --mount type=bind,src=/opt/niuma/game/config/server.ini,dst=/app/server.ini,readonly \
  --mount type=bind,src=/opt/niuma/game/log,dst=/app/log \
  --log-driver json-file --log-opt max-size=100m --log-opt max-file=3 \
  "$GAME_IMAGE"
EOF
chmod 0750 /usr/local/sbin/run-niuma-game.sh
cat > /etc/systemd/system/niuma-game.service <<'UNIT'
[Unit]
Description=NiuMa C++ game server
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/sbin/run-niuma-game.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
# `enable --now` does not restart an already-active service, so an existing
# deployment would otherwise keep running the previous image tag.
systemctl enable niuma-game
systemctl restart niuma-game

rm -f /etc/nginx/conf.d/default.conf
install -d -m 0755 /var/lib/letsencrypt
cat > /etc/nginx/conf.d/niuma-game.conf <<EOF
server {
    listen 80;
    server_name ${GAME_DOMAIN};
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
  -d "$GAME_DOMAIN"

cat > /etc/nginx/conf.d/niuma-game.conf <<EOF
server {
    listen 80;
    server_name ${GAME_DOMAIN};
    location /.well-known/acme-challenge/ { root /var/lib/letsencrypt; }
    location / { return 404; }
}
server {
    listen 443 ssl;
    listen 9098 ssl;
    server_name ${GAME_DOMAIN};
    ssl_certificate /etc/letsencrypt/live/${GAME_DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${GAME_DOMAIN}/privkey.pem;
    location / {
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_pass http://127.0.0.1:19098;
    }
}
EOF
cat > /usr/local/sbin/renew-niuma-game-cert.sh <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/lib/letsencrypt:/var/www/certbot certbot/certbot:latest renew --webroot -w /var/www/certbot
systemctl reload nginx
EOF
chmod 0750 /usr/local/sbin/renew-niuma-game-cert.sh
install -d -m 0755 /etc/cron.d
printf '37 3 * * * root /usr/local/sbin/renew-niuma-game-cert.sh\n' > /etc/cron.d/niuma-game-certbot
nginx -t && systemctl reload nginx
systemctl --no-pager --full status niuma-game
