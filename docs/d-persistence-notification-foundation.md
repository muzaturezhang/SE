# D 通知与持久化实现

状态：D-COMPLETE-2026-11-07-01，内部开发基线，不是最终课程验收证据。

图示、状态机和联调顺序见 [D 通知与持久化设计](design/d-notification-persistence.md)。

本文记录 D 对 RS `FR-REM-001..003`、`FR-DB-001..004` 的实现。代码覆盖 D 自有端口、通知规则、数据库迁移、Room 持久化适配、统一锁序和测试基础设施。A/C 的认证、预订、支付业务服务仍由对应 owner 实现；D 已为它们提供 schema、锁和事务入口，不代写其业务规则。

## 1. 需求追踪

| 需求 | 实现 | 测试 |
| --- | --- | --- |
| FR-REM-001 | `ReminderService` 使用共享 `Clock` 的 Asia/Shanghai 时区；`scheduledAt = check-in - 1 day at notification.send-hour`（默认 09:00） | `ReminderServiceTest` |
| FR-REM-002 | `ReminderEmailComposer` 输出参考号、入住/退房日期、房型；无密码或凭据；失败只持久化/记录安全错误码 | `ReminderServiceTest`, `JdbcReminderRepositoryTest` |
| FR-REM-003 | 同一预订内容变化时原子重排并审计；`cancelOneDayReminder` 取消未来发送；取消行不再进入到期查询 | `ReminderServiceTest`, `JdbcReminderRepositoryTest` |
| FR-DB-001 | Flyway 同时支持 H2 与 MySQL 8 profile；Room 有 JDBC 持久化适配；其余业务表已版本化建立 | `SchemaContractTest`, `JdbcRoomInventoryRepositoryTest` |
| FR-DB-002 | 房号主键/唯一约束；`email_normalized` 大小写不敏感唯一键；状态和审计历史不物理删除 | `SchemaContractTest` |
| FR-DB-003 | `persistence_lock` 定义 room inventory → booking → payment 固定锁序；Room 写入在事务内加锁 | `JdbcPersistenceLockServiceTest`, `JdbcRoomInventoryRepositoryTest` |
| FR-DB-004 | 全新 H2 schema、JDK 17 `verify`、JaCoCo 90% 总行覆盖率门槛、Windows/Linux 脚本、GitHub Actions | Maven `verify` |

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

- 调用方 C 在 booking 创建成功后调用 `scheduleOneDayReminder`；取消时调用 `cancelOneDayReminder`；改期、改房型或改联系邮箱后再次调用 `scheduleOneDayReminder`，相同预订只有一条当前 revision。
- 相同内容和未取消状态幂等返回原记录；内容变化会重置为 `PENDING`、清零尝试次数并写 `RESCHEDULED` 审计。
- 状态：`PENDING → SENDING → SENT | PENDING(retry) | FAILED`，另有终态 `CANCELLED`。这些是通知内部状态，不是共享 Booking/Payment 状态机。
- 发送认领使用 `status + version` 条件更新。两个实例同时处理时最多一个进入 `SENDING`。
- 外发邮件发生在数据库事务之外；行本身作为 outbox 记录，先提交后发送。
- 失败默认最多 3 次、间隔 15 分钟。持久化和日志只使用如 `SMTP_SEND_FAILED`、`EmailDeliveryException` 的错误类别，不写入 provider 原文、收件邮箱或游客资料。
- 已发送后发生取消不会撤回邮件，只更新当前通知状态并保留审计；SENDING 与取消/改期竞争时采用至少一次投递语义，旧发送的 `markSent` 会因版本变化失败，新 revision 可再次发送。

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

源码、YAML 和测试均不保存真实用户名、密码或收件人。`live-email` profile 仍故意没有可用适配器，不能把未配置状态伪造成发送成功。

```yaml
notification:
  dispatch-enabled: false
  batch-size: 50
  max-attempts: 3
  retry-delay: 15m
  dispatch-interval-ms: 60000
  send-hour: 9
  from-address: no-reply@example.invalid
```

定时派发默认关闭。启用 `notification.dispatch-enabled=true` 后才会周期性执行 `dispatchDue`。

## 4. 版本化数据库 schema

Flyway 迁移：

