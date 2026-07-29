#!/usr/bin/env bash
# Executed as root by AWS Systems Manager when the local workstation cannot
# build the linux/amd64 game image.
set -Eeuo pipefail
IFS=$'\n\t'

dnf install -y awscli docker tar gzip
systemctl enable --now docker

CONFIG_JSON="$(printf '%s' "${NIUMA_CONFIG_B64:?missing build configuration}" | base64 -d)"
value() { jq -er --arg key "$1" '.[$key]' <<< "$CONFIG_JSON"; }

REGION="$(value region)"
BUCKET="$(value bucket)"
GAME_IMAGE="$(value gameImage)"
GAME_SOURCE_KEY="$(value gameSourceKey)"
GAME_DOCKERFILE_KEY="$(value gameDockerfileKey)"
GAME_BUILD_LOG_KEY="$(value gameBuildLogKey)"
export AWS_DEFAULT_REGION="$REGION"

WORK_DIR="/tmp/niuma-game-build"
SOURCE_ARCHIVE="/tmp/niuma-game-source.tar.gz"
DOCKERFILE_PATH="/tmp/niuma-Dockerfile.game"
BUILD_LOG="/tmp/niuma-game-docker-build.log"
rm -rf "$WORK_DIR"
install -d -m 0755 "$WORK_DIR"
rm -f "$BUILD_LOG"

aws s3 cp "s3://${BUCKET}/${GAME_SOURCE_KEY}" "$SOURCE_ARCHIVE"
aws s3 cp "s3://${BUCKET}/${GAME_DOCKERFILE_KEY}" "$DOCKERFILE_PATH"
tar -xzf "$SOURCE_ARCHIVE" -C "$WORK_DIR"

registry="${GAME_IMAGE%%/*}"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$registry"
set +e
docker build --network host --progress=plain --file "$DOCKERFILE_PATH" --tag "$GAME_IMAGE" "$WORK_DIR" 2>&1 | tee "$BUILD_LOG"
build_status="${PIPESTATUS[0]}"
set -e
aws s3 cp "$BUILD_LOG" "s3://${BUCKET}/${GAME_BUILD_LOG_KEY}" || true
[[ "$build_status" -eq 0 ]] || exit "$build_status"
docker push "$GAME_IMAGE"
