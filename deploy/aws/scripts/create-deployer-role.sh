#!/usr/bin/env bash
# Creates a same-account CDK deployment role and grants one IAM user permission
# to assume it. Run this only with an administrator-capable AWS CLI profile.
set -Eeuo pipefail
IFS=$'\n\t'

ROLE_NAME='NiuMaCdkDeployer'
TRUSTED_USER_ARN=''
REGION="${AWS_REGION:-ap-east-1}"

usage() {
  cat <<'EOF'
Usage:
  AWS_PROFILE=<administrator-profile> AWS_REGION=ap-east-1 \
  ./create-deployer-role.sh --trusted-user-arn arn:aws:iam::<account-id>:user/<user-name>

Options:
  --trusted-user-arn ARN  IAM user allowed to assume the deployment role (required)
  --role-name NAME        Role name (default: NiuMaCdkDeployer)
  --region REGION         Deployment region label (default: ap-east-1)

This first-install role receives AdministratorAccess so CDK can create the VPC,
RDS, EC2, IAM roles, S3, ECR and Secrets Manager resources in this project.
Replace it with a reviewed least-privilege policy after the initial deployment.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --trusted-user-arn) TRUSTED_USER_ARN="${2:?missing value for --trusted-user-arn}"; shift 2 ;;
    --role-name) ROLE_NAME="${2:?missing value for --role-name}"; shift 2 ;;
    --region) REGION="${2:?missing value for --region}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 1 ;;
  esac
done

[[ -n "$TRUSTED_USER_ARN" ]] || { usage >&2; exit 1; }
if [[ "$TRUSTED_USER_ARN" =~ ^arn:aws:iam::([0-9]{12}):user/(.+)$ ]]; then
  TRUSTED_ACCOUNT_ID="${BASH_REMATCH[1]}"
  TRUSTED_USER_PATH="${BASH_REMATCH[2]}"
else
  printf 'trusted-user-arn must be an IAM user ARN in the standard AWS partition.\n' >&2
  exit 1
fi

command -v aws >/dev/null || { printf 'AWS CLI is required.\n' >&2; exit 1; }
export AWS_REGION="$REGION" AWS_DEFAULT_REGION="$REGION"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
TRUSTED_USER_NAME="${TRUSTED_USER_PATH##*/}"
[[ "$ACCOUNT_ID" == "$TRUSTED_ACCOUNT_ID" ]] || {
  printf 'This helper is for a same-account IAM user. Caller account %s differs from trusted user account %s.\n' "$ACCOUNT_ID" "$TRUSTED_ACCOUNT_ID" >&2
  exit 1
}

ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"
TRUST_DOCUMENT="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"${TRUSTED_USER_ARN}\"},\"Action\":\"sts:AssumeRole\"}]}"
USER_POLICY="{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"sts:AssumeRole\",\"Resource\":\"${ROLE_ARN}\"}]}"

if aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  aws iam update-assume-role-policy --role-name "$ROLE_NAME" --policy-document "$TRUST_DOCUMENT"
  printf 'Updated trust policy for existing role %s.\n' "$ROLE_NAME"
else
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document "$TRUST_DOCUMENT" \
    --max-session-duration 14400 \
    --tags Key=Application,Value=niuma Key=Purpose,Value=cdk-deployment >/dev/null
  printf 'Created role %s.\n' "$ROLE_NAME"
fi

aws iam attach-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
aws iam put-user-policy \
  --user-name "$TRUSTED_USER_NAME" \
  --policy-name AssumeNiuMaCdkDeployer \
  --policy-document "$USER_POLICY"

cat <<EOF

Deployment role is ready: ${ROLE_ARN}

Configure a local AWS CLI role profile without putting credentials in this repository:

[profile niuma-ap-east-1]
role_arn = ${ROLE_ARN}
source_profile = <your-source-profile>
region = ap-east-1
role_session_name = niuma-cdk

Then set AWS_PROFILE=niuma-ap-east-1 in deploy/aws/config.env and run:
  ./deploy/aws/scripts/deploy-infra.sh
EOF
