# Holiday Resort Booking System

团队共享的 Web 工程。当前提供 Spring Boot 骨架、安缦参考方向的客户界面，以及 B 的房型浏览、可用性查询、STAFF 房间管理和可选的预订流程、账户与会员 UI 演示。房型、基础价、日期占用与可用数量来自内存样例数据；尚未接入真实库存、注册、预订、支付、提醒或数据库业务表。

查询支持日期、房型和房间数量，并在后端校验输入、计算整个住期连续可用的房间。快速演示见 [房间查询与样例场景](docs/room-query-demo.md)。

## B 本地交接版本

2026-10-30，B-LOCAL-2026-10-30-01：Azul Zulu Java17实际运行，源工程及干净解压目录分别通过222项测试。已补键盘跳转/错误摘要焦点和减少动态效果。完整结果、运行要求及尚待人工/团队验证项目见 [交接前检查](docs/b-closeout.md)。这个编号不是Git tag或全系统最终验收版本。

已有JDK17可使用 `JAVA17_HOME=/your/jdk17 ./scripts/with-java17.sh ./scripts/dev-ui.sh`；或配置JAVA_HOME后使用原入口。交接源码包可执行 `python3 scripts/package-handoff.py --release B-LOCAL-2026-10-30-01` 生成，产物与校验文件位于deliverables，未打包缓存/JDK/临时密码。

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

所有修改都在内存中，重启恢复初始样例。正式登录由 A 接入，MySQL 持久化由 D 接入。操作步骤见 [管理演示](docs/room-management-demo.md)。

## 独立预览预订流程 UI

先停止旧服务，再执行：

```sh
./scripts/dev-ui.sh
```

打开 <http://localhost:8080/preview/stays/new>。也可以在客房页查询日期和数量，点击结果中的“安排入住 · 演示”。页面包含入住确认、会话草稿详情、支付状态、我的预订和空状态。脚本同时保留本地 STAFF 管理预览。

此入口仅在回环 `dev,local-staff,booking-ui,account-ui,staff-booking-ui` 环境启用。草稿只留在当前会话，不创建 Booking、不占库存或扣款；动态选择不计算最终总价，支付状态使用固定示例。正式认证、报价、订单和支付仍由 A/C 接入。步骤与接入清单见 [预订 UI 演示](docs/stay-ui-demo.md)。

## 账户与会员 UI

同样使用 `./scripts/dev-ui.sh`，打开 <http://localhost:8080/preview/account/login>。可走注册表单 → 结果 → 登录后页面预览 → 个人中心 → 会员开通/续费展示。

演示密码只做表单校验，不保存、不回显，不进行真实认证或授予权限。会员费、有效期规则与折扣留待 A/C 返回；开通/续费按钮不能付款。账户预览退出只清除自己的展示资料，不影响 STAFF 身份和预订草稿。详见 [账户演示](docs/account-ui-demo.md)、[B 的剩余交接清单](docs/b-handoff-checklist.md)。

## 工作人员预订管理 UI

同样使用 `./scripts/dev-ui.sh`，打开 <http://localhost:8080/admin/stays>，以本地 STAFF 身份登录。支持编号/入住日/状态组合筛选、详情与保留筛选的返回，以及房间/预订后台导航。记录为6条独立虚构样例，金额和状态只读；没有确认收款、取消/改期或库存释放。详见 [工作人员预订演示](docs/staff-stay-ui-demo.md)。默认开发环境不启用此入口。

## 正式账户与会员（A）

启动后打开 <http://localhost:8080/account/register> 可注册真实账户；`/account/login` 登录后进入个人中心 `/account` 与会员页 `/account/membership`。STAFF 与 CUSTOMER 共用同一登录页，登录后按角色跳转。

密码以 BCrypt 存储，要求 8–72 个字符且同时包含字母和数字；邮箱大小写不敏感且唯一。会员基线为 CNY 680.00、住宿 95 折、自支付确认日起 365 天；开通申请保持待确认，待支付服务（C）核对后生效。开发环境数据在内存中，重启恢复；MySQL 持久化由 D 接入。需求与契约见 [Member 与账户需求](docs/requirements/member-account.md) 与 [接口登记](docs/interfaces.md)。

## 检查与打包

```sh
./scripts/test.sh
./scripts/maven.sh --version
```

`test.sh` 执行 `verify`：编译、基础集成测试和可运行 JAR 打包。输出位于 `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar`。

```sh
java -jar target/holiday-resort-booking-0.1.0-SNAPSHOT.jar
```

测试覆盖基础设施、安全边界、日期区间、Room 查询、管理写操作、原子更新、本地预览登录、预订 UI 输入、会话隔离、只读支付示例、账户表单、站内返回白名单与会员展示；其他成员的业务模块尚未实现。实际执行结果见 [交接记录](handoff/CURRENT.md)。

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
