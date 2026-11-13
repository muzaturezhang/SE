# 2026-11-13：D 与 ABC 最新基线集成

## 变更

- 将 D foundation 合并到最新 `origin/main`（含 A member 与 C booking/payment）。
- 修复 `BookingStatus.isTerminal()` 编译缺口并添加状态回归测试。
- 增加 `BookingInventoryPort`、`InMemoryBookingInventoryPort` 和 `JdbcBookingInventoryPort`，把 booking allocation 从 B 的 staff room management 端口中拆出。
- 增加 Member、Membership、Staff、Booking、Payment 的 JDBC adapter，补齐 `mysql` profile 的 bean wiring。
- 增加 V8 迁移：booking idempotency/discount、payment failure reason、booking/payment/membership audit、active room-night release history。
- 增加 `BookingLifecycleEvent` 与 `BookingReminderEventListener`，把 booking create/paid/fail/expire 与 reminder schedule/cancel 接入同一事务。
- 增加 `PaymentService` 事务边界、重复回调保护、幂等提交和 clock-based paid timestamp。
- 增加 stale `SENDING` lease 恢复、send-lease 配置和相应 JDBC 测试。
- 增加 `MySqlProfileIntegrationTest`、`MySqlLiveIntegrationTest` 和 GitHub Actions MySQL 8 service job。

## 验证

Windows、Temurin JDK 17.0.20.1、Maven Wrapper 3.9.11：

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\maven.ps1 --batch-mode --no-transfer-progress verify
```

结果：BUILD SUCCESS；290 tests / 0 failures / 0 errors / 1 skipped（真实 MySQL 环境门禁）；Flyway 8 migrations；JaCoCo line 90.11%，branch 74.63%。

## 未验证

- 本机真实 MySQL 8；由 CI `mysql-integration` job 验证。
- 真实 SMTP provider、退信和 quota。
- GitHub Actions 远端执行。
- C 的取消/改期完整 UI/服务路径、会员费支付编排，以及 Q5/Q7 真实用户测试。


## CI 结果

- Push run 43881256201：Java 17 verification、MySQL 8 integration 均成功。
- PR run 43882193364：Java 17 verification、MySQL 8 integration 均成功。
- PR #2 已合并到 main，合并提交 aa1275d。
- GitHub 报告 3 warnings / 1 notice，无失败 check；后续使用证据前应展开 annotations 确认是否为非阻塞依赖告警。
