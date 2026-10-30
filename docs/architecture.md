# 工程边界

采用一个 Spring Boot Web 应用，Thymeleaf 渲染页面，Bootstrap 和原生 JavaScript 提供 UI。按业务模块分包，公共配置集中维护。

## 请求路径

```text
浏览器 → Web Controller → 模块 Service → 领域规则 / Repository 契约
                                              ↓
                                    persistence 中的适配实现
```

Room 已建立 StayPeriod、Room、RoomType、RoomBlock、RoomInventory 领域对象，RoomQueryService 查询服务和 RoomRepository 只读契约。开发适配器从 classpath 中的样例文件构建不可变内存快照，Controller 仅调用服务，Thymeleaf 渲染结果。D 的真实实现后续遵守评审后的契约，并通过 profile/依赖注入替换；页面不直接访问数据库。

第二批新增 RoomManagementService 和 RoomInventoryEditor 写端口。内存适配器用同步更新及 volatile 不可变快照，让校验和替换在同一原子边界执行，异常不提交修改。查询仍使用独立的只读 RoomRepository。该实现没有数据库事务或 Booking 预留能力。目录、查询、400 错误与无结果均由后端决定；浏览器不能通过篡改输入绕过校验。

## Ownership

| 路径 | Owner | 边界 |
| --- | --- | --- |
| member | A | Customer、Member、身份认证与会员规则 |
| room | B | Room、房型信息、可用性、房间管理 |
| booking | C | 预订生命周期、计价、库存与支付编排 |
| payment | C | 支付接口、sandbox、结果核对 |
| notification | D | Reminder 与 Email |
| persistence | D | 数据库迁移与 Repository 适配 |
| templates、static、web | B | 跨模块页面、导航、共享 UI |
| config、shared、pom.xml | A 协调 | 各 owner 确认共享改动 |

各模块将按需建立 `domain`、`application`、`web` 子包。包中的 package-info 是责任说明，不代表对应模块已经实现。

## 安全与时间

当前公开 `/`、`/rooms`、静态资源和健康检查；管理路径 `/admin/**` 要求 ROLE_STAFF，其他请求默认拒绝。默认 profile 不提供身份或登录；可选 local-staff 为仅回环 dev 环境提供临时 Session 登录，用于 B 独立验证。CSRF 保持开启。A 负责正式认证与身份接入。

应用提供 Asia/Shanghai 的 Clock Bean。业务日期使用 LocalDate；后续服务注入 Clock，避免依赖测试机器的默认时区。金额与共享状态尚无公共类，避免在各模块分别定义。

## 第三批：B 的预订流程 UI

`web.stay` 与 `templates/stay` 均归 B，未在 C 的 booking/payment 包添加业务实现。StayPreviewController → StayPageGateway → DemoStayPageGateway → RoomQueryService（只读）。页面 DTO 包含 Selection、Quote、StaySummary、Reservation 与 DisplayState；它们不是 C 的实体、共享状态或数据库 schema。

Demo 适配器只在 booking-ui 下注册且按 HttpSession 隔离。提交最多替换本会话一个 UI-DRAFT，Room 库存不写入，也没有 Booking/Payment/邮件调用。动态报价的 totalMinor 为空，不自行乘晚数、数量或折扣；支付示例使用预先写好的整数分金额和固定状态，不模拟实际状态流转。

真实接入必须另建 CUSTOMER 保护的入口，从认证身份取得 viewerId，并由 C 在服务层验证归属和事务。不能将真实数据源直接接入匿名 `/preview/stays/**`。B 的 Gateway 将 C/A 输出映射成页面投影，保留字段错误、缺报价、支付待核对、过期与空状态。草稿总价为空时禁用支付，真实支付创建/回调/核对尚未实现。

## 第四批：账户和会员页面

`web.account` / `templates/account` 归 B。AccountPreviewController 接收仅本次请求的密码表单；表单验证后立即 clearSecrets，Gateway 的签名只接触姓名/邮箱，不接触密码。DemoAccountPageGateway 使用 SessionScope，保存页面展示资料和注册结果；不实现 A 的账号存储、密码哈希、认证、角色或会员资格计算。

会员页面使用 MemberDisplayState 的 UI 投影和可空 CNY 整数分费用。NONE/ACTIVE/EXPIRED/PENDING 只是固定展示场景；没有真实开通或续费指令。A/C 日后映射本人资料、权益/有效期/费用、认证与会员支付结果，B 接真实 Controller 和页面。当前 AccountPageGateway.preview* 不能被误当作生产认证接口。

account-ui 的匿名页面仅适用于回环开发示例；默认拒绝，CSRF 与 no-store 开启。返回位置由 AccountReturnTarget 的路径/参数白名单检查。演示退出只清理本模块展示资料，不清除真实 SecurityContext 或其他模块草稿；后续正式退出仍由 A 定义。

## 第五批与设计交接材料

web.staffstay / templates/staffstay提供ROLE_STAFF保护的只读工作人员列表/详情，使用独立演示记录；不查询客户草稿、不访问C模块表或改变库存，实际业务写操作未接入。staff-booking-ui是独立dev回环profile，默认未安装此页面适配器。

2026-10-29按当前源码整理 [Room类图与依赖](design/room-model.md)、[CRC](design/room-crc.md)、[导航](design/navigation.md)、[可用性交互](design/availability-interaction.md) 与 [逐页UI原则](design/ui-principles.md)。设计草案和 [团队接入说明](../handoff/B-team-integration.md) 供模块owner评审，未冻结新服务、数据库表或共享状态。
