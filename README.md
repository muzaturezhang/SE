# Holiday Resort Booking System

**Group 6:** Nacy Lin, Muzature Zhang, Freya Wang, Mila Liu。

团队共享的 Holiday Resort 预订 Web 工程，基于 Java 17、Spring Boot、Thymeleaf、H2/MySQL 8。当前正式客户流程已包含注册/登录、客房查询、实时报价、下单与库存占用、我的预订、改期、取消、sandbox 支付和会员费支付；正式 STAFF 流程已包含房间管理、真实预订列表/详情、筛选分页和支付结果核对。D 提供版本化 Flyway schema、H2/MySQL JDBC adapter、统一锁顺序、提醒/邮件端口、CI、JaCoCo 和可重复测试。B/A/C 的独立 preview 路由仍保留用于界面演示，但不再代表正式业务闭环。用户测试和外部邮箱投递仍需真实参与者和外部环境，仓库不会伪造这些证据。

## 当前正式功能

- 客户：`/account/register`、`/account/login`、`/stays/new`、`/stays`、`/stays/{reference}`，支持服务端报价、幂等下单、改期、取消、支付和会员费支付。
- STAFF：`/admin/rooms`、`/admin/stays`、`/admin/stays/{reference}`，支持真实预订筛选分页、房号与状态查看，以及 `UNDER_REVIEW` 支付的人工确认/拒绝。
- 数据：默认 `dev` 使用 H2；`mysql` profile 使用 MySQL 8 与 Flyway V1–V8。正式金额始终由后端计算并保存价格快照。
- 验证：`.\scripts\test.ps1` 执行完整测试、JAR 构建、JaCoCo 90% 行覆盖率闸门。最新工程结果为 338 项测试、0 失败、5 项本地 MySQL 环境跳过，GitHub Actions 双 Java 17/MySQL 8 任务通过。
- 截图：正式客户与 STAFF 页面截图位于 `docs/screenshots/final-2026-11-14/`，含桌面和移动视口。
- 证据边界：本地环回 SMTP 已实测；外部 SMTP/Mailtrap 投递和 Q5/Q7 用户测试尚未收集，不能作为已完成的真实验证报告。

## 本地正式使用与测试

在 PowerShell 中运行：

```powershell
cd "C:\Users\Lenovo\Documents\ChatGPT\软件工程\SE"
.\scripts\run-stage4.ps1
```

脚本使用项目内 JDK 17、持久化本地 H2 测试库 `data/local/stage4-use` 和随机生成的本地 STAFF 密码。服务就绪后打开：

- 客户注册：<http://127.0.0.1:8080/account/register>
- 客户预订：<http://127.0.0.1:8080/stays/new>
- 客户订单：<http://127.0.0.1:8080/stays>
- STAFF 登录：<http://127.0.0.1:8080/account/login>
- STAFF 预订管理：<http://127.0.0.1:8080/admin/stays>

STAFF 用户名为 `localstaff`，密码保存在：

```powershell
Get-Content .\.cache\staff-preview\password.txt
```

测试使用虚构姓名和 `@example.invalid` 邮箱。支付页面只连接 sandbox，不能输入真实银行卡或真实支付密码。停止服务时回到运行脚本的 PowerShell 窗口按 `Ctrl+C`。本地 H2 测试数据在脚本重启后保留；如需全新数据，可在服务停止后删除 `data/local/stage4-use.mv.db`。

队友统一使用说明见 [Group 6 成品使用与测试说明](docs/group6-team-testing-guide.md)。

查询支持日期、房型和房间数量，并在后端校验输入、计算整个住期连续可用的房间。快速演示见 [房间查询与样例场景](docs/room-query-demo.md)。

## B 本地交接版本

2026-10-30，B-LOCAL-2026-10-30-01：Azul Zulu Java17实际运行，源工程及干净解压目录分别通过222项测试。已补键盘跳转/错误摘要焦点和减少动态效果。完整结果、运行要求及尚待人工/团队验证项目见 [交接前检查](docs/b-closeout.md)。这个编号不是Git tag或全系统最终验收版本。

已有JDK17可使用 `JAVA17_HOME=/your/jdk17 ./scripts/with-java17.sh ./scripts/dev-ui.sh`；或配置JAVA_HOME后使用原入口。交接源码包可执行 `python3 scripts/package-handoff.py --release B-LOCAL-2026-10-30-01` 生成，产物与校验文件位于deliverables，未打包缓存/JDK/临时密码。

## D 的通知与持久化实现

