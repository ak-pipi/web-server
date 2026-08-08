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

for command_name in aws jq npm; do require_command "$command_name"; done
[[ -f "$CONFIG_FILE" ]] || die "请先创建 ${CONFIG_FILE}。"

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${AWS_REGION:?config.env 必须设置 AWS_REGION}"
: "${STACK_NAME:=NiuMaCostSaver}"
: "${API_DOMAIN:?config.env 必须设置 API_DOMAIN}"
: "${COCOS_CREATOR_BIN:=}"
: "${CLIENT_DIR:=${WORKSPACE_DIR}/client_cocos/client-cocos}"
: "${WEB_BUILD_DIR:=}"
: "${SKIP_COCOS_BUILD:=0}"
: "${CLIENT_ENV:=aws}"
: "${CLIENT_ENV_CONFIG:=}"
: "${EXPECTED_AWS_ACCOUNT_ID:=}"
: "${EXPECTED_DEPLOY_ROLE:=}"
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"
[[ -z "${AWS_PROFILE:-}" ]] || export AWS_PROFILE
[[ -d "$CLIENT_DIR" ]] || die "未找到 Cocos 项目: ${CLIENT_DIR}"
CLIENT_DIR="$(cd -- "$CLIENT_DIR" && pwd)"
[[ "$CLIENT_ENV" =~ ^[A-Za-z0-9._-]+$ ]] || die "CLIENT_ENV 只能包含字母、数字、点、下划线或短横线: ${CLIENT_ENV}"
[[ "$CLIENT_ENV" == 'aws' ]] || die "AWS 发布脚本只能使用 CLIENT_ENV=aws，当前为: ${CLIENT_ENV}"
if [[ -z "$CLIENT_ENV_CONFIG" ]]; then
  CLIENT_ENV_CONFIG="${CLIENT_DIR}/config/env.${CLIENT_ENV}.json"
