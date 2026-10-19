# 第五周：Docker 容器化与持久化验证

项目：社区报修与上门维修服务平台。继续使用原 `monolith/` 工程。

## 1. Dockerfile 与镜像构建

`monolith/Dockerfile` 使用 Maven + Java 25 镜像编译源码并执行 `clean verify`，运行阶段使用 Java 25 JRE，只复制可执行 JAR，以非 root 用户启动。`monolith/.dockerignore` 排除 target、IDE 配置和本地密码文件。构建不依赖宿主机 Java、Maven 或预先打好的 JAR。

```bash
sudo docker build --progress=plain -t community-repair:week05 ./monolith
sudo docker image ls community-repair
```

## 2. Compose 编排

仓库根目录 `compose.yaml` 定义 app、mysql 和共享网络，数据库镜像固定为 `mysql:8.4.11`。应用通过 `mysql:3306` 访问数据库，数据库不向宿主机公开端口。MySQL 使用专用应用账号执行 `SELECT 1` 健康检查，app 等待 `service_healthy` 后启动。

```bash
cp .env.example .env
# 编辑 .env，替换两个密码占位符；不要提交真实 .env。
sudo docker compose config --quiet
sudo docker compose up -d --build
sudo docker compose ps
sudo docker compose logs --tail=50 app
```

容器内部应用端口为 8080，宿主机默认映射 8081（APP_PORT 可修改）。Ubuntu 内访问 `http://localhost:8081`；Windows 访问 `http://192.168.158.128:8081`，虚拟机 IP 改变时替换该地址。

本机 VMware 共享目录是 `/home/weifuwu/VMshare/microservices-practice-2412190707`。由于该 FUSE 挂载不允许 root 直接读取，构建前以普通用户同步源码到 Ubuntu 本地目录，然后运行 Docker：

```bash
mkdir -p ~/community-repair-week05
tar -C /home/weifuwu/VMshare/microservices-practice-2412190707 \
  --exclude=.git --exclude=target --exclude=.idea --exclude=.env.local.ps1 \
  -cf - . | tar -C ~/community-repair-week05 -xf -
cd ~/community-repair-week05
```

其他环境直接进入自己的仓库根目录，无需执行此同步步骤。本机已有课堂示例占用 8080，因此使用独立 Compose 项目 `community-repair` 和 8081 端口。

## 3. 个人业务 API 验证

在运行 Docker 的 Ubuntu 内，从仓库根目录执行：

```bash
curl --noproxy '*' -i http://localhost:8081/actuator/health
curl --noproxy '*' -i -H 'Content-Type: application/json' \
  --data-binary @monolith/examples/order.json http://localhost:8081/api/repair-orders
curl --noproxy '*' -i http://localhost:8081/api/repair-orders
# 将 1 替换为创建响应中的真实 id
curl --noproxy '*' -i http://localhost:8081/api/repair-orders/1
```

健康响应应为 HTTP 200 且 status=UP；创建工单应为 HTTP 201；查询应为 HTTP 200。使用 Python 3 验证脚本可自动保存本次创建的编号和响应：

```bash
python3 scripts/verify-container.py before
```

## 4. 数据持久化验证

Compose 命名卷 `mysql-data` 挂载到 `/var/lib/mysql`，实际卷名带项目名前缀 `community-repair_mysql-data`。执行下面命令前先完成上面的 before 验证：

```bash
sudo docker compose down
sudo docker compose up -d
sudo docker compose ps
# 等待应用启动，健康接口返回 UP 后再执行
python3 scripts/verify-container.py after
```

验证脚本查询同一 id，比较前后完整 JSON，确认 ID、描述、状态、时间及关联人员等字段全部不变。不要使用 `docker compose down -v`，该选项会删除数据卷。单独重启应用不能替代本题要求的容器删除重建。

仅停止、保留容器与数据：`sudo docker compose stop`；再次启动：`sudo docker compose start`。删除容器但保留命名卷：`sudo docker compose down`。

## 5. 实际结果与截图

