# 第七周：服务注册与发现

项目：社区报修与上门维修服务平台。公开仓库：https://github.com/shigaharuki17/microservices-practice-2412190707 。沿用原 services/ 下三个工程、模型、API、数据库归属和业务规则，monolith/ 保持不变。

## 1. 部署 Nacos 并注册服务

根 compose.yaml 新增 nacos/nacos-server:v3.2.4，保留三个业务服务与三个 MySQL。服务名仍为 user-service、technician-service、repair-service；各服务 spring.application.name 非空，基础包 com.zjgsu.hly。远程依赖通过 BOM 管理 Discovery Starter；不添加 Config Starter，不单独覆盖 nacos-client。

Nacos 鉴权启用，Token 为 48 字节随机数据的 Base64，Server identity 单独生成。真实值仅在忽略的 .env，不进入截图。nacos-data 和 nacos-logs 命名卷保存数据及日志；业务库沿用原数据卷。默认 public 命名空间、个人分组 PERSONAL_GROUP，与课堂 COURSE_GROUP 分开。

| 用途 | 宿主机端口 | 容器端口 |
| --- | --- | --- |
| Nacos 控制台 HTTP | 18088 | 8080 |
| Nacos 服务端 API | 8849 | 8848 |
| Nacos SDK gRPC | 9849 | 9848 |
| user / technician / repair | 8091 / 8092 / 8093 | 相同 |

本机 Windows 访问 VMware 控制台为 http://192.168.158.128:18088/ ，服务端 API 192.168.158.128:8849。默认模板绑定回环；本机仅将 NACOS_BIND_HOST 改为虚拟机私网 IP，不向公网暴露。Nacos 内部地址 nacos:8848。管理员账户由 NACOS_USERNAME/NACOS_PASSWORD 注入，首次初始化完成后再启动业务服务。

```bash
# Ubuntu 仓库根目录。已有 .env 时只补齐新字段，勿覆盖原数据库密码。
cp .env.example .env
# 编辑所有占位密码、Nacos Token 和 Server identity；不要输出 .env。
sudo docker compose up -d --wait nacos
python3 scripts/nacos_admin.py init
sudo docker compose up -d --build
sudo docker compose ps
python3 scripts/nacos_admin.py snapshot manual
```

本机共享挂载不允许 root 直接读取，因此将仓库（排除 .git、target、IDE 文件）同步到 ~/community-repair-week07 后执行。首次镜像下载遇到 20GB 虚拟机磁盘满，仅清理可重新生成的 Docker 构建缓存，未删除数据库卷。随后按用户要求将虚拟硬盘扩到 100GB，并用 growpart / resize2fs 扩展 /dev/sda2 的 ext4 根文件系统；实测总容量约 99GB、可用约 76GB，原容器与数据保留。

## 2. 配置隔离并完成服务发现调用

repair-service 的 DependencyClient 每次查询住户/人员，都调用 DiscoveryClient.getInstances(实际服务名)，取当前实例 URI 拼接原业务路径后发送 RestTemplate 请求，不自行永久缓存，不回退固定地址。删除 USER_SERVICE_URL / TECHNICIAN_SERVICE_URL，仅保留注册中心、服务名及数据库配置。连接超时默认 2000ms、读取 3000ms，可用 HTTP_CONNECT_TIMEOUT_MS / HTTP_READ_TIMEOUT_MS 覆盖。

选定业务：创建住户与维修人员 → 提交报修（发现 user-service 校验住户）→ 审核 → 派单（发现 technician-service 校验技能和可接单状态）→ 确认接单 → 查询。API 路径、请求 DTO 与原业务规则保持不变。

空实例或发现异常返回 503，连接失败/超时返回 503，资源缺失返回 404，状态或技能冲突 409；失败时不跳过校验新增工单。完整可执行请求在 smoke-test.py，脚本自动创建本次独立数据。

```bash
bash scripts/smoke-test.sh
# 查询业务数据
curl --noproxy '*' -i http://localhost:8093/api/repair-orders
```

## 3. 健康状态、下线与恢复

