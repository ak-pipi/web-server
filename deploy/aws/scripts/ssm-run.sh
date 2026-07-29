#!/usr/bin/env bash
# Run a local shell script on an EC2 instance through AWS Systems Manager.
# Input is base64 encoded, so no application passwords are put on a command line.
set -Eeuo pipefail
IFS=$'\n\t'

INSTANCE_ID="${1:?usage: ssm-run.sh INSTANCE_ID REMOTE_SCRIPT CONFIG_BASE64}"
REMOTE_SCRIPT="${2:?usage: ssm-run.sh INSTANCE_ID REMOTE_SCRIPT CONFIG_BASE64}"
CONFIG_B64="${3:?usage: ssm-run.sh INSTANCE_ID REMOTE_SCRIPT CONFIG_BASE64}"

[[ -f "$REMOTE_SCRIPT" ]] || { printf '找不到远端脚本: %s\n' "$REMOTE_SCRIPT" >&2; exit 1; }
SCRIPT_B64="$(base64 < "$REMOTE_SCRIPT" | tr -d '\n')"
REMOTE_NAME="/tmp/niuma-$(basename "$REMOTE_SCRIPT")"
REMOTE_COMMAND="export NIUMA_CONFIG_B64='${CONFIG_B64}'; echo '${SCRIPT_B64}' | base64 -d > '${REMOTE_NAME}'; chmod 700 '${REMOTE_NAME}'; bash '${REMOTE_NAME}'"
# Use JSON rather than AWS CLI shorthand. The command contains shell quoting and
# semicolons, which the shorthand parser can otherwise misinterpret as a list.
PARAMETERS_JSON="$(jq -cn --arg command "$REMOTE_COMMAND" '{commands: [$command]}')"

COMMAND_ID="$(aws ssm send-command \
  --document-name AWS-RunShellScript \
  --instance-ids "$INSTANCE_ID" \
  --timeout-seconds 3600 \
  --parameters "$PARAMETERS_JSON" \
  --query 'Command.CommandId' --output text)"

printf '已向 %s 发送部署命令 %s；等待完成…\n' "$INSTANCE_ID" "$COMMAND_ID"
for _ in $(seq 1 360); do
  STATUS="$(aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || true)"
  case "$STATUS" in
    Success)
      aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
        --query StandardOutputContent --output text
      exit 0
      ;;
    Failed|Cancelled|TimedOut|Cancelling)
      aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" \
        --query '{status:Status,stdout:StandardOutputContent,stderr:StandardErrorContent}' --output json >&2 || true
      exit 1
      ;;
  esac
  sleep 10
done

printf 'SSM 命令在 60 分钟内未完成: %s\n' "$COMMAND_ID" >&2
exit 1
