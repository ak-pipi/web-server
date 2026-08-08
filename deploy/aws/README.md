# NiuMa：1000 PCU AWS 冷启动部署包

本目录把《AWS 部署架构与服务采购方案（1000 PCU）》中的**方案 B（极致省钱自建版）**做成可重复执行的 CDK + 本地发布脚本。它不在本机或代码库保留生产密码，也不需要开放 SSH。

> 适用边界：一套单区域、约 1000 PCU 的冷启动/公测环境。Redis 和 RabbitMQ 与 Java Web 共用一台 EC2；RDS 是唯一的持久化数据源。若中间件、任一 EC2 或单个可用区故障，业务会中断，因此它不是高可用生产方案。

> 目录位置：AWS 部署工程位于 `web_server/web-server/deploy/aws`。脚本默认以 `web_server/web-server` 为 Java 后端工程根，并从同一工作区的兄弟目录解析 C++ 游戏服 `server`、Cocos 客户端 `client_cocos/client-cocos`、管理后台 `web_ui/web-ui`；若目录不同，可在 `config.env` 中设置 `GAME_SERVER_DIR`、`SQL_DIR`、`CLIENT_DIR`、`WEB_UI_DIR`。

## 1. 最终资源与配置清单

| 类别 | 创建的资源 | 固定配置 | 目的 |
|---|---|---|---|
| 网络 | 1 个 VPC，2 个公有子网、2 个隔离数据库子网 | 两个可用区；不创建 NAT Gateway | 省掉 NAT 成本，同时让 RDS 不具备公网路由 |
| 计算 | `niuma-web` EC2 | `t3.medium`、x86、30 GiB 加密 gp3、Amazon Linux 2023 | Java、Nginx、Redis、RabbitMQ |
| 计算 | `niuma-game` EC2 | 默认 `t3.medium`；可改 `c6i.large`；30 GiB 加密 gp3、Amazon Linux 2023 | C++ 游戏服、Nginx |
| 公网入口 | 2 个 Elastic IP | 一个绑定 Web，一个绑定游戏服 | 域名 A 记录稳定指向，不必因重启变更客户端地址 |
| 数据库 | RDS for MySQL | `db.t4g.medium`、MySQL 8.0、20 GiB gp3、最多自动扩到 100 GiB、单可用区、7 天备份 | 钱包、资产、回放和业务数据 |
| 数据库防护 | DB 子网组、数据库安全组、加密、删除保护 | 私有子网；仅两台应用 EC2 可访问 3306；`RETAIN` | 数据库不暴露公网，避免误删 |
| 中间件 | Redis 7.4、RabbitMQ 3.13 容器 | 运行在 Web EC2；Redis AOF 持久化、RabbitMQ 数据卷 | 对应采购文档的低成本方案 B |
| 制品 | 私有 S3 Bucket、私有 ECR Repository | S3 全部阻止公开访问、TLS 强制、版本控制；ECR 扫描与仅保留 10 个镜像 | Java JAR、SQL、游戏服镜像 |
| 前端 | `WebClientBucket` + CloudFront | Cocos H5 玩家端静态资源 | 玩家网页客户端 |
| 前端 | `WebUiBucket` + CloudFront | web_ui 管理后台静态资源；`/niuma66/*` 代理到 API 域名 | 后台管理页面 |
| 密钥 | 4 个 Secrets Manager Secret + RDS 自动生成凭据 | MySQL、Redis、RabbitMQ、JWT 分开保存 | 无明文生产凭据写入 Git 或 CDK 上下文 |
| 运维 | EC2 IAM Role、Systems Manager、CloudWatch Agent 权限 | 不创建 22 端口入站规则 | 通过 SSM 远程发布和运维 |

### 对外与内部端口

