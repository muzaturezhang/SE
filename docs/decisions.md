# 团队确认的启动基线

2026-10-27，用户确认队友已同意《团队启动与接口协作建议稿》中的方案。本文将其作为工程启动依据；接口签名、DTO、状态和数据库 schema 仍需对应成员评审。

| 范围 | 启动决定 |
| --- | --- |
| 应用 | Web；一个 Spring Boot 工程和共享仓库 |
| 技术 | Java 17 编译目标、Spring Boot 3.x、Maven、Thymeleaf、Bootstrap 5、原生 JS、MySQL 8 |
| 预订单位 | 管理具体房间，按房型和数量预订；初版单笔一个房型 |
| 可用性 | 按整个入住区间连续可分配的房间计算；搜索不占库存 |
| 占用 | 创建待支付 booking 时再次检查并占用；默认 15 分钟 |
| 支付与释放 | 支付不确定时先核对；确认未成功且不会继续扣款后再释放 |
| 并发 | C 编排预订；B 提供库存接口；D 支持同一事务与统一锁策略 |
| 价格 | B 提供房型每晚价；A 提供会员规则；C 计算最终房费 |
| 金额 | CNY 整数分；整笔折扣后四舍五入到分；保存计价快照 |
| 会员费 | 单独计算，不应用住宿会员折扣 |
| 日期 | Asia/Shanghai；LocalDate；[入住日期, 退房日期)；至少一晚 |
| 停用 | 保留历史；存在当前或未来有效预订、待支付占用时拒绝停用 |
| 权限 | Session；CUSTOMER / STAFF；会员为 Customer 的业务状态 |
| UI | B 统一页面，其他成员提供业务服务；先 Mock 再接入 |
| Q8 | 取消、改期、3 天和 1 天提醒先做设计演进，另行确认实现范围 |

## 仅属骨架的实现选择

- Spring Boot 具体补丁固定为 3.5.16，Maven 为 3.9.11，Bootstrap 为 5.3.8。
- dev/test 使用相互独立的 H2 内存库，MySQL 使用独立 profile。
- 本机当前以 JDK 23 执行构建，字节码目标为 17；尚需在真实 JDK 17 上复核。
- B 已实现 Room 只读查询领域对象和 Service/Repository 契约用于独立开发；这些字段与签名为模块草案，待 C/D 评审。仍未冻结数据库表结构、共享 Booking/Payment 状态、库存占用或支付接口。

跨模块变更需说明旧值、新值、原因、受影响成员及需要更新的测试和文档。

## 2026-10-28：B 第二批模块实现选择

用户已授权房间管理：新增、编辑、停用/重新启用和房型基础价维护。房号暂作为不可改的唯一标识，新增格式为 1–20 位大写字母/数字/短横线，首位为字母或数字；这是 B 的实现草案，待 D 评审映射与约束，不自行冻结 schema。编辑只能选择现有房型，不在这一批新建或删除房型。

有任一 RoomBlock 的退房日晚于共享 Clock 当天，就拒绝停用或更换房型；退房日等于当天的占用按已结束处理。历史记录保留；不提供物理删除。RoomBlock 输入必须由未来 C/D 适配层过滤成有效占用，当前没有 Booking/Payment 状态判断。

每晚基础价在房型上维护，金额精确转成正整数分，不静默舍入。未来订单成交快照仍由 C 持有。STAFF 与 CSRF 在 HTTP 后端强制执行；仅可选、回环 dev profile 提供临时身份，正式认证归 A。新增写端口、事务边界和错误类型登记在 interfaces.md，仍待评审。

## 2026-10-28：第三批 UI 独立开发

用户确认继续预订确认、支付、我的预订与详情 UI。为避免与 C/A 冲突，页面和投影适配契约放在 B 的 web.stay 包，C 的 booking/payment 包保持原状。动态草稿无最终报价；固定状态和金额仅供 UI 展示。没有实现计价、会员折扣、创建 Booking、库存占用、扣款、回调或超时释放。

