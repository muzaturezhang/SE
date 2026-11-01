# Member 与账户需求

Owner：A。2026-11-01 实现第一批真实注册/登录/资料/会员；接口与字段仍待 B/C/D 评审。

| ID | 需求 | 验收条件 |
| --- | --- | --- |
| FR-MEM-001 | 顾客注册 | 姓名 1–80 字、邮箱合法且最多 254 字、密码符合策略；邮箱去空白并统一小写；注册成功可登录，重复邮箱（忽略大小写）拒绝并保持原账户。 |
| FR-MEM-002 | 密码策略 | 8–72 字符，同时包含字母和数字，不能以空格开头或结尾；只保存 BCrypt 哈希，密码不进入日志、Session、flash 或页面回显。 |
| FR-MEM-003 | 会话登录与退出 | 正确凭证建立 Session 并授予 CUSTOMER；错误凭证显示统一提示；POST /logout 清除 Session；登录页面与注册页面无需身份即可访问。 |
| FR-MEM-004 | 工作人员身份 | STAFF 账号由团队预先提供，公开注册不能授予 STAFF；STAFF 可通过共享登录页登录，成功后进入 /admin/rooms。 |
| FR-MEM-005 | 本人资料 | CUSTOMER 只能查看自己的姓名、邮箱、注册日期与账户身份；主体 ID 来自服务端认证，不接受请求参数。 |
| FR-MEM-006 | 会员方案 | 公布会员费、住宿折扣比例与有效期；金额以 CNY 整数分存储与展示，页面金额以后端返回为准。 |
| FR-MEM-007 | 会员申请与确认 | 未开通可记录申请，申请保持待确认且幂等；支付服务确认后按确认日计算有效期；有效期内不能重复开通；过期后可续费。 |
| FR-MEM-008 | 折扣资格 | 仅有效期内会员可获住宿折扣，结果包含比例与可读标签；未开通、待确认、已过期均不可用；最终计价由 Booking/Payment 完成。 |
| FR-MEM-009 | 服务端授权与 CSRF | /account/** 要求 CUSTOMER，/admin/** 要求 STAFF；所有 POST 校验 CSRF；匿名访问账户页面跳转登录，其他受保护或未知路径拒绝访问。 |
| NFR-MEM-001 | 失败可读且不泄露 | 校验失败返回可读字段信息，不输出异常堆栈；密码在任何错误路径都不回显；重复邮箱 409，非法输入 400，记录缺失 404。 |

## 关键规则

- 登录标识为邮箱（大小写不敏感且唯一）；认证后的 principal 名是内部顾客 ID，供 Booking/Payment 校验归属。
- 会员默认基线：会员费 CNY 680.00、住宿 95 折、自支付确认日起 365 天；比例与费用由 A 提议，待团队确认后冻结。
- 会员费独立于住宿房费，不套用住宿折扣；折扣由 C 在整笔房费上一次性按“四舍五入到分”应用。
- 时间使用 Asia/Shanghai 与共享 Clock；注册日期与会员有效期使用 LocalDate。

## Traceability

| Requirements | Use case / 页面 | 覆盖的测试 |
| --- | --- | --- |
| FR-MEM-001 至 003 | 注册页、登录页 | RegistrationServiceTest、MemberAccountWebTest |
| FR-MEM-004 至 005、FR-MEM-009 | 登录页、个人中心、共享安全链 | MemberAccountWebTest、LocalStaffPreviewWebTest、FoundationIntegrationTest |
| FR-MEM-006 至 008 | 会员页、开通/续费页 | MembershipServiceTest、MemberAccountWebTest |