| 端口 | 位置 | 来源 | 用途 |
|---:|---|---|---|
| 80 | 两台 EC2 | 公网 | 仅用于 Let's Encrypt HTTP-01 校验；Web 随后会跳转至 HTTPS |
| 443 | Web EC2 | 公网 | Java API 的 HTTPS，由 Nginx 反向代理至 `127.0.0.1:18080` |
| 443 | 游戏 EC2 | 公网 | WSS，由 Nginx TLS 卸载后转至本机 C++ `19098` |
| 9098 | 游戏 EC2 | 公网 | 兼容旧配置的 WSS，同样转至本机 C++ `19098` |
| 10086 | 游戏 EC2 | 公网 | C++ 原生 TCP 游戏连接 |
| 3306 | RDS | 仅 Web/Game 安全组 | MySQL |
| 6379 | Web EC2 | 仅 Game 安全组 | Redis；Java 本机也访问 |
| 5672 | Web EC2 | 仅 Game 安全组 | RabbitMQ；Java 本机也访问 |

不开放 SSH `22`、RDS 公网、Redis 公网、RabbitMQ 公网和 RabbitMQ 管理后台 `15672`。需要诊断时用 Systems Manager Session Manager 或端口转发。

## 2. 部署前一次性准备

1. 选择 AWS 区域。以**玩家所在地区的网络延迟**和服务可用性为准；本包不写死区域。`config.env` 的 `AWS_REGION` 必须与实际部署区域一致。
2. 在 AWS 账户中建立最小权限的发布身份，授予创建本包内 EC2/VPC/RDS/IAM/S3/ECR/Secrets Manager/CloudFormation/SSM 资源的权限；不要使用长期 Root Access Key。
3. 在本机安装并登录 AWS CLI、Node.js 22 LTS+、Docker（含 buildx）、Maven 3.8+、`jq`、`dig`。例如 SSO 用户先完成 `aws sso login --profile <profile>`。
4. 准备两个可从公网解析的域名或子域名，例如 `api.example.com` 和 `game.example.com`。HTTPS 与 WSS 必须使用域名，不能填裸 IP；证书签发不要求你拥有邮箱。
5. 检查目标区域的 EC2、Elastic IP、RDS 配额。若把游戏机改为 `c6i.large`，还要确认该实例类型在目标可用区可售。
6. 审核项目中的 SQL 数据。当前 `sql/niuma.sql` 包含 `DROP TABLE`、机器人/示例数据；它只能用于空库的首次初始化，绝不能直接覆盖已有生产数据。
7. 立即轮换仓库中历史配置文件里已有的数据库、Redis、RabbitMQ 和 JWT 凭据；发布包不会读取这些历史凭据。

### 没有邮箱、只有 AWS 子账户时

可以部署。这里的“子账户”按 AWS IAM 用户、IAM Identity Center 用户或可扮演的 IAM Role 理解；它不需要持有 AWS 根账户邮箱。

1. **邮箱是可选项。** 将 `TLS_EMAIL=` 留空即可。发布脚本会让 Certbot 显式使用 `--register-unsafely-without-email` 申请 Let’s Encrypt 证书；证书和自动续期仍可正常工作，但不会收到到期、恢复或安全通知。若以后获得运维邮箱，请在下次证书更新时把它加入 Certbot 的注册联系人。
2. **域名仍是必需项。** 没有邮箱不妨碍 DNS 验证；但如果没有域名，不能得到浏览器/客户端信任的 HTTPS/WSS 证书。此时只能暂时测试裸 TCP `10086`，或自行在客户端信任自签名证书，不应对外开放 WSS。
3. **最适合手工创建的前置资源是 CDK 引导栈，而不是业务资源。** 让主账户管理员在目标账户、目标区域执行一次 `cdk bootstrap`，生成 `CDKToolkit`、部署/发布/查找 IAM Role 及其 S3/ECR 资产资源；随后把子账户配置为可以扮演这些带 `aws-cdk:bootstrap-role` 标签的角色。CDK 官方也支持由管理员手动创建或定制 bootstrap 资源。
4. 如果管理员不愿给子账户完整管理员权限，首次可由管理员完成 bootstrap，并授予子账户发布角色、文件发布角色、镜像发布角色和查找角色的 `sts:AssumeRole` 权限。管理员还需确保 bootstrap 的 CloudFormation 执行角色有权限创建本方案中的 VPC、EC2、RDS、IAM、S3、ECR、Secrets Manager 和 SSM 资源。
5. **不要先手工创建 VPC、子网、EC2、RDS、EIP、S3 制品桶、ECR 或同名 Secrets 后再执行当前部署包。** 当前 CDK 将管理这些资源，手工预建会造成资源重复或 CloudFormation 冲突。若公司必须复用既有网络/数据库，应另行改造为“导入现有 VPC/子网/安全组”的 CDK 版本，而不是混用两种创建方式。
6. 域名 A 记录、AWS Budget、Cost Anomaly Detection 和告警可手工创建，且不会与 CDK 冲突。

