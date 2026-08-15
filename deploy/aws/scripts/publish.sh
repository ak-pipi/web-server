#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
AWS_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
WEB_SERVER_DIR="$(cd -- "${AWS_DIR}/../.." && pwd)"
WORKSPACE_DIR="$(cd -- "${WEB_SERVER_DIR}/../.." && pwd)"
CONFIG_FILE="${AWS_DIR}/config.env"

die() { printf '错误: %s\n' "$*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null || die "缺少命令: $1"; }
for command_name in aws docker jq mvn base64 tar; do require_command "$command_name"; done
[[ -f "$CONFIG_FILE" ]] || die "请先创建 ${CONFIG_FILE}。"

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${AWS_REGION:?config.env 必须设置 AWS_REGION}"
: "${STACK_NAME:=NiuMaCostSaver}"
: "${API_DOMAIN:?config.env 必须设置 API_DOMAIN}"
: "${GAME_DOMAIN:?config.env 必须设置 GAME_DOMAIN}"
: "${TLS_EMAIL:=}"
: "${INITIALIZE_DATABASE:=0}"
: "${RESET_PLAYER_DATA:=0}"
: "${SKIP_DNS_CHECK:=0}"
: "${GAME_IMAGE_LOCAL:=}"
: "${REMOTE_GAME_BUILD:=auto}"
: "${PUBLISH_WEB_UI:=1}"
: "${MIGRATION_BASELINE:=v13_add_system_log_tables.sql}"
: "${GAME_SERVER_DIR:=${WORKSPACE_DIR}/server}"
: "${SQL_DIR:=${WEB_SERVER_DIR}/sql}"
: "${EXPECTED_AWS_ACCOUNT_ID:=}"
: "${EXPECTED_DEPLOY_ROLE:=}"
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"
[[ -z "${AWS_PROFILE:-}" ]] || export AWS_PROFILE
[[ "$INITIALIZE_DATABASE" == '0' || "$INITIALIZE_DATABASE" == '1' ]] || die "INITIALIZE_DATABASE 只能是 0 或 1"
[[ "$RESET_PLAYER_DATA" == '0' || "$RESET_PLAYER_DATA" == '1' ]] || die "RESET_PLAYER_DATA 只能是 0 或 1"
[[ "$REMOTE_GAME_BUILD" == 'auto' || "$REMOTE_GAME_BUILD" == '0' || "$REMOTE_GAME_BUILD" == '1' ]] \
  || die "REMOTE_GAME_BUILD 只能是 auto、0 或 1"
[[ "$PUBLISH_WEB_UI" == '0' || "$PUBLISH_WEB_UI" == '1' ]] \
  || die "PUBLISH_WEB_UI 只能是 0 或 1"
if [[ "$PUBLISH_WEB_UI" == '1' ]]; then
  require_command npm
fi
[[ -d "$GAME_SERVER_DIR" ]] || die "未找到 C++ 游戏服工程目录：${GAME_SERVER_DIR}"
[[ -d "$SQL_DIR" ]] || die "未找到 SQL 目录：${SQL_DIR}"
GAME_SERVER_DIR="$(cd -- "$GAME_SERVER_DIR" && pwd)"
SQL_DIR="$(cd -- "$SQL_DIR" && pwd)"
[[ -d "${WEB_SERVER_DIR}/niuma-admin" ]] || die "未找到 web_server 工程：${WEB_SERVER_DIR}"
[[ -d "${GAME_SERVER_DIR}/Server" ]] || die "C++ 游戏服工程目录不正确：${GAME_SERVER_DIR}"
[[ -f "${SQL_DIR}/niuma.sql" ]] || die "未找到初始化 SQL：${SQL_DIR}/niuma.sql"

CURRENT_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
[[ -z "$EXPECTED_AWS_ACCOUNT_ID" || "$EXPECTED_AWS_ACCOUNT_ID" == "$CURRENT_ACCOUNT_ID" ]] || \
  die "当前 AWS 账户为 ${CURRENT_ACCOUNT_ID}，但 config.env 要求 ${EXPECTED_AWS_ACCOUNT_ID}。已停止发布。"
if [[ -n "$EXPECTED_DEPLOY_ROLE" ]]; then
  [[ "$CALLER_ARN" == "arn:aws:sts::${CURRENT_ACCOUNT_ID}:assumed-role/${EXPECTED_DEPLOY_ROLE}/"* ]] || \
    die "当前身份不是预期的 assumed-role/${EXPECTED_DEPLOY_ROLE}：${CALLER_ARN}"
fi
printf '将使用 AWS 账户 %s，身份 %s 发布到 %s。\n' "$CURRENT_ACCOUNT_ID" "$CALLER_ARN" "$AWS_REGION"

STACK_JSON="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --output json)"
stack_output() {
  jq -er --arg key "$1" '.Stacks[0].Outputs[] | select(.OutputKey == $key) | .OutputValue' <<< "$STACK_JSON"
}

ARTIFACT_BUCKET="$(stack_output ArtifactBucketName)"
GAME_REPOSITORY="$(stack_output GameEcrRepositoryUri)"
WEB_INSTANCE_ID="$(stack_output WebInstanceId)"
GAME_INSTANCE_ID="$(stack_output GameInstanceId)"
WEB_EIP="$(stack_output WebElasticIp)"
GAME_EIP="$(stack_output GameElasticIp)"
WEB_PRIVATE_IP="$(stack_output WebPrivateIp)"
DATABASE_ENDPOINT="$(stack_output DatabaseEndpoint)"
DATABASE_SECRET_ARN="$(stack_output DatabaseSecretArn)"
REDIS_SECRET_ARN="$(stack_output RedisSecretArn)"
RABBIT_SECRET_ARN="$(stack_output RabbitSecretArn)"
APPLICATION_SECRET_ARN="$(stack_output ApplicationSecretArn)"

check_dns() {
  local domain="$1" expected_ip="$2" resolved
  resolved="$(dig +short A "$domain" 2>/dev/null || true)"
  grep -Fxq "$expected_ip" <<< "$resolved" || die "${domain} 尚未解析到 ${expected_ip}；请先设置 DNS A 记录，或在确认后设置 SKIP_DNS_CHECK=1。"
}
if [[ "$SKIP_DNS_CHECK" != '1' ]]; then
  require_command dig
  check_dns "$API_DOMAIN" "$WEB_EIP"
  check_dns "$GAME_DOMAIN" "$GAME_EIP"
fi

RELEASE="$(date -u +%Y%m%dT%H%M%SZ)"
WEB_JAR_KEY="releases/${RELEASE}/niuma-admin.jar"
SCHEMA_KEY="releases/${RELEASE}/sql/niuma.sql"
MIGRATION_PREFIX="releases/${RELEASE}/sql/migrations"
MIGRATION_MANIFEST_KEY="releases/${RELEASE}/sql/migrations.txt"
GAME_IMAGE="${GAME_REPOSITORY}:${RELEASE}"
GAME_SOURCE_KEY="releases/${RELEASE}/game/source.tar.gz"
GAME_DOCKERFILE_KEY="releases/${RELEASE}/game/Dockerfile.game"
GAME_BUILD_LOG_KEY="releases/${RELEASE}/game/docker-build.log"

SQL_MIGRATIONS=(
  "${SQL_DIR}/v2_upgrade_step1.sql"
  "${SQL_DIR}/v2_add_regional_games.sql"
  "${SQL_DIR}/v3_add_rule_config.sql"
  "${SQL_DIR}/v4_add_record_tables.sql"
  "${SQL_DIR}/v5_add_doudizhu.sql"
  "${SQL_DIR}/v6_add_game_record_tables.sql"
  "${SQL_DIR}/v7_game_record_retention.sql"
  "${SQL_DIR}/v7_fix_doudizhu_two_player_rule.sql"
  "${SQL_DIR}/v8_fix_doudizhu_standard_hand_count.sql"
  "${SQL_DIR}/v9_fix_paodekuai_turn_timeout.sql"
  "${SQL_DIR}/v10_agency_commission.sql"
  "${SQL_DIR}/v11_fixed_score_and_room_options.sql"
)
if [[ "$RESET_PLAYER_DATA" == '1' ]]; then
  SQL_MIGRATIONS+=("${SQL_DIR}/v12_reset_players_for_new_rules.sql")
fi
SQL_MIGRATIONS+=(
  "${SQL_DIR}/v13_add_system_log_tables.sql"
  "${SQL_DIR}/v14_agent_workbench_and_menu_cleanup.sql"
  "${SQL_DIR}/v15_fix_game_management_menu_encoding.sql"
  "${SQL_DIR}/v16_permanent_agency_invite_codes.sql"
)
if [[ "$RESET_PLAYER_DATA" == '1' ]]; then
  SQL_MIGRATIONS+=("${SQL_DIR}/v17_player_id_invite_binding_reset.sql")
fi
SQL_MIGRATIONS+=("${SQL_DIR}/v18_restore_register_invite_codes.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v19_unify_super_admin_login.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v20_player_game_restrictions.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v21_member_remark.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v22_shuffle_fee_income_box.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v23_remove_unused_legacy_games.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v24_restore_regional_single_round_districts.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v25_income_box_collect_id.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v26_income_box_partial_withdraw.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v27_fix_regional_record_time.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v28_rename_regional_district_display_names.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v29_fix_taojiang_district_labels.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v30_paodekuai_rule_options.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v31_paodekuai_score_scale.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v32_fix_regional_round_count_and_record_labels.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v33_paodekuai_min_carry_room_settlement.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v34_paodekuai_score_scale_all_stakes.sql")
SQL_MIGRATIONS+=("${SQL_DIR}/v35_decimal_gold_and_cash_pledge.sql")

validate_sql_migration_manifest() {
  local migration migration_name listed listed_path
  for migration in "${SQL_DIR}"/v*.sql; do
    [[ -e "$migration" ]] || continue
    migration_name="$(basename "$migration")"
    if [[ "$RESET_PLAYER_DATA" != '1' ]]; then
      case "$migration_name" in
        v12_reset_players_for_new_rules.sql|v17_player_id_invite_binding_reset.sql)
          continue
          ;;
      esac
    fi
    listed=0
    for listed_path in "${SQL_MIGRATIONS[@]}"; do
      if [[ "$(basename "$listed_path")" == "$migration_name" ]]; then
        listed=1
        break
      fi
    done
    [[ "$listed" == '1' ]] || die "SQL 迁移文件未加入发布清单：${migration_name}"
  done
}

