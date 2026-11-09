# Nacos 3.2.4 控制台保护阈值误报修复

## 现象与原因

2026-11-09，个人项目的三个服务在新版控制台都显示红色“触发保护阈值”，但各有 1 个健康实例。

实际 API 检查：`protectThreshold=0.0`、`healthy=true`、`enabled=true`，服务列表的 `triggerFlag` 为字符串 `"false"`。3.2.4 镜像内新版控制台的 `serviceManagement.js` 使用 `t.triggerFlag ? ... : ...` 判断，JavaScript 非空字符串 `"false"` 为真，因此显示误报。

官方有对应 [Issue #15567](https://github.com/alibaba/nacos/issues/15567) 和 [修复 PR #15610](https://github.com/alibaba/nacos/pull/15610/files)。本机实际镜像仍存在该判断，不能仅根据版本号假定修复已包含。

## 修复范围

保留 `nacos/nacos-server:v3.2.4` 为基础镜像，使用 `infra/nacos/Dockerfile` 构建 `community-repair/nacos:3.2.4-console-fix`。

`patch-console.py` 仅修复内嵌 `nacos-console-3.2.4.jar` 的 `static/next/js/serviceManagement.js` 一处条件：只有布尔 `true` 或字符串 `"true"` 才显示告警，字符串 `"false"` 不显示。真正触发保护时仍然显示告警，没有使用 CSS 隐藏告警，也不更改保护阈值、实例健康、账号或业务数据。

补丁保留原 JAR 项目的压缩方式和其他资源，构建时校验 ZIP 完整性。找不到精确目标或存在多个待替换条件时会失败，不会宽泛替换；重复应用可得到相同结果。该补丁针对已核验的 3.2.4 产物，升级基础镜像时必须重新检查，不能直接套用。

## 构建与启动

在 Ubuntu 当前项目根目录执行：

```bash
python3 infra/nacos/test_patch.py
sudo docker compose config --quiet
sudo docker compose build nacos
sudo docker compose up -d --no-deps nacos
sudo docker compose ps nacos
```

只重建个人 Nacos，原 `nacos-data`、`nacos-logs` 和业务数据库卷保留。等待 Nacos 就绪、业务服务重新注册后检查页面。

Windows 打开 <http://192.168.158.128:18088/next/#/serviceManagement?namespace=public&namespaceShowName=public>，按 **Ctrl+F5** 强制刷新，避免浏览器继续使用旧脚本缓存。

## 本次结果

- 补丁测试 3 项通过，保留非目标资源、嵌套 JAR 压缩方式和重复执行一致性；未知或多目标脚本安全失败。
- JavaScript 条件验证：布尔与字符串 false 不显示告警，布尔与字符串 true 仍显示告警。
- 实际后台浏览器登录新版控制台后，三个服务的保护状态均为 `-`，实例数和健康实例数各为 1。
- 三个业务服务 Actuator 均为 UP，报修页面的住户及维修人员列表可通过服务发现正常读取，已有工单 11 仍为 ACCEPTED。
- [修复后控制台截图](troubleshooting/nacos-console/console-after.png) 和 [不含凭据的验证记录](troubleshooting/nacos-console/verification.json)。

该修复解决的是控制台显示问题，不代表关闭 Nacos 的真实保护机制。

## 本机回退

修改前编排备份保存在 Ubuntu 的 `~/compose.before-console-fix.yaml`。如需恢复原镜像，在 `~/community-repair-week07` 中执行：

```bash
cp ~/compose.before-console-fix.yaml compose.yaml
sudo docker compose up -d --no-deps --no-build nacos
```

回退不删除数据卷。原镜像会再次出现上述显示误报；若后续官方版本已经正确修复，应重新评估后再移除本地补丁。