对于“同一 AWS 账户内的 IAM 子用户”，本目录还提供了角色创建助手。它必须由**具备 IAM 管理权限的管理员 CLI profile**执行；如果当前子用户没有 `iam:CreateRole`、`iam:AttachRolePolicy` 和 `iam:PutUserPolicy` 权限，它不能自行给自己提权：

```bash
AWS_PROFILE=<管理员-profile> AWS_REGION=ap-east-1 \
  ./deploy/aws/scripts/create-deployer-role.sh \
  --trusted-user-arn arn:aws:iam::<账号ID>:user/<子用户名>
```

该助手创建 `NiuMaCdkDeployer` 并在首次部署期间授予 `AdministratorAccess`，同时仅允许指定 IAM 子用户扮演它。首次部署、验收完成后，应按实际资源缩减该角色权限。随后在本机 `~/.aws/config` 配置 `source_profile` + `role_arn`，将 `config.env` 中的 `AWS_PROFILE` 改为该 role profile，再运行基础设施脚本。不要把控制台密码、访问密钥或 Session Token 写入 `config.env` 或发到聊天中。

## 3. 创建 AWS 基础设施

1. 复制配置模板，不要将真实文件提交到版本库：

   ```bash
   cd /path/to/CLionProjects/web_server/web-server
   cp deploy/aws/config.example.env deploy/aws/config.env
   ```

2. 编辑 `deploy/aws/config.env`。

   - 设置 `AWS_PROFILE`、`AWS_REGION`、`STACK_NAME`。
   - `GAME_INSTANCE_TYPE=t3.medium` 是冷启动省钱默认值；压测或稳定 1000 PCU 时改为 `c6i.large` 后重新运行基础设施脚本。
   - 填写两个域名；`TLS_EMAIL` 可留空，但建议未来补一个运维邮箱，以接收 Let’s Encrypt 到期通知。
   - `WEB_DOMAIN` 用于 Cocos H5 玩家端；`WEB_UI_DOMAIN` 用于 web_ui 管理后台。两者都可以先不配证书，使用 CloudFront 默认域名验收。
   - web_ui 默认部署在 `/niuma66-ui/`，API 代理前缀为 `/niuma66`；这与当前 `web_ui/web-ui/vue.config.js`、`.env.production` 保持一致。
   - 保持 `INITIALIZE_DATABASE=0`，直到确认这是全新的空 RDS。
   - `RESET_PLAYER_DATA=1` 会把 `v12_reset_players_for_new_rules.sql` 加入初始化迁移清单，具有破坏性，只能在确认需要清理旧玩家/房间/流水时启用。

3. 在本地运行：

   ```bash
   ./deploy/aws/scripts/deploy-infra.sh
   ```

   脚本会安装 CDK 依赖、执行 `cdk bootstrap`、创建 CloudFormation Stack。CDK 第一次部署某个账户/区域前必须 bootstrap；这是 CDK 用于放置部署资产和角色的前置栈。

4. 在 CloudFormation 的 Stack 输出中记录 `WebElasticIp` 和 `GameElasticIp`。基础设施脚本也会在终端输出成功信息。