validate_sql_migration_manifest

printf '构建 Java Web 制品…\n'
(cd "${WEB_SERVER_DIR}" && mvn -q -DskipTests clean package)
aws s3 cp --no-progress "${WEB_SERVER_DIR}/niuma-admin/target/niuma-admin.jar" "s3://${ARTIFACT_BUCKET}/${WEB_JAR_KEY}"
aws s3 cp --no-progress "${SQL_DIR}/niuma.sql" "s3://${ARTIFACT_BUCKET}/${SCHEMA_KEY}"
MIGRATION_MANIFEST_FILE="$(mktemp)"
trap 'rm -f "$MIGRATION_MANIFEST_FILE" "${GAME_SOURCE_ARCHIVE:-}"' EXIT
: > "$MIGRATION_MANIFEST_FILE"
for migration in "${SQL_MIGRATIONS[@]}"; do
  [[ -f "$migration" ]] || die "SQL 迁移文件不存在：${migration}"
  aws s3 cp --no-progress "$migration" "s3://${ARTIFACT_BUCKET}/${MIGRATION_PREFIX}/$(basename "$migration")"
  printf '%s\n' "$(basename "$migration")" >> "$MIGRATION_MANIFEST_FILE"
done
aws s3 cp --no-progress "$MIGRATION_MANIFEST_FILE" "s3://${ARTIFACT_BUCKET}/${MIGRATION_MANIFEST_KEY}"