| 版本 | 内容 |
| --- | --- |
| `V1` | `reminder_schedule` 初始提醒表和唯一键 |
| `V2` | `room_type`, `room`, `room_block`, 房间与价格审计 |
| `V3` | `customer`, `staff_account`, `membership` |
| `V4` | `booking`, 房间分配/逐夜占用、`payment`, 支付事件 |
| `V5` | 提醒的退房日期、房型、计划发送时间和提醒事件审计 |
| `V6` | `persistence_lock` 固定锁序 |
| `V7` | 初始三个房型和八个房间目录 |

关键约束：

- `room.id` 为主键，天然保证房号唯一。
- `customer.email_normalized` 唯一，并由 `CHECK` 要求小写；A 必须用 trim + lowercase 生成该值。
- `booking_room_night(room_id, stay_date)` 主键从数据库层阻止同一房间同夜重复分配，不依赖 MySQL 独有 exclusion constraint。
- `payment.idempotency_key` 唯一；`BOOKING` 支付必须有 booking，`MEMBERSHIP` 支付必须独立于 booking。
- 金额全部为整数分并有正数/范围检查；日期使用 `LocalDate`；时间戳使用 UTC Instant。
- 房型价格、房间状态、提醒、支付均保留事件审计；应用不提供物理删除。
- `reminder_schedule` 保留既有表名以保护已发布的 `V1` checksum，语义对应设计中的 `reminder` 表。

## 5. Room JDBC 适配

`JdbcRoomInventoryRepository` 实现 B 的 `RoomInventoryEditor`：

- `readSnapshot` 在 repeatable-read 事务中读取房型、房间、手工 block 与未释放的 booking allocation。
- `updateSnapshot` 先锁定 `ROOM_INVENTORY`，再读取当前快照、执行 B 的校验函数并写回。
- 房间首次创建、房型/状态变化和价格变化写审计表；不删除历史行。
- 并发唯一键冲突转换为 `RoomInventoryConflictException`，由 B 的服务映射为可读 409。
- Booking 占用只能由 C 通过 allocation 表和统一锁释放，不能借 B 的 Room 更新端口改写。

MySQL/H2 共用的锁入口：

```java
locks.lockRoomInventory(); // room inventory
locks.lockBooking();       // room inventory -> booking
locks.lockPayment();       // room inventory -> booking -> payment
```

调用者必须已经开启数据库事务。任何跨模块事务都不得反向获取锁。

## 6. 测试与 CI

Windows：

```powershell
.\scripts\test.ps1
```

Linux/macOS：

```sh
./scripts/test.sh
```

CI 位于 `.github/workflows/ci.yml`，使用 Temurin JDK 17，执行 `clean verify` 并保留 Surefire 与 JaCoCo 报告。JaCoCo 当前总行覆盖率门槛为 90%，BUNDLE 报告用于课程证据时必须附实际运行环境。

## 7. 2026-11-07 实际验证

环境：Windows、Temurin JDK 17.0.20.1、Maven Wrapper 3.9.11、项目本地依赖缓存。

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\maven.ps1 --batch-mode --no-transfer-progress verify
```

结果：`BUILD SUCCESS`；245 tests，0 failures，0 errors，0 skipped；Flyway 在全新 H2 数据库成功应用 7 个迁移；JaCoCo 总行覆盖率 91.59%，90% BUNDLE 门槛通过。另以默认 `dev` profile 启动打包 JAR 到 127.0.0.1:18080，`/actuator/health` 返回 `UP` 后正常停止进程。D 相关当前测试为提醒服务 6、SMTP 适配器 2、提醒 JDBC 4、schema 契约 4、Room JDBC 3、锁 3、clean reset 1；全项目测试总数由原 230 项扩展/替换为 245 项。

## 8. 明确未完成与联调条件

- 未运行真实 MySQL 8 实例；SQL 已按 H2/MySQL 兼容语法编写并由 H2 验证，但最终仍需 MySQL 8 集成证据。
- 未向真实 SMTP 服务发信；SMTP 适配器存在，Mock 是默认且唯一未经外部验证的默认路径。
- A 的 Customer/Member repository 端口和 C 的 Booking/Payment repository 端口尚未评审。D 不越权定义其领域对象或状态机，因此对应 JDBC adapter 待这些端口冻结后实现；表与约束已就绪。
- C 必须在创建、取消、改期事务成功后接入 `NotificationService`；不能把定时派发当作 booking 业务触发器。
- Q8 的三天前提醒属于设计演进，不在本轮。
- 进程崩溃后长期停留在 `SENDING` 的租约恢复、provider 幂等和退信处理仍需后续实现。
- CI 文件已建立但尚未在 GitHub 远端实际运行；本机结果不能替代远端 CI 证据。