5. 在 DNS 提供商建立两个 A 记录，TTL 先设为 300 秒：

   ```text
   api.example.com   A   <WebElasticIp>
   game.example.com  A   <GameElasticIp>
   ```

6. 等待 DNS 生效。确认以下命令分别返回对应 EIP：

   ```bash
   dig +short A api.example.com
   dig +short A game.example.com
   ```

## 4. 首次本地发布

1. 若 RDS 是空库，并且你已审阅过 `niuma.sql` 与 `scripts/publish.sh` 中的 SQL 迁移清单，把 `config.env` 中的 `INITIALIZE_DATABASE=1`。否则保持 `0`。
2. 在 `web_server/web-server` 目录可一键部署全部工程：

   ```bash
   ./deploy/aws/scripts/deploy-all.sh
   ```

   需要跳过已创建的基础设施时：

   ```bash
   ./deploy/aws/scripts/deploy-all.sh --skip-infra
   ```

3. 也可以按模块单独执行。`publish.sh` 会按顺序发布 server、web_server 和 web_ui：

   ```bash
   ./deploy/aws/scripts/publish.sh
   ```

   client_cocos H5 发布：

   ```bash
   ./deploy/aws/scripts/publish-web-client.sh
   ```

   web_ui 管理后台发布：

   ```bash
   ./deploy/aws/scripts/publish-web-ui.sh
   ```

4. `publish.sh` 严格按以下顺序执行：

   1. 验证 DNS 已指向两枚 EIP；
   2. 编译当前 `web_server/web-server` 工程，上传 `niuma-admin.jar`、初始化 SQL、迁移 SQL 和迁移清单至私有 S3；
   3. 按 `linux/amd64` 构建 C++ 游戏服镜像，扫描后推送私有 ECR；
   4. 用 SSM 在 Web EC2 安装 Java、Docker、Nginx，读取 Secrets Manager，启动 Redis、RabbitMQ、Java 服务；
   5. 仅当 `INITIALIZE_DATABASE=1` 时，用临时 MySQL 客户端导入基础 SQL，再按 `scripts/publish.sh` 写出的清单顺序执行迁移 SQL；`v12_reset_players_for_new_rules.sql` 只有 `RESET_PLAYER_DATA=1` 时才会执行；
   6. 用 Docker 版 Certbot 为 API 域名申请证书，写入 Nginx HTTPS 配置和每日续期任务；
   7. 用 SSM 在游戏 EC2 写入运行时 `server.ini`，其中 C++ WebSocket 改为本机 `19098`；拉取 ECR 镜像并以 host network 启动；
   8. 为游戏域名申请证书，Nginx 在公网 `443` 终止 WSS，并保留 `9098` 兼容入口，再代理给 C++ `19098`。
   9. 构建 `web_ui/web-ui`，上传到 `WebUiBucket` 的 `/niuma66-ui/` 前缀，并刷新 web_ui CloudFront 缓存。

5. web_ui 仍可用 `publish-web-ui.sh` 单独发布。CloudFront 会把页面请求交给 S3，把 `/niuma66/*` API 请求转发到 `API_DOMAIN` 并去掉 `/niuma66` 前缀。若临时只发布 server + web_server，执行 `PUBLISH_WEB_UI=0 ./deploy/aws/scripts/publish.sh`。
6. 首次初始化成功后，立刻把 `INITIALIZE_DATABASE` 改回 `0`。后续代码发布只需要再次运行 `publish.sh`；它不会碰数据库表结构或数据。

## 5. 验收步骤