can_run_amd64_container() {
  docker run --rm --platform linux/amd64 public.ecr.aws/amazonlinux/amazonlinux:2023 /bin/true >/dev/null 2>&1
}

upload_game_source() {
  GAME_SOURCE_ARCHIVE="$(mktemp "${TMPDIR:-/tmp}/niuma-game-source.XXXXXX")"
  LC_ALL=C COPYFILE_DISABLE=1 tar --format=ustar \
    --exclude='.git' \
    --exclude='build' \
    --exclude='build_output' \
    --exclude='cmake-build-*' \
    -czf "$GAME_SOURCE_ARCHIVE" \
    -C "$GAME_SERVER_DIR" .
  aws s3 cp --no-progress "$GAME_SOURCE_ARCHIVE" "s3://${ARTIFACT_BUCKET}/${GAME_SOURCE_KEY}"
  aws s3 cp --no-progress "${AWS_DIR}/Dockerfile.game" "s3://${ARTIFACT_BUCKET}/${GAME_DOCKERFILE_KEY}"
}

run_remote_game_build() {
  local build_config_json build_config_b64
  build_config_json="$(jq -cn \
    --arg region "$AWS_REGION" \
    --arg bucket "$ARTIFACT_BUCKET" \
    --arg gameImage "$GAME_IMAGE" \
    --arg gameSourceKey "$GAME_SOURCE_KEY" \
    --arg gameDockerfileKey "$GAME_DOCKERFILE_KEY" \
    --arg gameBuildLogKey "$GAME_BUILD_LOG_KEY" \
    '{region:$region,bucket:$bucket,gameImage:$gameImage,gameSourceKey:$gameSourceKey,gameDockerfileKey:$gameDockerfileKey,gameBuildLogKey:$gameBuildLogKey}')"
  build_config_b64="$(printf '%s' "$build_config_json" | base64 | tr -d '\n')"
  "${SCRIPT_DIR}/ssm-run.sh" "$GAME_INSTANCE_ID" "${AWS_DIR}/remote/build-game-image.sh" "$build_config_b64"
}

