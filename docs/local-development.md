# 本地开发指南

## Java17本地验证补充

2026-10-30已在Azul Zulu 17.0.21.0.3 arm64完整构建/运行。历史JDK23说明保留为骨架建立时的事实。可用 `JAVA17_HOME=/your/jdk17 ./scripts/with-java17.sh ./scripts/test.sh` 强制检查17；本机工程缓存JDK不分发，不修改系统默认版本。最新结果/干净包说明见 [交接前检查](b-closeout.md)。

## 版本与下载

| 项目 | 选择 |
| --- | --- |
| Java 源码与字节码目标 | 17 |
| Spring Boot | 3.5.16，固定版本 |
| Maven | 3.9.11，Wrapper 自动下载 |
| Maven Wrapper | 3.3.4 only-script |
| UI | Thymeleaf，Bootstrap 5.3.8，原生 JavaScript |
| 默认数据库 | H2 内存库，仅用于启动和本地开发 |
| 团队数据库基线 | MySQL 8，待 D 建表和验证 |

建骨架时本机只有 JDK 23/22/16/11，没有 JDK 17 或全局 Maven。先使用现有 JDK 23，并以 `--release 17` 编译；正式团队环境建议安装 JDK 17。当前实测环境和结果记录在 handoff 中。切换到 Java 21 等版本需要更新团队约定和本项目配置。

`scripts/maven.sh` 将 Maven 分发包及依赖缓存在 `.cache/maven/`。首次执行需联网，之后已有依赖可复用。直接调用 `mvnw` 时 Wrapper 默认可能使用个人 Maven 目录，所以优先使用 scripts 入口。

Maven 分发包启用 SHA-256 校验；配置校验值前已核对 Maven Central 发布的 SHA-512。Wrapper 脚本来自官方分发包。

## 启动与配置

```sh
./scripts/dev.sh
SERVER_PORT=8081 ./scripts/dev.sh
```

默认仅监听 `127.0.0.1`。需要同一局域网演示时可显式设置 `SERVER_ADDRESS=0.0.0.0`；默认没有登录账号，仅公开客房查询、静态资源和健康检查。管理预览不能通过此方式对局域网开放。

可运行 JAR 也支持 `SERVER_PORT`、`SERVER_ADDRESS` 等环境变量。`app.timezone` 为 Asia/Shanghai，`app.currency` 为 CNY，`BOOKING_HOLD_MINUTES` 默认 15。占用时长目前只提供配置入口，尚无计时和库存业务逻辑。

## MySQL profile

由 D 准备 MySQL 数据库后，在终端或 IDE 中提供以下环境变量并启用 profile：

```sh
export SPRING_PROFILES_ACTIVE=mysql
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/holiday_resort'
export MYSQL_USERNAME='your_local_username'
# MYSQL_PASSWORD 通过本地环境或 IDE 的环境变量设置，不写入仓库
./scripts/dev.sh
```

`mysql` profile 不提供默认密码；三个 MYSQL 变量均应设置。当前没有业务表、SQL 自动建表、Flyway 依赖或迁移脚本。D 还需实现评审后的 RoomRepository 与 RoomInventoryEditor；内存样例适配器在 mysql 下不会注册，所以直接启用该 profile 目前会因缺少 RoomRepository 与写端口而拒绝启动。D 确认 schema 后再接入迁移工具。不得声称远程 MySQL 或业务持久化已验证。

默认开发环境不会读取 `.env` 文件，使用 shell 或 IDE 环境变量注入。H2 控制台关闭。未开放的路由默认拒绝访问；管理路径 `/admin/**` 已要求 `ROLE_STAFF`；A 后续接入 Session 身份，其他路由保持默认拒绝。

## 本地 STAFF 管理预览

```sh
./scripts/dev-staff.sh
```

脚本固定监听 127.0.0.1，启用 `dev,local-staff`。第一次运行用 OpenSSL 生成随机密码，保存在 `.cache/staff-preview/password.txt`，权限 600；后续启动复用。用户名为 `localstaff`。脚本不打印密码，也不写入源码、日志或 fixture。默认 `dev.sh` 不启用这个账号；环境中若残留 `SPRING_PROFILES_ACTIVE`，先清除再启动默认环境。

`GET /admin/rooms` 会转到 `/staff-preview/login`。登录、写操作和退出都保留 CSRF；登录成功后使用 STAFF Session。仅这个可选 profile 提供开发登录；它拒绝与 mysql/test 共用，缺少 dev 或非回环监听时拒绝启动。正式部署不得启用它。A 接入真实登录后保留管理权限规则，移除启动环境中的 `local-staff` 即不注册临时身份。

无 OpenSSL 的环境可通过 IDE 自行设置 `SPRING_PROFILES_ACTIVE=dev,local-staff`、`SERVER_ADDRESS=127.0.0.1`、`LOCAL_STAFF_USERNAME`、`LOCAL_STAFF_PASSWORD`（至少 16 字符），然后使用原有 Maven 启动入口。此替代步骤和 Windows 尚未实测。密码过长应避免超过 BCrypt 的 72 字节输入限制；脚本生成 36 个 ASCII 字符。

