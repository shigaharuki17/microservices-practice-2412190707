# 社区报修平台微服务拆分设计

保留 monolith/ 原单体版本；在 services/ 下建立三个独立 Maven 工程，基础包均为 com.zjgsu.hly，各自管理模型、Controller、Service、Repository、配置与测试。

## 拆分映射和数据所有权

| 原单体职责 | 微服务 | 端口 | 独立 MySQL / 数据库 | 应用账号 | 自有表 |
|---|---|---:|---|---|---|
| 工单输入中内嵌的住户信息 | user-service | 8091 | user-db / user_db | user_app | residents |
| Technician 及创建、查询 | technician-service | 8092 | technician-db / technician_db | technician_app | technicians |
| RepairOrder 和审核、派单、接单 | repair-service | 8093 | repair-db / repair_db | repair_app | repair_orders |

住户档案从工单输入中独立管理，本周不实现认证登录。人员服务拥有技能与可接单信息；工单服务聚合状态流程。三个 MySQL 8.4.11 容器、三个账号、三个命名卷隔离数据；数据库不公开宿主机端口。每个应用只获得自己数据库的密码，不拥有其他服务的数据库凭据。

工单 residentId 和 technicianId 是普通 Long 字段，不建立跨库外键，不使用跨服务 JPA 关联，不直接引用其他服务 Repository。住户姓名与电话保存为提交时的历史快照，来自住户服务而非客户端伪造值；之后资料变化不会自动覆盖历史工单。version 字段防止本地并发覆盖。

## 调用关系

```mermaid
flowchart LR
    Client[客户端 / smoke-test.sh] --> User[user-service :8091]
    Client --> Tech[technician-service :8092]
    Client --> Repair[repair-service :8093]
    Repair -->|RestTemplate GET /api/residents/id| User
    Repair -->|RestTemplate GET /api/technicians/id| Tech
    User --> UDB[(user_db: residents)]
    Tech --> TDB[(technician_db: technicians)]
    Repair --> RDB[(repair_db: repair_orders)]
```

## 跨服务业务流程

1. 创建住户档案和维修人员，获得各自 ID。
2. 提交工单时，repair-service 通过真实 HTTP 查询 user-service；校验成功后才写 repair_db，状态 SUBMITTED。
3. 审核通过进入 APPROVED。
4. 派单前通过 HTTP 查询 technician-service，校验存在、available=true、skill 匹配；成功后保存 technicianId，状态 ASSIGNED。
5. 被分配人员确认接单，校验其在人员服务中仍存在，进入 ACCEPTED。

创建返回 201，查询及操作成功 200；输入错误 400，资源缺失 404，状态/技能冲突 409，依赖错误响应 502，连接失败/超时 503。错误时不得跳过校验写入工单。住户缺失、住户服务停止时，工单数量不变；技能不匹配时仍为 APPROVED，technicianId 为 null。

## HTTP 与服务发现配置（第七周）

使用 RestTemplate，保留连接 2000ms、读取 3000ms 超时（HTTP_CONNECT_TIMEOUT_MS、HTTP_READ_TIMEOUT_MS）。DependencyClient 每次 resident/technician 操作都调用 DiscoveryClient.getInstances，取当前实例 URI 拼接业务路径；不保留固定 endpoint，不使用普通 RestTemplate 的“服务名 URL”冒充服务发现。空实例或发现异常映射 503，HTTP 404 保留为业务资源不存在。

三个服务名称与上述映射表一致，通过 Nacos 3.2.4 注册到 public 命名空间和 PERSONAL_GROUP，与课堂 COURSE_GROUP 隔离。Compose 内 NACOS_SERVER_ADDR=nacos:8848，用户、密码、分组与命名空间由环境变量注入。公开模板仅占位，不提交真实 .env。测试 profile 禁用 Nacos，H2 与模拟 DiscoveryClient 独立验证。

```mermaid
flowchart LR
    Repair[repair-service] -->|getInstances 每次重新查询| Nacos[Nacos 3.2.4 / PERSONAL_GROUP]
    User[user-service] -->|注册实例| Nacos
    Tech[technician-service] -->|注册实例| Nacos
    Repair -->|注册实例| Nacos
    Nacos -.->|当前实例 URI| Repair
    Repair -->|HTTP /api/residents/id| User
    Repair -->|HTTP /api/technicians/id| Tech
```

## 一致性窗口和当前边界

远端 GET 校验和本地提交不属于同一原子事务。远端查询成功后，资料可能变化，本地事务也可能失败；HTTP 200 不保证工单保存成功。当前远端调用都是 GET，因此不会出现远端新增写入残留，但存在校验与提交之间的时间窗口。未来增加远端扣款或名额占用时，会出现远端已写、本地失败，需要后续幂等、消息、Saga 或补偿设计。

本周仅实现各自数据库、本地事务、基础 HTTP 和失败处理，不声称实现跨服务原子事务、自动补偿、消息、认证授权或生产级高可用注册中心。旧单体数据不自动迁入新库，脚本每次创建自己的演示数据。

参考：[Spring HTTP 客户端](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)、[Compose 健康依赖](https://docs.docker.com/reference/compose-file/services/)。

第七周验证见 [服务上下线记录](homework/week-07/index.md)。参考 [Nacos 接入](https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/quick-start/) 和 [Nacos 鉴权](https://nacos.io/en/docs/latest/manual/admin/auth/)。