2026-11-07，D 在 `notification` 与 `persistence` 包完成以下内部开发基线：

- Flyway `V1`–`V8`：提醒、房型/房间/占用、客户/员工/会员、预订/支付、审计历史、统一锁表、房型种子数据及 booking/payment/membership 对齐。
- `NotificationService`：上海时区一天前提醒、09:00 发送时间、取消、改期自动重排、有界重试、版本化认领、隐私安全错误码。
- `EmailTransport`：默认 `MockEmailTransport`；可选 `smtp-email` profile 使用环境变量配置的 SMTP，不在源码保存凭据。
- JDBC 持久化：Room、Member、Membership、Staff、Booking、Payment 和 Reminder adapter；Booking 使用独立 `BookingInventoryPort`，避免把预订占用混入 B 的 staff room management 端口。
- 测试基础设施：H2 全新 schema、Flyway 约束测试、JUnit/JaCoCo、覆盖率门槛、Windows/Linux 入口和 GitHub Actions。

详细边界、联调调用和未验证项见 [D 通知与持久化实现](docs/d-persistence-notification-foundation.md)。默认 `dev`/`test` 保留内存业务 adapter；`mysql` profile 使用 JDBC adapter，真实 SMTP 需要显式 `smtp-email` profile 与环境配置。
## 快速启动

需要 JDK 17 或更新版本；项目编译目标为 Java 17。首次运行需要联网下载 Maven 和依赖，不需要另外安装 Maven。

macOS / Linux，在本项目目录执行：

```sh
./scripts/dev.sh
```

浏览器打开 <http://localhost:8080>；客房与别墅页面为 <http://localhost:8080/rooms>。终端按 Ctrl+C 停止。

预览依赖正在运行的本地服务。查看页面时保持启动终端运行；如果出现连接被拒绝，先重新执行 `./scripts/dev.sh`，等待出现 `Tomcat started on port 8080` 后刷新页面。

Windows PowerShell：

```powershell
./scripts/maven.ps1 spring-boot:run
```

启动默认使用 `dev` profile 和临时 H2 数据库，不需要 MySQL、支付密钥或邮件账号。关闭进程后开发数据丢失。页面静态资源已保存在本地，运行时不需要访问 CDN。

## 独立验证房间管理

先停止占用 8080 的服务，再运行：

```sh
./scripts/dev-staff.sh
```

打开 <http://localhost:8080/admin/rooms>，用户名为 `localstaff`，随机密码保存在本机 `.cache/staff-preview/password.txt`（不会提交 Git）。此脚本仅在本机 `dev,local-staff` 启用临时 STAFF 身份；默认 `dev.sh` 不提供账号。支持新增、修改、停用、重新启用、房型基础价维护和房型/状态筛选。

默认 `dev` 的所有修改在内存中，重启恢复初始样例；D 的 `mysql` profile 已提供事务化 Room JDBC 适配和版本化 schema。正式登录与 Booking/Payment JDBC adapter 已接入共享代码，B 的 preview 路由仍不等于真实页面验收。操作步骤见 [管理演示](docs/room-management-demo.md)。

## 独立预览预订流程 UI（保留）

先停止旧服务，再执行：

```sh
./scripts/dev-ui.sh
```

打开 <http://localhost:8080/preview/stays/new>。也可以在客房页查询日期和数量，点击结果中的“安排入住 · 演示”。页面包含入住确认、会话草稿详情、支付状态、我的预订和空状态。脚本同时保留本地 STAFF 管理预览。

此入口仅在回环 `dev,local-staff,booking-ui,account-ui,staff-booking-ui` 环境启用。草稿只留在当前会话，不创建 Booking、不占库存或扣款；动态选择不计算最终总价，支付状态使用固定示例。正式流程请登录客户账户后访问 `/stays/new`。步骤与接入清单见 [预订 UI 演示](docs/stay-ui-demo.md)。

## 账户与会员 UI

同样使用 `./scripts/dev-ui.sh`，打开 <http://localhost:8080/preview/account/login>。可走注册表单 → 结果 → 登录后页面预览 → 个人中心 → 会员开通/续费展示。

演示密码只做表单校验，不保存、不回显，不进行真实认证或授予权限。会员费、有效期规则与折扣留待 A/C 返回；开通/续费按钮不能付款。账户预览退出只清除自己的展示资料，不影响 STAFF 身份和预订草稿。详见 [账户演示](docs/account-ui-demo.md)、[B 的剩余交接清单](docs/b-handoff-checklist.md)。

