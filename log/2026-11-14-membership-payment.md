# 2026-11-14：会员费支付

## 实现

- Payment purpose：`BOOKING` / `MEMBERSHIP`。
- Payment 增加 `customerId` 和 `purpose`；MEMBERSHIP 的 booking reference 为空。
- `PaymentService.initiateMembershipPayment(customerId)` 使用后端 MembershipPlan 金额。
- 会员费成功回调在同一事务内更新 Payment 并调用一次 `MembershipService.confirmPayment`。
- 重复会员支付/重复成功回调幂等。
- JDBC 与 in-memory payment adapter 均支持两种 purpose。

## 验证

- 本地完整套件：317 tests，0 failures，0 errors，5 个 live MySQL 环境 skip。
- JaCoCo line 90.69%，branch 74.47%。
- 新增 `MembershipPaymentServiceTest`。
- `MySqlProfileIntegrationTest` 覆盖 MEMBERSHIP purpose、无 booking reference、会员激活。
- `MySqlLiveIntegrationTest` 新增真实 MySQL 会员费用例。

## 后续

- 真实 UI 的会员开通/续费按钮仍需接 `initiateMembershipPayment` 和 callback/return 页面。
- 不声称真实资金结算；当前使用 sandbox provider。