printf '构建并推送 linux/amd64 游戏服镜像…\n'
if [[ -n "$GAME_IMAGE_LOCAL" ]]; then
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${CURRENT_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  docker image inspect "$GAME_IMAGE_LOCAL" >/dev/null \
    || die "指定的本地游戏镜像不存在: ${GAME_IMAGE_LOCAL}"
  printf '复用已验证的本地游戏镜像 %s。\n' "$GAME_IMAGE_LOCAL"
  docker tag "$GAME_IMAGE_LOCAL" "$GAME_IMAGE"
  docker push "$GAME_IMAGE"
else
  USE_REMOTE_GAME_BUILD="$REMOTE_GAME_BUILD"
  if [[ "$USE_REMOTE_GAME_BUILD" == 'auto' ]]; then
    if can_run_amd64_container; then
      USE_REMOTE_GAME_BUILD=0
    else
      USE_REMOTE_GAME_BUILD=1
      printf '本机 Docker 无法运行 linux/amd64 容器，将改在 AWS Game EC2 上构建镜像。\n'
    fi
  fi
  if [[ "$USE_REMOTE_GAME_BUILD" == '1' ]]; then
    printf '上传游戏服源码并触发远端构建…\n'
    upload_game_source
    run_remote_game_build
  else
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${CURRENT_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    docker buildx build --platform linux/amd64 --network host --progress=plain --load \
      --file "${AWS_DIR}/Dockerfile.game" \
      --tag "$GAME_IMAGE" \
      "$GAME_SERVER_DIR"
    docker push "$GAME_IMAGE"
  fi
fi

CONFIG_JSON="$(jq -cn \
  --arg region "$AWS_REGION" \
  --arg bucket "$ARTIFACT_BUCKET" \
  --arg webJarKey "$WEB_JAR_KEY" \
  --arg schemaKey "$SCHEMA_KEY" \
  --arg migrationPrefix "$MIGRATION_PREFIX" \
  --arg migrationManifestKey "$MIGRATION_MANIFEST_KEY" \
  --arg migrationBaseline "$MIGRATION_BASELINE" \
  --arg gameImage "$GAME_IMAGE" \
  --arg databaseEndpoint "$DATABASE_ENDPOINT" \
  --arg databaseSecretArn "$DATABASE_SECRET_ARN" \
  --arg redisSecretArn "$REDIS_SECRET_ARN" \
  --arg rabbitSecretArn "$RABBIT_SECRET_ARN" \
  --arg applicationSecretArn "$APPLICATION_SECRET_ARN" \
  --arg webPrivateIp "$WEB_PRIVATE_IP" \
  --arg apiDomain "$API_DOMAIN" \
  --arg gameDomain "$GAME_DOMAIN" \
  --arg tlsEmail "$TLS_EMAIL" \
  --arg initializeDatabase "$INITIALIZE_DATABASE" \
  --arg resetPlayerData "$RESET_PLAYER_DATA" \
  '{region:$region,bucket:$bucket,webJarKey:$webJarKey,schemaKey:$schemaKey,migrationPrefix:$migrationPrefix,migrationManifestKey:$migrationManifestKey,migrationBaseline:$migrationBaseline,gameImage:$gameImage,databaseEndpoint:$databaseEndpoint,databaseSecretArn:$databaseSecretArn,redisSecretArn:$redisSecretArn,rabbitSecretArn:$rabbitSecretArn,applicationSecretArn:$applicationSecretArn,webPrivateIp:$webPrivateIp,apiDomain:$apiDomain,gameDomain:$gameDomain,tlsEmail:$tlsEmail,initializeDatabase:$initializeDatabase,resetPlayerData:$resetPlayerData}')"
CONFIG_B64="$(printf '%s' "$CONFIG_JSON" | base64 | tr -d '\n')"

"${SCRIPT_DIR}/ssm-run.sh" "$WEB_INSTANCE_ID" "${AWS_DIR}/remote/configure-web.sh" "$CONFIG_B64"
"${SCRIPT_DIR}/ssm-run.sh" "$GAME_INSTANCE_ID" "${AWS_DIR}/remote/configure-game.sh" "$CONFIG_B64"

if [[ "$PUBLISH_WEB_UI" == '1' ]]; then
  printf '\n发布 web_ui 管理后台…\n'
  "${SCRIPT_DIR}/publish-web-ui.sh"
fi

printf '\n发布完成。请验证：\n'
printf '  HTTPS API: https://%s/\n' "$API_DOMAIN"
printf '  WSS: wss://%s/\n' "$GAME_DOMAIN"
printf '  WSS legacy port: wss://%s:9098/\n' "$GAME_DOMAIN"
printf '  TCP: %s:10086\n' "$GAME_DOMAIN"