## 工作人员预订管理（正式）

STAFF 登录后访问 <http://localhost:8080/admin/stays>。正式页面读取真实 Booking/Payment 数据，支持编号/入住日/预订状态组合筛选、20 条分页、房号与价格快照、独立展示预订/支付状态，并仅对 `UNDER_REVIEW` 支付开放“确认已付/拒绝”核对；拒绝原因需要 3–255 个字符。不会提供任意改金额、状态或库存的入口。STAFF 账号由环境配置预置，不通过公开注册创建。

## 正式账户与会员（A）

启动后打开 <http://localhost:8080/account/register> 可注册真实账户；`/account/login` 登录后进入个人中心 `/account` 与会员页 `/account/membership`。STAFF 与 CUSTOMER 共用同一登录页，登录后按角色跳转。

密码以 BCrypt 存储，要求 8–72 个字符且同时包含字母和数字；邮箱大小写不敏感且唯一。会员基线为 CNY 680.00、住宿 95 折、自支付确认日起 365 天；开通申请保持待确认，待支付服务（C）核对后生效。开发环境默认使用内存 adapter，重启恢复；`mysql` profile 已由 D 接入 Customer/Membership/Staff JDBC adapter 与审计表。需求与契约见 [Member 与账户需求](docs/requirements/member-account.md) 与 [接口登记](docs/interfaces.md)。

## 检查与打包

```sh
./scripts/test.sh
./scripts/maven.sh --version
```

`test.sh` 执行 `verify`：编译、基础集成测试和可运行 JAR 打包。输出位于 `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar`。

```sh
java -jar target/holiday-resort-booking-0.1.0-SNAPSHOT.jar
```

测试覆盖基础设施、安全边界、日期区间、Room 查询、管理写操作、原子更新、本地预览登录、预订 UI 输入、会话隔离、账户表单、站内返回白名单、会员展示、D 的提醒/重试/取消/重排、SENDING lease 恢复、完整 schema 约束、统一锁、Booking/Payment/Member/Staff JDBC adapter、Booking→Reminder 生命周期、失败注入和释放后重新销售。当前本地验证为 318 项测试、0 失败、0 错误、5 项真实 MySQL 环境门禁 skip，JaCoCo 总行覆盖率 90.42%、分支覆盖率 74.42%；GitHub Actions push/PR 两套 workflow 的 Java 17 verification 和 MySQL 8 integration 均已通过。实际执行结果见 [D 测试报告](docs/test/d-test-report.md) 与 [证据索引](docs/evidence-index.md)。

## 目录

```text
src/main/java/com/holidayresort/booking/
  config/          公共配置与安全基础  A 协调
  web/             共享页面入口  B
  member/          Customer Member  A
  room/            Room Search Availability  B
  booking/         Booking  C
  payment/         Payment  C
  notification/    Reminder Email  D
  persistence/     Repository 实现与持久化基础  D
  shared/          经团队确认的公共类型
src/main/resources/
  templates/       Thymeleaf 页面  B
  static/          本地 Bootstrap 与共享样式  B
  db/migration/    数据库迁移预留  D
src/test/          各模块测试与基础集成测试
data/              本地数据 示例数据和上传文件的预留位置
docs/              架构 业务基线和接口记录
log/               按时间追加的工作日志
handoff/           当前状态与下一步
scripts/           开发与检查入口
```

## B 设计与接入材料

[设计入口](docs/design/README.md)提供Room类图/依赖、24顶层类型CRC、客户/工作人员导航、可用性查询交互和逐页UI原则；[Word汇总](docs/design/B_Design_and_Integration_Handoff.docx)便于转发。图保留Mermaid源与SVG/PNG，详细路由/职责可继续维护。

[发给A/C/D的接入说明](handoff/B-team-integration.md)分列各owner输入、7项待评审问题及联调条件。当前是按已实现代码整理的材料，接口状态仍待团队评审，没有代填最终报告或review结果。

## 接续开发入口

1. 先读 [handoff/CURRENT.md](handoff/CURRENT.md)，确认已完成、未完成和已验证内容。
2. 读 [docs/decisions.md](docs/decisions.md) 和 [docs/interfaces.md](docs/interfaces.md)。
3. 按模块开发；修改公共配置、接口、状态或数据库字段时同步受影响成员。
4. 完成一批工作后更新 `handoff/CURRENT.md`，在 `log/` 追加事实记录和检查结果。

MySQL 配置、端口调整与版本说明见 [本地开发指南](docs/local-development.md)。