管理修改只替换内存快照，重启即恢复 fixture；不是 MySQL 持久化。不要同时运行 `verify` 和会编译源码的 dev 脚本，避免 target 输出竞争。

## 预订流程 UI 演示

```sh
./scripts/dev-ui.sh
```

脚本通过 `BOOKING_UI_PREVIEW=true` 、`ACCOUNT_UI_PREVIEW=true` 和 `STAFF_BOOKING_UI_PREVIEW=true` 复用 dev-staff.sh，启用 `dev,local-staff,booking-ui,account-ui,staff-booking-ui`，固定回环监听并复用已有本地 STAFF 凭证。普通 dev.sh/dev-staff.sh 不默认启用 booking-ui。客户 UI 预览页无需登录，只展示虚构示例和当前会话草稿；正式业务路由尚未开放。

访问 `/preview/stays/new` 或从客房查询结果进入。`/preview/stays` 显示记录，`?example=empty` 显示空状态，`/preview/stays/{reference}` 是详情，末尾 `/payment` 为支付页面。页面响应设置 no-store，姓名/邮箱用 POST 提交，仅保留一个当前会话草稿，不写日志、fixture 或数据库；建议保留预填演示资料。

booking-ui 要求 dev、回环地址，拒绝 mysql/test 共用。测试用独立的 dev,booking-ui 上下文和 booking-ui-test H2 名称；配置守卫另有单元测试。JAR 默认启动不暴露演示入口。未来真实页面需要 A 的 CUSTOMER Session 与 C 的 owner 校验，不可把真实适配器直接接到当前匿名预览路由。

## 账户与会员 UI 演示

`dev-ui.sh` 同时启用 account-ui；普通 dev.sh/dev-staff.sh 不默认启用这组路由。入口 `/preview/account/login`、`/register`、`/membership`，均在 `/preview/account` 前缀下。

注册与登录的 POST 保留 CSRF。这里只验证邮箱/姓名、密码必填/输入上限/重复一致性，不判断账号或密码正确性；256 字符是演示传输边界，正式密码策略需 A 确认。密码不传给 Gateway、不进 Session/flash/log、不写数据库，错误返回时也清空；请使用任意临时演示文本。

注册只保存当前会话的一份姓名/邮箱预览，不创建账户，也不自动进入登录后页面。后续登录页从当前会话回填该邮箱；相同演示邮箱可在个人中心延续刚才的姓名。账户页面预览不设置 SecurityContext、角色或实际登录状态。个人中心入口在没有展示资料时转到演示登录；这个跳转不代表生产鉴权。

返回地址用后端白名单检查，仅允许账户/会员/已知预订预览页，不接收站外地址、admin 路径、编码或控制字符。会员状态切换只读；费用和折扣不自定，开通与续费无付款 POST。退出预览不失效整个 Session，保留 STAFF 和 Stay 草稿。

account-ui 要求 dev+回环，拒绝 mysql/test 共用；MVC 测试使用独立 account-ui-test H2 名称。正式接入必须由 A 的认证和本人资料服务提供身份，从后端取 CUSTOMER 主体，另建受认证保护的真实路由；不能让匿名预览路由接真实账号数据。本文不声称默认应用具有正式注册/登录能力。

## 工作人员预订 UI 演示

`dev-ui.sh` 同时启用 staff-booking-ui。入口 `/admin/stays` 与详情 `/admin/stays/{id}` 均要求ROLE_STAFF；没有匿名例外，账户UI的展示资料不授予权限。默认 dev.sh/dev-staff.sh 不安装此组页面；单独启用可用 `STAFF_BOOKING_UI_PREVIEW=true ./scripts/dev-staff.sh`。

只读6条独立虚构记录，支持编号部分匹配、精确入住日、状态组合筛选及保留条件的详情返回。不读取客户草稿、不写Room库存；管理操作按钮禁用，没有预订或支付POST。profile要求dev+回环，拒绝mysql/test；测试使用独立staff-booking-ui-test H2名称。真实员工权限、分页/状态/金额快照和业务写接口待A/C/D评审接入。操作见staff-stay-ui-demo.md。

## 检查

```sh
./scripts/test.sh
```

基础集成测试使用独立 `test` profile，检查模板渲染、本地资源、健康检查、默认拒绝策略、CSRF 和 H2 连接。Room 测试覆盖区间重叠、连续可用、停用排除、查询只读、数量和日期边界，以及绕过前端的非法 HTTP 输入；查询 Web 测试固定 Clock，使样例场景可重复。管理测试覆盖重复房号、占用保护、停用/重新启用、基础价精度、HTTP 权限与 CSRF；独立本地预览登录测试使用专门的 H2 名称与测试用账号。没有真实支付、邮件发送或 MySQL 并发验证。JUnit/Surefire 结果在 `target/surefire-reports/`；覆盖率和 CI 由 D 后续补齐。

## 第三方资源

Bootstrap 5.3.8 的 CSS/JS 已保存到 `static/vendor/bootstrap/`，同目录保留 MIT LICENSE。下载来源为 jsDelivr 的官方 bootstrap npm 包，许可证来自 twbs/bootstrap v5.3.8。当前页面没有第三方在线字体或 CDN 依赖。
