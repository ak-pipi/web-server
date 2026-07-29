#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

RUN_INFRA=true
RUN_SERVER_WEB=true
RUN_CLIENT_COCOS=true
RUN_WEB_UI=true

usage() {
  cat <<'EOF'
用法:
  ./deploy/aws/scripts/deploy-all.sh [选项]

默认顺序部署全部 AWS 资源和应用：
  1. deploy-infra.sh         创建/更新 AWS 基础设施
  2. publish.sh              发布 web_server、server 与 web_ui
  3. publish-web-client.sh   发布 client_cocos H5 到 S3/CloudFront

选项:
  --skip-infra         跳过基础设施部署
  --skip-server-web    跳过 server + web_server 发布
  --skip-client-cocos  跳过 client_cocos H5 发布
  --skip-web-ui        跳过 web_ui 管理后台发布
  -h, --help           显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-infra) RUN_INFRA=false; shift ;;
    --skip-server-web) RUN_SERVER_WEB=false; shift ;;
    --skip-client-cocos) RUN_CLIENT_COCOS=false; shift ;;
    --skip-web-ui) RUN_WEB_UI=false; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf '未知选项: %s\n' "$1" >&2; usage >&2; exit 1 ;;
  esac
done

run_step() {
  local name="$1"
  local script="$2"
  printf '\n========== %s ==========\n' "$name"
  "$script"
}

${RUN_INFRA} && run_step '部署 AWS 基础设施' "${SCRIPT_DIR}/deploy-infra.sh"
if ${RUN_SERVER_WEB}; then
  if ${RUN_WEB_UI}; then
    run_step '发布 server + web_server + web_ui' "${SCRIPT_DIR}/publish.sh"
  else
    PUBLISH_WEB_UI=0 run_step '发布 server + web_server' "${SCRIPT_DIR}/publish.sh"
  fi
elif ${RUN_WEB_UI}; then
  run_step '发布 web_ui 管理后台' "${SCRIPT_DIR}/publish-web-ui.sh"
fi
${RUN_CLIENT_COCOS} && run_step '发布 client_cocos H5' "${SCRIPT_DIR}/publish-web-client.sh"

printf '\n全部选定部署步骤已完成。\n'