1. 控制台检查两台 EC2 已出现在 Systems Manager 的 **Managed nodes** 中。
2. Web 服务：`curl -I https://api.example.com/` 应返回任意正常 HTTP 状态（应用路径可因前端路由而为 200/302/404）。
3. TLS：浏览器或 `openssl s_client -connect api.example.com:443 -servername api.example.com` 检查证书域名和有效期。
4. WSS：使用客户端或 WebSocket 测试工具连接 `wss://game.example.com/`。Nginx 日志和游戏容器日志中不能出现 upstream connection refused。
5. TCP：使用真实客户端连接 `game.example.com:10086`；在 EC2 上用 SSM 执行 `docker logs --tail 100 niuma-game`，确认游戏服已经连接 MySQL、Redis 和 RabbitMQ。
6. web_ui：访问 `WebUiUrl` 或 `https://admin.example.com/`，确认登录页静态资源来自 S3，验证码和登录请求经 `/niuma66/*` 正常到达 API。
7. RDS：在 RDS 控制台确认 `Publicly accessible = No`、备份保留 7 天、删除保护已打开；做一次手动快照，命名例如 `niuma-before-public-beta`。
8. 恢复演练：至少在非生产环境从该快照恢复一次，并验证 Java/C++ 可连通恢复后的数据库。

## 6. 日常运维、回滚与数据安全

- **查看日志**：用 Session Manager 进入实例，Web 查看 `systemctl status niuma-web` 和 `journalctl -u niuma-web -f`；游戏查看 `systemctl status niuma-game`、`docker logs -f niuma-game`。
- **回滚应用**：ECR 和 S3 均保留历史发布版本。将 `publish.sh` 中本次生成的镜像/JAR 版本替换为上一版本，或者以保留版本重新运行相同的 SSM 配置脚本。回滚前先记录当前版本并做 RDS 快照。
- **数据库迁移**：本发布脚本不会自动对已有生产库执行新 SQL。每次迁移应先创建 RDS 手动快照，在预发布库验证 SQL 和回滚方式，再以明确的变更单独执行。
- **密钥轮换**：在 Secrets Manager 轮换 Redis/RabbitMQ/数据库凭据后，必须重新运行发布脚本或 SSM 配置脚本，使本机服务配置同步；JWT 密钥轮换会使现有登录令牌失效，应安排维护窗口。
- **费用保护**：建立 AWS Budget 和 Cost Anomaly Detection；启用 RDS/EC2/网络流量的 CloudWatch 告警。文档中的美元数是当时的估算，实际金额以所选区域、EIP、数据传出、存储增长和公网流量账单为准。

## 7. 何时迁移到高可用方案 A

以下任一情形出现时，不要再依赖此单机中间件方案：在线人数稳定超过 1000、充值/钱包写入显著增加、需要多台游戏服、或者业务不能接受单机故障。

按顺序升级：

1. 将 Redis 迁移到 ElastiCache for Redis，并把 Java/C++ 的 Redis 地址改为私有端点；
2. 将 RabbitMQ 迁移到 Amazon MQ for RabbitMQ，并将队列/交换机配置迁移后再切换连接；
3. RDS 改为 Multi-AZ，并评估更大规格/读副本；
4. Java 前添加 ALB + ACM；游戏 TCP/WSS 前添加 NLB，EC2 改为 Auto Scaling Group；
5. 迁移证书终止至 ALB/NLB 和 ACM，关闭两台实例上的公网 80/443/9098 直接入口；
6. 压测确认连接数、P99 延迟、RDS CPU/连接数/慢查询、Redis 内存、RabbitMQ 队列深度和磁盘空间。

## 8. 重要限制

- 这是一套**标准 AWS 全球区域**的部署包；不同区域的实例可用性、服务版本和价格不同。若使用独立分区/专用云环境，应先验证 CDK、ECR、SSM、ACM/证书和域名验证流程。
- `10086` 是文档指定的裸 TCP 协议。若其承载登录令牌、钱包或其他敏感数据，应在客户端协议层补充 TLS/加密与重放防护；仅 WSS 使用 TLS 并不能保护裸 TCP。
- 本包以现有 Java x86 JAR 与 C++ x86 镜像为目标，因此 Web 使用 `t3.medium` 而不是文档中的 `t4g.medium`。只有确认 Java 依赖和所有原生库已适配 ARM64 后，才应改用 t4g。