```bash
sudo bash scripts/smoke-test.sh --with-failure
# 等价核心操作，由脚本自动等待注册列表变化并尝试恢复：
sudo docker compose stop user-service
# 用新的有效业务请求提交工单，检查 HTTP 503 和数量不变。
sudo docker compose start user-service
# 等待重新注册和 Actuator UP，再以新描述提交并检查 HTTP 201。
```

脚本在下线前准备真实存在的住户，停服后使用相同有效 residentId 和新的描述提交，避免以无效数据伪造失败。停止后读取 Nacos 当前实例列表，检查无健康实例；对失败前后的工单数量做比较。恢复后再次检查注册及健康，并以新的有效请求创建工单，不只查询旧数据。恢复 trap 防止异常退出遗留停服状态。

三个状态必须区别：

- **SDK 连接存活**：客户端与注册中心 gRPC 会话存在，只说明控制通道存活。
- **注册实例状态**：Nacos 当前注册/健康状态，可因主动注销、连接断开或检测而变化；客户端订阅和缓存传播需要时间。
- **Actuator 应用健康**：应用及其已启用的健康指标结果；本项目保留数据库检查。Nacos Discovery HealthIndicator 默认不据此保证业务调用成功。本地健康 UP 不代表所有远程流程可用，Actuator DOWN 也不自动等价 Nacos 摘除实例。

停止 user-service 后其 HTTP 健康接口不可达，不伪称返回了 DOWN；repair-service 可以仍 UP，但提交须返回 503。实际空实例或仍缓存旧地址导致连接失败均不允许写入。脚本等待注册列表达到预期状态，以实际响应和数据数量核对，而非假设立即传播。

远端 GET 校验成功与本地事务提交之间仍可能发生失败，发现机制不消除此窗口。当前远端仅查询，无远端新增写入，但资料可能在本地提交前变化；不实现分布式事务、自动补偿、自动重试、轮询或熔断。

## 4. 验证脚本、测试与结论

```bash
# 仓库根目录
bash scripts/smoke-test.sh
sudo bash scripts/smoke-test.sh --with-failure
for service in user-service technician-service repair-service; do
  (cd services/$service && ./mvnw test) || exit 1
done
```

普通 smoke 检查实际控制台 HTTP HTML 页面、三个应用健康、个人分组下的注册实例、完整报修流程以及 404/409；停服模式增加注销、503 不落库和恢复后 201。任一检查失败抛出异常，Bash set -euo pipefail 不吞掉失败；已额外以错误控制台端口验证进程退出非零。控制台可以打开不等于业务调用成功。

所有测试使用 test profile 关闭 Nacos 自动注册，数据库使用 H2，发现组件由 Mockito 模拟。DependencyHttpTests 对本地临时 HTTP 服务器发起实际请求，覆盖读取成功/404、连接拒绝、空实例503、每次操作重新发现；不连接真实 Nacos 或 MySQL。Windows 分别进入三个目录运行 .\mvnw.cmd test。

本机已通过 19 项测试：user 4、technician 4、repair 11，0 失败、0 错误。真实容器验证已完成：工单 3 经提交、审核、派单、接单进入 ACCEPTED；user-service 下线时实例从 1 变 0，新有效请求返回 503，工单数量保持 4；恢复注册后实例回到 1，同一住户的新描述请求返回 201，新增工单 5。三个健康接口正常时均返回 200/UP。正常流程、404/409 业务规则、空实例、下线失败与恢复均通过。

| 检查项 | 实际结果 | 证据 |
| --- | --- | --- |
| Nacos 初始化与控制台 | 初始化凭据可登录；真实控制台 18088 返回 HTTP200 | [初始化](evidence/initialization.txt)、[控制台](evidence/console.txt) |
| 注册列表与地址 | PERSONAL_GROUP 三个服务，实例分别为 172.20.0.4:8091、172.20.0.5:8092、172.20.0.6:8093 | [正常注册](evidence/registry-normal.json) |
| 完整受理流程 | 工单 3：SUBMITTED → APPROVED → ASSIGNED → ACCEPTED，HTTP201/200 | [实际请求响应](evidence/normal.json)、[寻址日志](evidence/discovery-trace.txt) |
| 住户服务下线 | 实例为 0；HTTP503，消息为依赖服务没有可用实例；工单数量4→4 | [下线注册](evidence/registry-offline.json)、[失败响应](evidence/outage.json) |
| 住户服务恢复 | 实例为1；新请求HTTP201，工单5为SUBMITTED | [恢复注册](evidence/registry-recovery.json)、[恢复响应](evidence/recovery.json) |
| 自动化测试 | 19项、0失败、0错误；测试不依赖真实 Nacos/MySQL | [本机测试](evidence/windows-tests.txt)、[镜像构建](evidence/build.txt) |
| 完整脚本返回值 | 正常/停服/恢复全通过，退出0；故意使用错误控制台端口退出1 | [全程日志](evidence/smoke.txt)、[负向检查](evidence/negative-console-check.txt) |