2026-10-19 在 VMware Ubuntu 中实际完成（文本日志仅清理行尾空格）：

| 验证 | 实际结果 | 原始证据 |
|---|---|---|
| 多阶段构建 | 镜像 community-repair:week05 构建成功，退出码 0；17 项测试全部通过 | [build.txt](evidence/build.txt) |
| Compose 启动 | app 运行，mysql healthy；MySQL 实际版本 8.4.11 | [start.txt](evidence/start.txt)、[runtime.txt](evidence/runtime.txt) |
| 运行镜像 | Java 25.0.4.1，UID 10001；无 Maven、javac，JAR 存在 | [runtime.txt](evidence/runtime.txt) |
| 业务接口 | 健康 200/UP；创建工单 201；查询 200 | [api-before.txt](evidence/api-before.txt) |
| 删除重建 | 两个容器 ID 均变化，2 号工单完整响应一致 | [persistence.txt](evidence/persistence.txt) |
| 数据快照 | 重建前后 JSON 完全相同 | [before](evidence/order-before.json)、[after](evidence/order-after.json) |

Java 创建响应的时间为纳秒精度，MySQL DATETIME(6) 为微秒精度。因此创建与首次查询比较业务字段，持久化验证则比较重建前后两次数据库查询的完整响应。

容器时间日志为 UTC；本地验证日期按北京时间为 2026-10-19。Windows 实测可访问 `http://192.168.158.128:8081/actuator/health` 和 `/api/repair-orders/2`，均返回 200。

### Windows 自带截图工具取证

截图目录为 `screenshots/`。只保存包含真实验证结果且不被聊天窗口遮挡的截图；原始文本日志已保存，图片尚待核对归档。

1. 构建：显示 build.txt 末尾的 BUILD SUCCESS、镜像导出和退出码 0，保存 01-build.png。
2. 服务：显示 `sudo docker compose ps`、镜像列表和 MySQL 版本，保存 02-compose.png。
3. API：显示 api-before.txt 中请求地址、HTTP 201/200、工单响应，保存 03-api.png；可另保存健康响应。
4. 持久化：显示 persistence.txt 中 down/up 命令、重建后的工单 2 及 PERSISTENCE=PASS，保存 04-persistence.png。与 API 图配对证明同一记录重建前后不变。

使用 Windows“截图工具”选择窗口或矩形模式，只截 VMware 中的验证终端。每小题上传相关图片 1–5 张。

## 6. 作业系统填写文本

仓库：https://github.com/shigaharuki17/microservices-practice-2412190707

### Dockerfile 与镜像构建

在仓库根目录执行 sudo docker build -t community-repair:week05 ./monolith，镜像构建成功。构建阶段使用 Maven 和 Java 25 编译源码并通过 17 项测试，运行阶段仅使用 JRE 与可执行 JAR，不依赖宿主机 Java 或 Maven。

### Compose 编排与启动

已使用 Compose 启动 app 和 MySQL 8.4.11 两个服务，应用通过共享网络中的 mysql 服务名连接数据库，账号与密码通过环境变量传入。MySQL 健康检查通过后启动应用，宿主机 8081 映射容器 8080，两个服务正常运行。

### 个人业务 API 验证

通过容器映射端口验证健康接口返回 HTTP 200 和 UP；调用报修创建接口返回 HTTP 201，查询 2 号工单返回 HTTP 200，住户、地址、描述和状态与创建内容一致，证明原业务接口在容器中正常运行。

### 数据持久化验证

创建并查询 2 号报修工单后，执行 docker compose down 和 docker compose up -d 删除重建应用及数据库容器。重新查询同一工单，ID 及完整响应保持一致，验证 MySQL 命名数据卷能够在容器重建后保留数据，过程中未使用 -v。


## 7. 参考资料

- [Maven 官方镜像](https://hub.docker.com/_/maven)
- [Eclipse Temurin 官方镜像](https://hub.docker.com/_/eclipse-temurin)
- [MySQL 官方镜像](https://hub.docker.com/_/mysql)
