# D 通知与持久化实现

状态：D-INTEGRATED-2026-11-13，内部工程基线，不是最终课程验收或真实生产证据。

图示、状态机和联调顺序见 [D 通知与持久化设计](design/d-notification-persistence.md)。

本文记录 D 对 RS `FR-REM-001..003`、`FR-DB-001..004` 的实现，以及为让 A/B/C 现有代码在 H2/MySQL profile 下真实协同所做的最小集成改动。每个业务规则仍由原 owner 负责；D 提供持久化、事务、通知和验证边界。

## 1. 需求追踪

| 需求 | 实现 | 测试 |
| --- | --- | --- |
| FR-REM-001 | `ReminderService` 使用共享 Clock 的 Asia/Shanghai 时区；入住日前一天 09:00 计划发送 | `ReminderServiceTest`, `JdbcReminderRepositoryTest` |
| FR-REM-002 | 邮件含参考号、入住/退房日期、房型；无凭据；失败只保存安全错误码 | `ReminderServiceTest`, `SmtpEmailTransportTest` |
| FR-REM-003 | 取消、失败、过期会取消提醒；创建/支付成功可安排；相同内容幂等，内容变化重排 | `BookingReminderEventListener`, `MySqlProfileIntegrationTest`, `JdbcReminderRepositoryTest` |
| FR-DB-001 | H2 与 MySQL 8 profile；Room、Member、Membership、Staff、Booking、Payment、Reminder JDBC adapter | `MySqlProfileIntegrationTest`, `MySqlLiveIntegrationTest` |
| FR-DB-002 | 房号/邮箱/idempotency 唯一约束；booking/payment/membership/reminder 审计；释放历史写入 append-only 表 | `SchemaContractTest`, `MySqlProfileIntegrationTest` |
| FR-DB-003 | `ROOM_INVENTORY -> BOOKING -> PAYMENT` 固定锁序；Booking 分配与保存、Payment 回调与 Booking 状态同一事务 | `JdbcPersistenceLockServiceTest`, `JdbcBookingInventoryPort`, `PaymentService` |
| FR-DB-004 | 全新 H2 schema、JUnit、JaCoCo 90% 总行门槛、Windows/Linux 脚本、GitHub Actions 和 MySQL 8 service | `mvn verify`, CI workflow |

## 2. 提醒应用契约

```java
public interface NotificationService {
    ReminderSchedule scheduleOneDayReminder(ReminderRequest request);
    boolean cancelOneDayReminder(String bookingReference);
    DispatchResult dispatchDue();
}

public record ReminderRequest(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String roomTypeName) {}
```

- `BookingService` 在创建 booking 的事务内发布 `BookingLifecycleEvent`；D 的 `BookingReminderEventListener` 在同一事务中安排提醒。
- Booking 进入 `PAID` 时再次 upsert，保证支付成功后仍存在提醒；进入 `FAILED` 或 `EXPIRED` 时取消提醒。
- 相同内容幂等；日期、退房、房型或邮箱变化时重排同一 revision 并写安全审计。
- 状态：`PENDING -> SENDING -> SENT | PENDING(retry) | FAILED`，另有终态 `CANCELLED`。
- 发送认领使用 `status + version` 条件更新；两个实例最多一个进入 `SENDING`。
- 进程崩溃后停留在 `SENDING` 的行由 `recoverStaleSending` 按 lease 回收；达到最大尝试次数后进入 `FAILED`。
- 外发邮件在数据库事务之外；reminder 行是 outbox。失败默认最多 3 次、间隔 15 分钟。
- 日志和持久化只保存稳定错误类别，不保存 provider 原文、收件邮箱或游客资料。

## 3. 邮件端口与配置

默认 `MockEmailTransport` 只把消息保存在进程内，绝不发真实邮件。可选 profile：

```powershell
$env:SPRING_MAIL_HOST='smtp.example.test'
$env:SPRING_MAIL_PORT='587'
$env:SPRING_MAIL_USERNAME='...'
$env:SPRING_MAIL_PASSWORD='...'
$env:NOTIFICATION_FROM_ADDRESS='booking@example.test'
.\scripts\maven.ps1 spring-boot:run -Dspring-boot.run.profiles=dev,smtp-email
```

```yaml
notification:
  dispatch-enabled: false
  batch-size: 50
  max-attempts: 3
  retry-delay: 15m
  send-lease: 5m
  dispatch-interval-ms: 60000
  send-hour: 9
  from-address: no-reply@example.invalid
```

源码、YAML 和测试均不保存真实密码或收件人。`live-email` profile 故意没有可用 adapter，启动失败优于伪发送。

## 4. Booking 生命周期与事务

D 新增 `BookingInventoryPort`，把 C 的“复核、分配、保存”封装为一个原子边界：

```text
Transaction
  lock ROOM_INVENTORY and BOOKING
  re-read room type and physical availability
  call C factory to save Booking
  insert booking_room_allocation
  insert active booking_room_night rows
  publish booking-created lifecycle event
  insert reminder outbox row
Commit
```

释放路径：