演示单独使用 `/preview/stays/**`，只在 dev + 回环的 booking-ui profile 启用。匿名访问仅适用于这些虚构示例和本会话草稿；不作为正式用户权限方案。未来 A/C 接入时必须挂载受 CUSTOMER 保护的真实路由，viewerId 由后端认证取得并校验记录归属。此页面契约仍需 A/C 评审，不冻结共享 DTO 或状态。

## 2026-10-28：第四批账户 / 会员 UI

用户授权登录、注册、个人中心、会员权益/状态和开通入口的 UI 独立开发。真实认证、账号与会员规则归 A，会员付款接 C。新代码位于 B 的 web.account，不修改 member/booking/payment 领域实现。

账户预览不授予身份或权限；密码只用于请求内的必填、256字符传输上限和重复一致校验，未确定生产复杂度策略，不存储或传给 Mock。注册结果和登录后个人资料最多各一份，按当前会话隔离。会员费/优惠为待服务返回，示例日期只用于布局展示，不表示有效期规则。

新增 account-ui 可选 profile、只回环 dev 使用。dev-ui.sh 显式启用账户/预订/STAFF 三种预览；默认环境仍无真实认证、无演示入口。返回位置白名单、CSRF、no-store、密码不回显和退出不影响其他模块均已作为开发验收条件。

## 2026-10-29：第五批工作人员预订 UI

用户“继续吧”授权只读工作人员预订列表/详情和统一后台导航。B在web.staffstay定义页面投影与独立虚构fixture，C业务包不变。编号/入住日/状态组合筛选、详情返回保留条件；实际管理写操作、收款/核对、库存释放和Q8范围仍归owner确认与实现。

staff-booking-ui独立可选，dev-ui.sh显式启用；默认与dev-staff.sh默认不安装它。所有/admin/stays请求继续强制STAFF权限，未增加匿名例外。页面固定金额不是计价算法，展示状态不是团队公共状态机；真实列表分页/状态/授权等契约待A/C评审。

## 2026-10-29：D 通知与持久化基础

用户确认负责 D 的工作后，先落地不依赖未冻结跨模块 schema 的最小基础：Flyway、`reminder_schedule`、JDBC 提醒适配器、一天前提醒服务、邮件端口、默认 Mock adapter、可选调度器和 JaCoCo 报告。没有创建 Booking/Payment/Room/Member 表，没有接入真实邮件供应商，也没有声称完成 Q8 CR3。

`bookingReference` 暂按最长 64 字符的不透明值保存，不建立外键。原因是 C 的 Booking 标识和生命周期尚未评审；等 C/D 确认后再决定数据库键、事件和取消/改期重排。相同 `(bookingReference, reminder kind)` 重复请求返回已有记录，内容不一致时显式冲突。

邮件调用放在数据库事务之外。发送认领用 `status + version` 条件更新，失败可延迟重试，达到配置上限后进入 `FAILED`。定时派发默认关闭，避免本地开发误发；启用时仍使用 `MockEmailTransport`，除非 live adapter 经团队评审后另行接入。

验证：Windows + Temurin JDK 17.0.20.1 + Maven 3.9.11，`scripts/maven.ps1 --batch-mode --no-transfer-progress verify` 为 BUILD SUCCESS，230 tests / 0 failures / 0 errors / 0 skipped，JaCoCo 总行覆盖率 95.05%，70% 门槛通过。尚未验证 MySQL 8、live email、Booking → Reminder 联调或崩溃后的 SENDING 租约恢复。

## 2026-11-07：D 通知与持久化完整实现

- 提醒发送时间固定为上海时区入住日前一天 09:00，可通过 `notification.send-hour` 调整；到期判断使用 UTC `Instant`，业务日期仍用上海 `LocalDate`。
- `NotificationService.scheduleOneDayReminder` 采用当前 revision 的幂等 upsert。相同内容不重复写入；改期、退房日期、房型或邮箱变化时自动重排并写不含 PII 的提醒审计。
- 取消以 `CANCELLED` 终态保留历史，不物理删除。发送认领和终态更新使用 `status + version` 条件更新；外发在事务之外。
- 默认 Mock 邮件；新增可选 `smtp-email` SMTP adapter，所有主机和凭据只来自环境变量。`live-email` 仍无适配器，启动失败优于伪发送。
- Flyway 新增 V2–V7：Room、Member、Booking、Payment、提醒审计、统一锁表和初始房型目录。`reminder_schedule` 保留名称以保护已发布 V1 checksum。
- `booking_room_night(room_id, stay_date)` 主键在 DB 层保证同一房间不能重复占用同一夜，避免依赖 MySQL 专有 exclusion constraint。
- 锁表使用 `ROOM_INVENTORY → BOOKING → PAYMENT` 固定顺序；B 的 JDBC Room adapter 已接入，C/A 的事务必须复用该入口。
- A/C repository 端口未冻结前不代写其领域服务或 adapter。D 只提供 schema、锁、通知与 Room 持久化实现。