镜像构建因缓存清理重新下载依赖后通过。一次临时 HTTP 测试使用300ms超时，在低资源虚拟机上触发无关超时，现将本地服务器成功请求的测试超时调整为应用默认的2秒连接、3秒读取后重新通过；未放宽业务失败断言。日志仅清理行尾空格，HTTP状态码、响应和数据变化保持真实。

### 截图提交对应

以下是后台浏览器对实际源码、真实请求与注册记录报告的截图，原始来源保存在 evidence/，未模拟 IDE 或终端，不使用 Computer Use。截图已逐张检查排版，密码和 Token 不展示。

| 作业小题 | 上传图片 |
| --- | --- |
| 1 部署 Nacos 并注册服务 | [01-registration.png](screenshots/01-registration.png) |
| 2 配置隔离并完成服务发现调用 | [02-discovery-workflow.png](screenshots/02-discovery-workflow.png) |
| 3 验证健康状态、下线与恢复 | [03-offline-recovery.png](screenshots/03-offline-recovery.png) |
| 4 验证脚本 | [04-script-tests.png](screenshots/04-script-tests.png) |

仓库上传不代替作业系统提交；各题上传对应图片，并填写下面的说明文字。

### 虚拟机资源调整后复核

按用户要求将磁盘20GB扩到100GB，再将2 vCPU/4GB调整为8 vCPU/16GB。Ubuntu 实际识别8个CPU，内存约15GiB、可用约10GiB，根文件系统约99GB、空闲约75GB，交换分区使用0。调整后正常重启验证：Nacos 原管理员凭据仍可登录，三个应用均200/UP，工单3仍ACCEPTED、工单5仍SUBMITTED。实例会重新分配IP，本次重启后repair-service为172.20.0.3，不应硬编码截图中的旧IP。

证据：[系统资源](evidence/vm-resources.txt)、[重启后健康与原工单](evidence/post-resources.json)、[重启后注册实例](evidence/registry-post-resources.json)。截图展示的是前一次完整正常/下线/恢复验证，时间与对应原始记录一致。



## 5. 提交到作业系统的文字

### 1 部署 Nacos 并注册服务

本项目沿用社区报修平台三个业务服务，在根目录 Compose 加入 Nacos 3.2.4，启用鉴权与持久化卷，先初始化管理员再启动业务。服务注册到 public 命名空间的 PERSONAL_GROUP，控制台映射 18088，服务端和 gRPC 分别映射 8849、9849，真实密码和 Token 不提交。

### 2 配置隔离并完成服务发现调用

社区报修平台在提交工单前通过 DiscoveryClient 获取 user-service 当前实例地址并校验住户，派单时发现 technician-service 并校验技能。三个服务统一使用 PERSONAL_GROUP，地址与凭据通过环境变量注入，移除原固定业务地址。空实例返回 503，资源缺失保留 404，失败时不新增工单。

### 3 验证健康状态、下线与恢复

实测正常报修完成接单，住户服务下线后实例数从1变0，返回503且工单数量保持4；恢复后重新注册，新有效请求返回201。区分 SDK 连接、注册健康与应用 Actuator 健康，恢复后重新提交有效请求验证成功，不将健康页面可访问当作业务成功，也不声称实现分布式事务。

### 4 验证脚本

从仓库根目录执行 bash scripts/smoke-test.sh 检查实际控制台地址、所有服务健康、注册实例及业务流程；停服模式验证失败不落库与恢复。任一检查失败返回非零。三个服务共 19 项测试通过，测试使用 H2 和模拟发现组件，不依赖真实 Nacos 或 MySQL。
