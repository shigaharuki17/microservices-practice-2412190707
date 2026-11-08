# 可视化报修工作台

## 访问和启动

网页直接部署在 `services/repair-service/src/main/resources/static/`，随 Spring Boot 打包。

- Windows：`http://192.168.158.128:8093/`
- Ubuntu：`http://localhost:8093/`

在 Ubuntu 当前项目根目录执行 `sudo docker compose up -d --build`。本机实际运行副本为 `~/community-repair-week07`，源码仓库保留在 Windows 原目录。更新报修服务可执行 `sudo docker compose up -d --build --no-deps repair-service`，数据库卷及原数据保留。

## 页面操作

1. 在“住户档案”新增姓名、联系电话，已有档案也可直接使用。联系电话不能重复。
2. 在“维修人员”新增人员，选择水管、电路或门锁技能及可接单状态。
3. 回到“工单工作台”，点击“新建报修”，选择住户并填写位置、类别和故障描述。
4. 查看工单详情，选择审核通过或驳回，并填写审核说明。
5. 审核通过后点击“安排维修人员”，仅列出对应技能且可接单的人员。
6. 派单后点击“确认接单”，模拟被分配维修人员接受任务。
7. 搜索工单编号、住户或位置，使用状态、类型筛选及分页查找记录。可点击统计项快速筛选。

操作提交成功后刷新列表与计数。后端失败时保留表单内容并显示错误，不将失败请求显示为成功。未加载成功的数据不会显示为零，已有数据加载失败时保留旧值并明确提示刷新。

## 数据和服务边界

`/api/portal/residents` 和 `/api/portal/technicians` 提供 GET 列表、POST 新增及 GET 单条。PortalController 负责输入输出，DependencyClient 每次操作通过 DiscoveryClient 获取当前实例地址，使用带连接和读取超时的 RestTemplate 转发。输入使用 DTO 校验，实际档案规则及存储仍由对应的 user-service 或 technician-service 完成。页面不连接数据库、不写死依赖 IP、不需要 CORS，也不保存 Nacos 凭据。

原工单 API 及业务 Service 规则继续使用。资源不存在返回 404、冲突返回 409、依赖不可用返回 503。实际维修完成、验收和业务登录权限仍未实现。

## 本次验证

2026-11-08：在 repair-service 目录执行 `.\mvnw.cmd test`，19 项测试通过、0 失败、0 错误。新增测试覆盖网页资源、档案列表和创建、Location 响应头、非法输入、409/503，以及临时真实 HTTP 服务上的发现寻址和 POST 转发。

后台浏览器访问实际 Ubuntu 容器，不使用桌面控制。已通过页面新增住户及维修人员，创建工单 9，依次完成 SUBMITTED、APPROVED、ASSIGNED、ACCEPTED；验证重复电话在表单显示 409，搜索、状态筛选和手机视口无横向页面溢出。

界面验收记录保留在数据库中，原工单未删除。第七周的 19 项测试和历史工单数量属于当时记录，本次新增前端测试后不要把历史数量当作当前总数。

## 功能边界

当前所有角色操作在同一演示工作台进行，没有登录或权限限制。接单界面模拟人员操作，不构成真实身份认证。历史已有服务健康检查和 Nacos 注册功能不等价于业务认证。运行结果以当次接口响应和数据为准。


真实页面停服验证：user-service 停止后，用已存在住户从页面提交新的有效工单，显示 HTTP 503，数量为 9 → 9；恢复并重新注册后，同一页面的新描述提交成功，新增工单 10（SUBMITTED）。另验证分页和服务器文本转义，无浏览器运行异常。演示期间的用户服务已恢复，其他服务和数据库未删除。

## 实际页面截图

- [工单工作台](ui/screenshots/workbench.png)
- [工单详情](ui/screenshots/order-detail.png)
- [依赖失败时的错误提示](ui/screenshots/dependency-error.png)
- [手机布局](ui/screenshots/mobile.png)


截图中的姓名、联系方式和位置均为 smoke-test 及浏览器验收脚本生成的合成演示数据，不对应实际住户或维修人员；测试电话使用固定前缀与时间戳后缀构造。
