# 第六周：个人项目微服务拆分

项目：社区报修与上门维修服务平台。
仓库：https://github.com/shigaharuki17/microservices-practice-2412190707

## 1. 服务拆分设计

保留原 monolith，在 services 新建 user-service、technician-service、repair-service。将住户档案、人员与工单流程分开，各自数据库、账号和数据表；通过 API 协作，仅保存外部 ID。职责映射、端口、数据所有权和 Mermaid 图见 [微服务设计](../../microservices-design.md)。

## 2. 代码拆分

每个服务均有独立 pom.xml、src、Maven Wrapper、application.yml、Controller、Service、Repository、DTO、模型和测试。基础包保持 com.zjgsu.hly。工单服务使用 RestTemplate，地址和连接/读取超时可通过环境变量覆盖。先校验依赖后写本地，不存在 404、不可用 503、技能冲突 409。

从仓库根目录执行（无需 MySQL，测试用 H2）：

```bash
for service in user-service technician-service repair-service; do
  (cd services/$service && ./mvnw test) || exit 1
done
```

Windows 分别进入三个目录执行 `.\mvnw.cmd test`。本机已实际通过 17 项测试：user 4、technician 4、repair 9，0 失败、0 错误。镜像构建也会运行 verify。

## 3. 本地编排

根目录 compose.yaml 一键启动三个应用和三个 MySQL 8.4.11。应用端口为 8091/8092/8093；数据库不映射宿主机端口，使用三个专用账号和三个命名卷，真实 .env 已忽略。

```bash
cp .env.example .env
# 编辑四个密码；本机已经有配置时不要覆盖 .env。
sudo docker compose config --quiet
sudo docker compose up -d --build
sudo docker compose ps
```

本机 VMware 共享挂载不允许 root 直接读取，实际构建前同步到 Ubuntu 本地目录：

```bash
mkdir -p ~/community-repair-week06
tar -C /home/weifuwu/VMshare/microservices-practice-2412190707 \
  --exclude=.git --exclude=target --exclude=.idea --exclude=.env.local.ps1 \
  -cf - . | tar -C ~/community-repair-week06 -xf -
cd ~/community-repair-week06
sudo docker compose up -d --build
```

停止 `sudo docker compose stop`，恢复 `sudo docker compose start`；`down` 保留卷，不要加 -v。原第五周配置保存为 compose.monolith.yaml，恢复旧单体使用 `sudo docker compose -f compose.monolith.yaml up -d --build`，不影响三个新库。

## 4. 验证

根目录基础验证（健康、成功流程、资源不存在、技能不匹配）：

```bash
bash scripts/smoke-test.sh
```

包括真实停服及恢复（需要 Docker 权限）：

```bash
sudo bash scripts/smoke-test.sh --with-failure
```

每次生成独立演示电话，可重复运行。脚本实际检查三个健康接口，创建住户与人员，完成提交审核派单接单。缺失住户 404，停用 user-service 后提交 503，工单数量不变；恢复服务后查询旧工单仍为 ACCEPTED。脚本以 trap 尝试恢复停止的服务。依赖 Bash、Python 3 标准库和 Docker Compose（停服模式）；手工接口命令可用 curl。

可通过 USER_URL、TECHNICIAN_URL、REPAIR_URL 指向其他宿主机。各服务健康地址为 /actuator/health。原始 HTTP 请求、状态码、响应和数据变化保存在 evidence/。

远端查询成功与本地事务提交之间可能发生失败；GET 不写远端但也不保证本地提交。当前不实现跨服务原子事务、自动补偿或消息，详见设计文档的一致性窗口。

## 5. 实际结果与截图

2026-10-26 已在 VMware Ubuntu 实际完成验证。三个应用和三个 MySQL 容器均运行，数据库健康；三个应用账号分别仅拥有自己的数据库权限，工单库外键数量为 0。

| 验证项目 | 实际结果 | 原始证据 |
| --- | --- | --- |
| 自动化测试 | user 4、technician 4、repair 9，共 17 项，0 失败、0 错误；Windows 和镜像构建均通过 | [本机测试](evidence/windows-tests.txt)、[镜像构建](evidence/build.txt) |
| 编排及数据隔离 | 六个容器运行，三个数据库 healthy，三个账号分别授权自己的库 | [运行状态和授权](evidence/runtime.txt)、[启动日志](evidence/start.txt) |
| 跨服务完整流程 | 三个健康接口 200/UP；工单 1 完成 SUBMITTED → APPROVED → ASSIGNED → ACCEPTED，查询 200 | [请求响应](evidence/normal.json)、[运行日志](evidence/normal.txt) |
| 住户不存在 | 返回 404，工单数量保持 1 | [正常及失败场景](evidence/normal.txt) |
| 维修技能不匹配 | 返回 409，工单 2 仍为 APPROVED，technicianId 为 null | [正常及失败场景](evidence/normal.json) |
| 住户服务真实停止 | 返回 503，工单数量保持 2，未绕过远程校验 | [停服响应](evidence/outage.json)、[停服日志](evidence/outage.txt) |
| 服务恢复 | 健康检查 200/UP，工单 1 仍为 ACCEPTED | [恢复响应](evidence/recovery.json) |

[完整脚本输出](evidence/smoke.txt)。日志仅清理行尾空格；请求、响应和结果保留。Windows 访问本次虚拟机使用 `http://192.168.158.128:8093/actuator/health`，IP 随虚拟机网络环境可能变化。

本周未使用 Computer Use。以下 PNG 为后台浏览器对项目源码与真实运行记录报告的截图，页面明确标注原始来源，未模拟 IDE 或终端界面。完整截图和可交叉核对的 HTML 报告均提交仓库。

| 提交位置 | 截图 |
| --- | --- |
| 服务拆分设计（可选证明） | [01 服务边界](screenshots/01-design.png) |
| 代码拆分 | [02 核心源码和 17 项测试](screenshots/02-code-tests.png) |
| 本地编排 | [03 容器状态与数据库隔离](screenshots/03-compose.png) |
| 验证 | [04 完整跨服务流程](screenshots/04-workflow.png)、[05 缺失资源、技能冲突与真实停服](screenshots/05-failures.png) |

作业系统仍需手动上传对应图片；仓库提交不代替作业系统上传。


## 6. 作业系统填写文本

### 服务拆分设计（不少于 50 字符）

本项目沿用社区报修与上门维修服务平台，保留 monolith 单体，拆分住户、维修人员和工单三个独立服务。每个服务拥有自己的数据库、账号和数据表，工单仅保存外部 ID，通过 HTTP 获取其他服务信息，不建立跨库外键。已说明端口、服务边界、调用关系和一致性窗口，并绘制架构图。

### 代码拆分（不少于 20 字符）

已在 services 下建立三个独立 Spring Boot Maven 工程，包含配置、Controller、Service、Repository 和测试。工单使用 RestTemplate 校验住户与人员，配置连接和读取超时，依赖缺失或不可用时返回明确错误并阻止本地写入。

### 本地编排（不少于 20 字符）

根目录 Compose 配置三个业务服务及三个 MySQL 数据库，以专用账号和命名卷隔离数据。容器通过 Compose 服务名通信，环境变量传入配置，应用等待各自数据库健康后启动，真实密码不提交仓库。

### 验证（不少于 20 字符）

使用根目录 smoke-test.sh 检查全部健康接口和跨服务报修流程，并验证住户不存在、住户服务停止时返回 404 或 503 且工单数量不变。恢复服务后仍可查询工单，文档说明了远端查询成功而本地失败的一致性窗口。
