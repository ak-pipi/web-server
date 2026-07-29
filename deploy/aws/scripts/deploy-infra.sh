#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
AWS_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
CONFIG_FILE="${AWS_DIR}/config.env"

die() { printf '错误: %s\n' "$*" >&2; exit 1; }
command -v aws >/dev/null || die '未安装 AWS CLI。'
command -v npm >/dev/null || die '未安装 Node.js/npm。'
[[ -f "$CONFIG_FILE" ]] || die "请先复制 config.example.env 为 ${CONFIG_FILE} 并填写。"

# shellcheck disable=SC1090
source "$CONFIG_FILE"
: "${AWS_REGION:?config.env 必须设置 AWS_REGION}"
: "${STACK_NAME:=NiuMaCostSaver}"
: "${API_DOMAIN:?config.env 必须设置 API_DOMAIN}"
: "${GAME_INSTANCE_TYPE:=t3.medium}"
: "${WEB_DOMAIN:=}"
: "${WEB_CERTIFICATE_ARN:=}"
: "${WEB_HOSTED_ZONE_ID:=}"
: "${WEB_HOSTED_ZONE_NAME:=}"
: "${WEB_UI_DOMAIN:=}"
: "${WEB_UI_CERTIFICATE_ARN:=}"
: "${WEB_UI_HOSTED_ZONE_ID:=}"
: "${WEB_UI_HOSTED_ZONE_NAME:=}"
: "${WEB_UI_API_PREFIX:=/niuma66}"
: "${WEB_UI_STATIC_PREFIX:=niuma66-ui}"
: "${EXPECTED_AWS_ACCOUNT_ID:=}"
: "${EXPECTED_DEPLOY_ROLE:=}"
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION" STACK_NAME
[[ -z "${AWS_PROFILE:-}" ]] || export AWS_PROFILE

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
[[ -z "$EXPECTED_AWS_ACCOUNT_ID" || "$EXPECTED_AWS_ACCOUNT_ID" == "$ACCOUNT_ID" ]] || \
  die "当前 AWS 账户为 ${ACCOUNT_ID}，但 config.env 要求 ${EXPECTED_AWS_ACCOUNT_ID}。已停止部署。"
if [[ -n "$EXPECTED_DEPLOY_ROLE" ]]; then
  [[ "$CALLER_ARN" == "arn:aws:sts::${ACCOUNT_ID}:assumed-role/${EXPECTED_DEPLOY_ROLE}/"* ]] || \
    die "当前身份不是预期的 assumed-role/${EXPECTED_DEPLOY_ROLE}：${CALLER_ARN}"
fi
printf '将使用 AWS 账户 %s，身份 %s 部署到 %s。\n' "$ACCOUNT_ID" "$CALLER_ARN" "$AWS_REGION"
export CDK_DEFAULT_ACCOUNT="$ACCOUNT_ID" CDK_DEFAULT_REGION="$AWS_REGION"

cd "$AWS_DIR"
npm ci

CDK_CONTEXT=(
  -c "gameInstanceType=${GAME_INSTANCE_TYPE}"
  -c "apiDomain=${API_DOMAIN}"
  -c "webUiApiPrefix=${WEB_UI_API_PREFIX}"
  -c "webUiStaticPrefix=${WEB_UI_STATIC_PREFIX}"
)
if [[ -n "$WEB_CERTIFICATE_ARN" ]]; then
  [[ -n "$WEB_DOMAIN" ]] || die '设置 WEB_CERTIFICATE_ARN 时，必须同时设置 WEB_DOMAIN。'
  CDK_CONTEXT+=( -c "webDomain=${WEB_DOMAIN}" -c "webCertificateArn=${WEB_CERTIFICATE_ARN}" )
  if [[ -n "$WEB_HOSTED_ZONE_ID" || -n "$WEB_HOSTED_ZONE_NAME" ]]; then
    [[ -n "$WEB_HOSTED_ZONE_ID" && -n "$WEB_HOSTED_ZONE_NAME" ]] \
      || die '启用 Route 53 别名记录时，必须设置 WEB_HOSTED_ZONE_ID 和 WEB_HOSTED_ZONE_NAME。'
    CDK_CONTEXT+=( -c "webHostedZoneId=${WEB_HOSTED_ZONE_ID}" -c "webHostedZoneName=${WEB_HOSTED_ZONE_NAME}" )
  fi
elif [[ -n "$WEB_HOSTED_ZONE_ID" ]]; then
  die '设置 WEB_HOSTED_ZONE_ID 前，请先填写 WEB_CERTIFICATE_ARN。'
elif [[ -n "$WEB_DOMAIN" ]]; then
  printf '提示: 尚未设置 WEB_CERTIFICATE_ARN；本次将先创建 CloudFront 默认域名，稍后补齐证书后可绑定 %s。\n' "$WEB_DOMAIN"
fi
if [[ -n "$WEB_UI_CERTIFICATE_ARN" ]]; then
  [[ -n "$WEB_UI_DOMAIN" ]] || die '设置 WEB_UI_CERTIFICATE_ARN 时，必须同时设置 WEB_UI_DOMAIN。'
  CDK_CONTEXT+=( -c "webUiDomain=${WEB_UI_DOMAIN}" -c "webUiCertificateArn=${WEB_UI_CERTIFICATE_ARN}" )
  if [[ -n "$WEB_UI_HOSTED_ZONE_ID" || -n "$WEB_UI_HOSTED_ZONE_NAME" ]]; then
    [[ -n "$WEB_UI_HOSTED_ZONE_ID" && -n "$WEB_UI_HOSTED_ZONE_NAME" ]] \
      || die '启用 web_ui Route 53 别名记录时，必须设置 WEB_UI_HOSTED_ZONE_ID 和 WEB_UI_HOSTED_ZONE_NAME。'
    CDK_CONTEXT+=( -c "webUiHostedZoneId=${WEB_UI_HOSTED_ZONE_ID}" -c "webUiHostedZoneName=${WEB_UI_HOSTED_ZONE_NAME}" )
  fi
elif [[ -n "$WEB_UI_HOSTED_ZONE_ID" ]]; then
  die '设置 WEB_UI_HOSTED_ZONE_ID 前，请先填写 WEB_UI_CERTIFICATE_ARN。'
elif [[ -n "$WEB_UI_DOMAIN" ]]; then
  printf '提示: 尚未设置 WEB_UI_CERTIFICATE_ARN；本次将先创建 CloudFront 默认域名，稍后补齐证书后可绑定 %s。\n' "$WEB_UI_DOMAIN"
fi
npx cdk bootstrap "aws://${ACCOUNT_ID}/${AWS_REGION}" "${CDK_CONTEXT[@]}"
npx cdk deploy "$STACK_NAME" --require-approval never "${CDK_CONTEXT[@]}"

printf '\n一键部署全部工程：\n  %s\n' "${SCRIPT_DIR}/deploy-all.sh"
printf '\n基础设施已创建。请执行：\n  %s\n' "${SCRIPT_DIR}/publish.sh"
printf '网页客户端构建并发布：\n  %s\n' "${SCRIPT_DIR}/publish-web-client.sh"
printf 'web_ui 管理后台构建并发布：\n  %s\n' "${SCRIPT_DIR}/publish-web-ui.sh"