```text
lock ROOM_INVENTORY and BOOKING
copy active nights to booking_room_night_history
delete active projection rows
set allocation released_at/release_reason
update Booking state
publish failed/expired lifecycle event
cancel reminder
Commit
```

因此：

- `booking_room_night` 是当前占用投影，PK `(room_id, stay_date)` 从数据库层阻止重叠；
- 释放前先写 append-only `booking_room_night_history`，房间可再次销售且历史仍可查；
- `JdbcRoomInventoryRepository` 继续只服务 B 的 staff room management，不再承担 C 的 booking allocation；
- B 的 `RoomInventoryEditor` 和 C 的 `BookingInventoryPort` 职责分离。

## 5. 版本化数据库 schema

| 版本 | 内容 |
| --- | --- |
| `V1` | `reminder_schedule` |
| `V2` | `room_type`, `room`, `room_block`, 房间与价格审计 |
| `V3` | `customer`, `staff_account`, `membership` |
| `V4` | `booking`, 分配/逐夜占用、`payment`, payment event |
| `V5` | 提醒日期、房型、计划发送时间和提醒审计 |
| `V6` | `persistence_lock` 固定锁序 |
| `V7` | 三个房型和八个房间种子 |
| `V8` | booking idempotency/discount、payment failure reason、夜间释放历史、booking/membership audit |

关键约束：

- `room.id` 主键；`customer.email_normalized` 唯一且必须小写；`booking.idempotency_key` 唯一。
- `payment.idempotency_key` 唯一；booking/membership purpose 约束保留。
- 金额全部为整数分；日期使用 LocalDate；时间戳使用 instant。
- 不修改已经应用的 V1–V7 checksum；后续 contract 变化走 V8+。
- `reminder_schedule.booking_reference` 暂为软引用，便于通知独立重试；待 C 的取消/改期 contract 完整后评估 FK。

## 6. JDBC adapter

D 提供：

| Adapter | Port | Profile |
| --- | --- | --- |
| `JdbcRoomInventoryRepository` | B `RoomInventoryEditor` | `mysql` |
| `JdbcBookingInventoryPort` | C `BookingInventoryPort` | `mysql` |
| `JdbcMemberRepository` | A `MemberRepository` | `mysql` |
| `JdbcMembershipRepository` | A `MembershipRepository` | `mysql` |
| `JdbcStaffDirectory` | A `StaffDirectory` | `mysql` |
| `JdbcBookingRepository` | C `BookingRepository` | `mysql` |
| `JdbcPaymentRepository` | C `PaymentRepository` | `mysql` |
| `JdbcReminderRepository` | D `ReminderRepository` | H2/MySQL |
| `JdbcPersistenceLockService` | D lock boundary | H2/MySQL |

`JdbcBookingRepository` 写 booking event；`JdbcMembershipRepository` 写 membership event；`JdbcPaymentRepository` 保存 provider/failure/idempotency 字段；`JdbcStaffProvisionerConfiguration` 只从 `STAFF_USERNAME`/`STAFF_PASSWORD` 环境变量预置 STAFF。

默认 `dev/test` 仍保留内存 adapter，便于快速运行。`mysql` profile 使用 JDBC adapter；本地无 MySQL 时可用 H2 `MODE=MySQL` 验证 wiring，真实 MySQL 由 CI service 验证。

## 7. 测试与 CI

Windows：

```powershell
.\scripts\test.ps1
```

Linux/macOS：

```sh
./scripts/test.sh
```

CI `.github/workflows/ci.yml` 有两个 job：

1. Temurin JDK 17 `clean verify`，上传 Surefire/JaCoCo；
2. MySQL 8 service，运行 `MySqlLiveIntegrationTest` 验证真实迁移和 seed catalogue。

## 8. 2026-11-13 实际验证

环境：Windows、Temurin JDK 17.0.20.1、Maven Wrapper 3.9.11、项目本地依赖缓存。

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\test.ps1
```

结果：`BUILD SUCCESS`；290 tests、0 failures、0 errors、1 skipped（真实 MySQL 环境门禁）；Flyway 在全新 H2 数据库成功应用 8 个迁移；JaCoCo 总行覆盖率 90.11%，分支覆盖率 74.63%，90% BUNDLE 门槛通过。MySQL-mode profile 集成测试验证 Customer/Membership/Staff/Booking/Payment JDBC adapter、幂等提交、重复回调和 Booking→Reminder 生命周期。

## 9. 明确未完成与联调条件

- 本机未运行真实 MySQL 8；`MySqlLiveIntegrationTest` 在无 `MYSQL_URL` 时明确 skip，CI service job 负责最终验证。
- 未向真实 SMTP provider 发信；Mock/SMTP adapter unit test 不等同于真实投递、退信或配额证据。
- C 当前没有完整的取消/改期 UI/service 方法；提醒 listener 已支持相应事件，待 C 补齐后用真实事件回归。
- 会员费支付路径仍未在 C 的 PaymentService 中实现；D 的 schema 和 adapter 已预留 `MEMBERSHIP` purpose，但不得在报告中写成已端到端完成。
- Q8 三天提醒仍是设计演进，不在当前 RS 实现范围。
- Q5/Q7 用户测试需要真实参与者和匿名化记录，不能由本报告替代。