## 2026-11-01：A 成员与认证实现选择

- 一个共享登录页 /account/login 同时服务 CUSTOMER 与 STAFF，登录成功后按角色跳转（STAFF → /admin/rooms，CUSTOMER → 保存请求或 /account）；失败统一回登录页。B 的 local-staff 预览由独立前置安全链保持 /staff-preview/login 原行为。
- 统一 PasswordEncoder 为 BCrypt；存储值不带 {id} 前缀；删除原 InMemoryUserDetailsManager，全部身份经 ResortUserDetailsService（先 STAFF 目录、后按邮箱查顾客）。
- 密码策略：8–72 字符、含字母和数字、不以空格开头或结尾；256 字符预览上限不代表生产策略，生产策略以本基线为准。
- 邮箱作为登录标识，去空白、统一小写、忽略大小写唯一；principal 名用内部顾客 ID，避免 UI 传入身份。
- 会员基线：会员费 CNY 680.00（68000 分）、住宿 95 折、自支付确认日 365 天；由 A 提议，待全组确认后冻结。会员费独立计价，不套住宿折扣；折扣最终由 C 应用并保留价格快照。
- STAFF 不提供公开注册；开发环境经 env（LOCAL_STAFF_*）预置，生产预置方式待团队确认。
- 匿名访问除 /account/** 外的受保护或未知路径保持 403，不跳到登录页，以维持既有基础测试语义；/account/** 匿名访问跳登录页。
- member 内存适配与 Room 相同仅在 `!mysql & (dev | test)` 提供；D 的 MySQL 适配、唯一约束与历史保留到位后替换。

## 2026-11-13：D 集成决策

- Booking allocation 使用独立 `BookingInventoryPort`，不复用 B 的 `RoomInventoryEditor`。原因是 B/JDBC adapter 明确拒绝修改 booking-owned blocks，复用会把两种职责和事务边界混在一起。
- `booking_room_night` 作为 active occupancy 投影；释放时先写 `booking_room_night_history`，再删除 active 行。这样既保持数据库级重叠约束，又保留释放证据并允许房间再次销售。
- `BookingService` 创建 booking 时发布生命周期事件；D 的 listener 在同一个事务内写入 reminder outbox，避免 commit 后崩溃导致提醒丢失。
- V1–V7 已作为迁移 checksum 基线保留；新增字段和审计表放入 V8，不重写旧迁移。
- `mysql` profile 使用 JDBC adapter；`dev`/`test` 保留内存 adapter。SandboxPaymentProvider 只用于非生产/CI，真实 provider 仍需后续接入。
- JaCoCo 90% 总行门槛保留；测试报告同时列出实际分支覆盖率和 environment-gated skip，避免只看总行数字。

## 2026-11-14：取消/改期规则冻结

- `CANCELLED` 加入 Booking 终态；`PAID` 在入住前可以取消，退款只作为外部流程记录，不伪造已退款。
- `PENDING` 允许改期并重新报价；`PAID` 只允许总价不变的安全改期。价格变化必须取消后重新预订，避免二次扣款。
- 改期必须经过 `BookingInventoryPort.change`，在同一事务释放旧库存、重新检查并分配新库存。
- 改期/取消必须校验 authenticated customer 的 Booking 归属。
- 改期成功后 Reminder 必须按新入住日重排；取消后 Reminder 必须取消。
- 生产 UI 仍需接入这两个 service 方法；当前先冻结 service 级规则和持久化测试。
