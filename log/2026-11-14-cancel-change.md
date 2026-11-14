# 2026-11-14：Booking 取消与改期

## 实现

- `BookingStatus.CANCELLED` 与终态判断。
- `BookingService.cancel(customerId, reference)`：归属校验、库存释放、Reminder 取消。
- `BookingService.change(customerId, reference, roomTypeId, arrival, departure, roomCount)`：
  - PENDING 重新报价并刷新 hold；
  - PAID 只允许总价不变；
  - 原子释放旧占用并重新分配；
  - Reminder 自动重排。
- `BookingInventoryPort.change` 与 in-memory/JDBC 实现。
- JDBC 释放历史保留；同一 Booking 改期可复用旧 allocation row，避免主键冲突。
- `BookingLifecycleEvent.CHANGED` 已接入 reminder listener。

## 验证

- 全量本地测试：313 tests，0 failures，0 errors，4 local MySQL-gated skips。
- JaCoCo line 90.89%，branch 75.15%。
- MySQL-mode 集成测试覆盖取消/改期后库存和 Reminder 更新。
- 真实 MySQL live test 新增取消/改期用例，待 CI 验证。

## 规则限制

- PAID 价格变化不做静默二次扣款，要求取消后重新预订。
- 退款是外部流程；当前只记录 `CANCELLED` 和审计，不声称真实退款完成。
- 正式 UI 仍需接入 controller/form 和页面反馈。
