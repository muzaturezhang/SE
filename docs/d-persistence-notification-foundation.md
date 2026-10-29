# D 通知与持久化基础

状态：D-FOUNDATION-2026-10-29-01，内部开发基线，不是最终课程证据，也不是全系统数据库方案。

本文记录 D 负责的通知提醒和持久化基础。当前只落地一天前邮件提醒、提醒调度表、JDBC 适配器、Mock 邮件端口和 JaCoCo 报告入口。Booking、Room、Payment、Member 的表结构、共享状态和事务边界仍未冻结，因此本批不创建跨模块业务表，也不声称已完成 Booking → Reminder 联调。

## 已实现的边界

| 能力 | 位置 | 当前行为 |
| --- | --- | --- |
| Schema 迁移 | `db/migration/V1__create_reminder_schedule.sql` | Flyway 在 dev、test 和 mysql 配置加载时执行 |
| 提醒仓储端口 | `notification.application.ReminderRepository` | 幂等插入、到期查询、发送认领和状态更新 |
| JDBC 适配器 | `notification.infrastructure.JdbcReminderRepository` | H2/MySQL 兼容 SQL；用版本号防止重复发送 |
| 提醒服务 | `notification.application.ReminderService` | 创建一天前提醒并按批次派发 |
| 邮件端口 | `notification.email.EmailTransport` | 业务不依赖供应商 SDK |
| 本地邮件适配器 | `MockEmailTransport` | 只记录内存消息，不发送真实邮件 |
| 调度入口 | `ReminderDispatchScheduler` | 只有 `notification.dispatch-enabled=true` 才运行 |
| 覆盖报告 | `jacoco-maven-plugin` | `verify` 后生成 `target/site/jacoco/index.html` |

## 对外草案契约

```java
record ReminderRequest(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate) {}

ReminderSchedule scheduleOneDayReminder(ReminderRequest request);
DispatchResult dispatchDue();
```

`ReminderSchedule` 是 D 内部的持久化读模型，不是共享 Booking 状态。C 不需要依赖其中的调度状态；C 只需在预订创建成功后调用提醒服务，并在取消或改期事件明确后再增加对应命令。

当前 `bookingReference` 是不透明字符串，最长 64 个字符。因为 Booking 表与主键尚未评审，`reminder_schedule` 不建立数据库外键，也不假定字符串就是最终 Booking 主键。联调时必须由 C/D 确认 Booking 标识、事件和幂等键。

## 调度规则

- 当前只实现 `ONE_DAY`，即 `remindOnDate = checkInDate - 1 day`。Q8 的三天前提醒仍属于设计演进范围，没有实现。
- 入住日期必须晚于共享 `Clock` 当天的上海日期；入住日为明天时，提醒当天到期。
- 数据库时间戳以 UTC `Instant` 写入；入住日期仍使用 `LocalDate` 和 Asia/Shanghai 业务日历。
- `(booking_reference, reminder_kind)` 唯一，重复创建返回已有记录。如果同一预订和提醒类型对应的邮箱或入住日期不同，服务抛出 `CONFLICT`，不静默覆盖。
- `PENDING → SENDING → SENT|PENDING|FAILED`。发送失败时，未达到最大尝试次数则回到 `PENDING` 并设置 `nextAttemptAt`；达到上限则进入 `FAILED`。
- 发送认领使用 `status + version` 条件更新，两个实例同时处理时最多一个能认领。
- 邮件调用位于数据库事务之外，避免第三方延迟长期占用数据库锁。

## 配置

```yaml
notification:
  dispatch-enabled: false
  batch-size: 50
  max-attempts: 3
  retry-delay: 15m
  dispatch-interval-ms: 60000
```

默认关闭定时派发，避免本地开发、测试或课堂演示误发邮件。启用后仍使用 `MockEmailTransport`，除非团队另行实现并评审 live provider。`live-email` profile 当前故意没有可用 adapter，不能把它当作已接入阿里云 DirectMail。

## 明确未完成

- 没有实现真实邮件供应商、凭据管理、退信处理或生产配额控制。
- 没有实现 Q8 CR3 的三天前和一天前双提醒。
- 没有创建 Booking、Payment、Room、Member 或跨模块事务表。
- 没有实现取消、改期或 Booking 状态变化触发提醒重调度；需要 C/D 先确认事件和幂等语义。
- 没有完成真实用户测试、MySQL 实例验证、同一版本系统截图或最终 Q5/Q7 证据。
- 新增测试必须实际执行后才能写入 Test Report；本文件不把待执行测试当作已通过结果。

## 联调待确认

1. C 提供的 Booking 稳定标识、创建成功事件、取消事件和改期事件。
2. 收件邮箱从 Customer 还是 Booking 快照取得；邮箱变更是否影响已排程提醒。
3. 提醒发送日是否固定为某个本地时刻，还是当天任意时间由调度器处理。
4. 真实邮件供应商、发件地址、配额、重试上限和审计要求。
5. Q8 三天前提醒的触发规则、去重键和取消/改期重排规则。

## 2026-10-29 实际验证

环境：Windows、Temurin JDK 17.0.20.1、Maven 3.9.11，项目本地 Maven 缓存。

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\maven.ps1 --batch-mode --no-transfer-progress verify
```

结果：`BUILD SUCCESS`，230 项测试，0 failures、0 errors、0 skipped；Flyway 在 H2 测试库成功应用 `V1`。JaCoCo 总行覆盖率 95.05%，通知包行覆盖率 86.72%，`BUNDLE` 行覆盖率门槛 70% 通过。报告位于 `target/site/jacoco/index.html`。

本机工程路径包含非 ASCII 字符，JaCoCo 执行文件改写到系统临时目录，再由报告阶段读取；报告和测试输出仍在 `target/`。这是本机路径兼容处理，不改变测试覆盖口径。

尚未验证：MySQL 8 实例、真实邮件 provider、跨模块 Booking → Reminder 调用、进程崩溃后的 `SENDING` 租约恢复、provider 幂等。当前按至少一次投递设计，邮件发送成功后若本地状态更新失败，可能重复发送。

## 测试覆盖

- `ReminderServiceTest`：一天前日期计算、输入校验、幂等冲突、成功派发、失败重试和终止状态。
- `JdbcReminderRepositoryTest`：Flyway 建表、唯一键幂等、到期查询、原子认领、发送完成、失败重试和终态。
