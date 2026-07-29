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
: "${WEB_UI_DIR:=${WORKSPACE_DIR}/web_ui/web-ui}"
: "${WEB_UI_BUILD_DIR:=}"
: "${SKIP_WEB_UI_BUILD:=0}"
: "${EXPECTED_AWS_ACCOUNT_ID:=}"
: "${EXPECTED_DEPLOY_ROLE:=}"
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"
[[ -z "${AWS_PROFILE:-}" ]] || export AWS_PROFILE
[[ -d "$WEB_UI_DIR" ]] || die "未找到 web_ui 工程: ${WEB_UI_DIR}"
WEB_UI_DIR="$(cd -- "$WEB_UI_DIR" && pwd)"

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

WEB_UI_BUCKET="$(stack_output WebUiBucketName)"
WEB_UI_DISTRIBUTION_ID="$(stack_output WebUiDistributionId)"
WEB_UI_URL="$(stack_output WebUiUrl)"
WEB_UI_STATIC_PREFIX="$(stack_output WebUiStaticPrefix)"
WEB_UI_API_PREFIX="$(stack_output WebUiApiPrefix)"

if [[ "$SKIP_WEB_UI_BUILD" != '1' ]]; then
  printf '安装 web_ui 依赖…\n'
  (cd "$WEB_UI_DIR" && npm install --no-audit --no-fund)

  printf '构建 web_ui 管理后台…\n'
  (cd "$WEB_UI_DIR" && VUE_APP_BASE_API="$WEB_UI_API_PREFIX" npm run build:prod)
fi

if [[ -z "$WEB_UI_BUILD_DIR" ]]; then
  WEB_UI_BUILD_DIR="${WEB_UI_DIR}/dist"
fi
[[ -f "${WEB_UI_BUILD_DIR}/index.html" ]] || die "未找到 web_ui 构建产物 ${WEB_UI_BUILD_DIR}/index.html。"

printf '上传 web_ui 到私有 S3 Bucket %s/%s…\n' "$WEB_UI_BUCKET" "$WEB_UI_STATIC_PREFIX"
aws s3 sync "${WEB_UI_BUILD_DIR}/" "s3://${WEB_UI_BUCKET}/${WEB_UI_STATIC_PREFIX}/" --delete \
  --cache-control 'public,max-age=31536000,immutable'

upload_no_cache() {
  local relative_path="$1" content_type="$2"
  local local_path="${WEB_UI_BUILD_DIR}/${relative_path}"
  [[ -f "$local_path" ]] || return 0
  aws s3 cp "$local_path" "s3://${WEB_UI_BUCKET}/${WEB_UI_STATIC_PREFIX}/${relative_path}" \
    --cache-control 'no-store,no-cache,must-revalidate' \
    --content-type "$content_type"
}

upload_no_cache 'index.html' 'text/html; charset=utf-8'
upload_no_cache 'favicon.ico' 'image/x-icon'

printf '刷新 web_ui CloudFront 缓存…\n'
INVALIDATION_ID="$(aws cloudfront create-invalidation \
  --distribution-id "$WEB_UI_DISTRIBUTION_ID" \
  --paths '/' "/${WEB_UI_STATIC_PREFIX}/*" "${WEB_UI_API_PREFIX}/*" \
  --query 'Invalidation.Id' \
  --output text)"

printf '\nweb_ui 管理后台发布完成。\n'
printf '访问地址: %s/\n' "$WEB_UI_URL"
printf '静态资源前缀: /%s/\n' "$WEB_UI_STATIC_PREFIX"
printf 'API 代理前缀: %s\n' "$WEB_UI_API_PREFIX"
printf 'CloudFront 刷新编号: %s\n' "$INVALIDATION_ID"