fi
[[ -f "$CLIENT_ENV_CONFIG" ]] || die "未找到 Cocos 环境配置: ${CLIENT_ENV_CONFIG}"
CLIENT_ENVIRONMENT_NAME="$(jq -r --arg fallback "$CLIENT_ENV" '.environment // $fallback' "$CLIENT_ENV_CONFIG")"
CLIENT_API_BASE_URL="$(jq -er '.apiBaseUrl' "$CLIENT_ENV_CONFIG")"
[[ "$CLIENT_API_BASE_URL" =~ ^https?:// ]] || die "Cocos 环境配置 apiBaseUrl 必须以 http:// 或 https:// 开头: ${CLIENT_API_BASE_URL}"
if [[ "$CLIENT_ENV" == 'aws' ]]; then
  EXPECTED_CLIENT_API_BASE_URL="https://${API_DOMAIN}"
  [[ "$CLIENT_API_BASE_URL" == "$EXPECTED_CLIENT_API_BASE_URL" ]] || \
    die "Cocos AWS apiBaseUrl(${CLIENT_API_BASE_URL}) 与 API_DOMAIN(${EXPECTED_CLIENT_API_BASE_URL}) 不一致。请同步 config/env.aws.json 或 deploy/aws/config.env。"
fi

CURRENT_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
[[ -z "$EXPECTED_AWS_ACCOUNT_ID" || "$EXPECTED_AWS_ACCOUNT_ID" == "$CURRENT_ACCOUNT_ID" ]] || \
  die "当前 AWS 账户为 ${CURRENT_ACCOUNT_ID}，但 config.env 要求 ${EXPECTED_AWS_ACCOUNT_ID}。已停止发布。"
if [[ -n "$EXPECTED_DEPLOY_ROLE" ]]; then
  [[ "$CALLER_ARN" == "arn:aws:sts::${CURRENT_ACCOUNT_ID}:assumed-role/${EXPECTED_DEPLOY_ROLE}/"* ]] || \
    die "当前身份不是预期的 assumed-role/${EXPECTED_DEPLOY_ROLE}：${CALLER_ARN}"
fi

STACK_JSON="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --output json)"
stack_output() {
  jq -er --arg key "$1" '.Stacks[0].Outputs[] | select(.OutputKey == $key) | .OutputValue' <<< "$STACK_JSON"
}

WEB_BUCKET="$(stack_output WebClientBucketName)"
WEB_DISTRIBUTION_ID="$(stack_output WebClientDistributionId)"
WEB_URL="$(stack_output WebClientUrl)"

if [[ "$SKIP_COCOS_BUILD" != '1' ]]; then
  [[ -n "$COCOS_CREATOR_BIN" && -x "$COCOS_CREATOR_BIN" ]] \
    || die "未找到 Cocos Creator 3.8.7。请在 config.env 设置 COCOS_CREATOR_BIN，或先在编辑器构建后设置 SKIP_COCOS_BUILD=1。"
  printf '安装 Cocos 客户端依赖…\n'
  (cd "$CLIENT_DIR" && npm install --no-audit --no-fund)

  printf '构建 Cocos Web Mobile 网页包…\n'
  set +e
  "$COCOS_CREATOR_BIN" --project "$CLIENT_DIR" \
    --build 'platform=web-mobile;buildPath=project://build;outputName=web-mobile;debug=false;md5Cache=true'
  COCOS_EXIT_CODE=$?
  set -e
  # Cocos Creator documents exit code 36 as a successful build.
  [[ "$COCOS_EXIT_CODE" -eq 0 || "$COCOS_EXIT_CODE" -eq 36 ]] \
    || die "Cocos 构建失败，退出码: ${COCOS_EXIT_CODE}"
fi

if [[ -z "$WEB_BUILD_DIR" ]]; then
  WEB_BUILD_DIR="${CLIENT_DIR}/build/web-mobile"
fi
[[ -f "${WEB_BUILD_DIR}/index.html" ]] || die "未找到网页构建产物 ${WEB_BUILD_DIR}/index.html。请检查 WEB_BUILD_DIR 或 Cocos 构建日志。"

RUNTIME_CONFIG_PATH="${WEB_BUILD_DIR}/config.json"
jq -n --arg environment "$CLIENT_ENVIRONMENT_NAME" --arg apiBaseUrl "$CLIENT_API_BASE_URL" \
  '{environment:$environment, apiBaseUrl:$apiBaseUrl}' > "$RUNTIME_CONFIG_PATH"
printf '写入 Cocos 运行配置: %s -> %s\n' "$CLIENT_ENV_CONFIG" "$CLIENT_API_BASE_URL"

printf '上传网页资源到私有 S3 Bucket %s…\n' "$WEB_BUCKET"
aws s3 sync "${WEB_BUILD_DIR}/" "s3://${WEB_BUCKET}/" --delete \
  --cache-control 'public,max-age=31536000,immutable'

upload_no_cache() {
  local relative_path="$1" content_type="$2"
  local local_path="${WEB_BUILD_DIR}/${relative_path}"
  [[ -f "$local_path" ]] || return 0
  aws s3 cp "$local_path" "s3://${WEB_BUCKET}/${relative_path}" \
    --cache-control 'no-store,no-cache,must-revalidate' \
    --content-type "$content_type"
}

# These files select the resource versions for a release. They must not be
# held by the browser after a new build has been uploaded.
upload_no_cache 'index.html' 'text/html; charset=utf-8'
upload_no_cache 'config.json' 'application/json; charset=utf-8'
upload_no_cache 'application.js' 'application/javascript; charset=utf-8'
upload_no_cache 'settings.json' 'application/json; charset=utf-8'
upload_no_cache 'src/settings.json' 'application/json; charset=utf-8'
upload_no_cache 'src/import-map.json' 'application/json; charset=utf-8'

printf '刷新 CloudFront 缓存…\n'
INVALIDATION_ID="$(aws cloudfront create-invalidation \
  --distribution-id "$WEB_DISTRIBUTION_ID" \
  --paths '/*' \
  --query 'Invalidation.Id' \
  --output text)"

printf '\n网页客户端发布完成。\n'
printf '访问地址: %s\n' "$WEB_URL"
printf 'CloudFront 刷新编号: %s\n' "$INVALIDATION_ID"
printf '请在浏览器检查登录、HTTP API 和 WSS 进房流程。\n'
