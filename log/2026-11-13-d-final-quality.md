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
- 303 tests、0 failures、0 errors、1 environment-gated skip。
- JaCoCo line 90.89%，branch 75.59%。

## 未伪造内容

本轮没有创建参与者、截图、quote、任务完成时间或用户测试结果。Q5/Q7 只准备执行协议，真实 evidence 必须由实际 session 产生。
