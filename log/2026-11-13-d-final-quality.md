# 2026-11-13：D 最终质量补强

## 新增测试

- `BookingReminderEventListenerTest`：创建/支付/失败提醒动作、同日入住跳过、非日期异常传播。
- `ReminderDispatchLeaseTest`：dispatch 前执行 stale SENDING 回收。
- `JdbcStaffProvisionerConfigurationTest`：空配置、部分配置失败、有效预置和重复预置幂等。
- `PaymentServiceTest` 扩展：重复成功回调、非 under-review 支付禁止人工确认/拒绝。
- `MySqlProfileIntegrationTest` 扩展：释放全部占用后重新销售，并验证 release history。

## 工作流维护

- 升级为 `actions/checkout@v7`、`actions/setup-java@v6`、`actions/upload-artifact@v7`。
- 使用显式 `actions/cache@v6` 缓存项目 `.cache/maven/repository`，移除 setup-java 的无效 Maven cache 路径告警。\r\n- 将 runner 固定为 `ubuntu-24.04`，避免 `ubuntu-latest` 自动迁移影响可复现性。
- CI 的已知 warning 已定位为旧 Node runtime、setup-java 弃用和 Maven cache 路径缺失，不是代码失败。

## 用户测试准备

- 新增 `docs/test/user-test-plan.md`。
- 新增 `docs/test/user-test-consent-and-privacy.md`。
- 新增 `docs/test/user-test-observation-sheet.csv`。
- 新增 `docs/test/d-failure-injection-matrix.md`。

## 验证

- `clean verify`：BUILD SUCCESS。
- 313 tests、0 failures、0 errors、4 environment-gated skips。
- JaCoCo line 90.89%，branch 75.15%。

## 未伪造内容

本轮没有创建参与者、截图、quote、任务完成时间或用户测试结果。Q5/Q7 只准备执行协议，真实 evidence 必须由实际 session 产生。


## 真实 MySQL 生命周期 CI

- Push run 43892676670：Java 17 verification、MySQL 8 integration 均成功。
- PR run 43892690685：Java 17 verification、MySQL 8 integration 均成功。
- MySQL job 在真实 MySQL 8 上执行 booking/payment/reminder/release 生命周期，0 annotations。


## 真实 MySQL 并发缺陷修复

- 两线程各订 3 间、房型总可用 4 间时，初版测试暴露出 DuplicateKeyException。
- 原因是事务在等待锁前已读取会员信息并建立旧快照，拿到锁后仍按旧可用性分配。
- 将 Booking/Payment 跨模块写事务改为 READ_COMMITTED，锁后复核能看到最新已提交占用。
- 并发测试改为验证恰好一笔成功、一笔冲突，且只留下成功预订的 room-night 行。


## 最终 CI

- Push run 43892676670：Java 17 verification、MySQL 8 integration 均成功。
- PR run 43892690685：Java 17 verification、MySQL 8 integration 均成功。
- 真实 MySQL 并发测试通过：恰好一笔 booking 成功、一笔冲突，只有赢家的 room-night 行落库。
- 新 workflow annotations 为 0。
