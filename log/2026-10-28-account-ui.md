# 2026-10-28：第四批账户 / 会员 UI

## 授权与范围

用户在登录/注册、个人中心、会员权益/状态/开通入口UI建议后回复“好的，你开始吧”。继续B独立页面工作，真实账号/认证/会员规则归A，会员与住宿支付归C，持久化归D。读取原分工文档作为职责参考，未据其中流程说明扩展为额外业务实现、对外沟通或提交。

## 实现

- 新增web.account页面投影Gateway、Profile/Membership/MemberDisplayState、请求表单、Controller、异常与安全返回白名单；实现登录/注册/结果/个人/会员/开通续费/错误页面，沿用原视觉和本地图像。
- Demo按Session隔离，最多一份资料展示与一份注册结果；真实登录轮换Session ID后仍能访问本会话数据，旧ID不可读。退出只清理本模块，不退出真实STAFF或清除Stay草稿。
- 姓名/邮箱校验、演示密码必填/256字符传输边界/两次一致、错误清空密码、不回显；Gateway不接密码，不创建或保存账号、不哈希/验证密码、不写SecurityContext/角色。注册姓名邮箱可以衔接后续演示登录和个人中心。
- 返回位置只允许已登记站内路径/参数，拒绝站外、协议相对、编码、控制字符、admin和重复未知参数；后端POST复核，保留原住期/数量或开通/续费返回位置。
- 会员未开通/有效/过期/待确认均固定只读展示；日期仅为例子，费用为空待报价、折扣/实际有效期待A，不做资格计算。开通/续费只展示，付款禁用且无付款POST。
- account-ui只dev+回环，拒绝mysql/test共用；默认不注册页面/端口，不开放路径。dev-ui.sh增加显式开关同时启用账户/预订/STAFF预览；不新增真实身份。
- 新增UI-ACCOUNT-001..008需求、测试映射、操作说明，更新接口/架构/决定/视觉/开发文档。按原分工的B长期职责、Stage2和Q3/Q4整理b-handoff-checklist.md，未伪造最终报告、签名、用户测试或集成证据。

## 实际验证

macOS arm64、Oracle JDK23、Maven3.9.11，编译目标17。javap检查AccountPreviewController major version61；未在真实JDK17执行。

最终命令：

```sh
./scripts/maven.sh --batch-mode --no-transfer-progress -DfailIfNoTests=true verify
```

2026-10-28 20:39:20 +08:00 BUILD SUCCESS。191 tests、0 failures/errors/skipped，XML汇总一致。此前126项保留，新增65：返回地址24、Demo Gateway8、Account Web27、配置守卫5、默认隔离1。首次返回续费测试误用String重新编码已有Location，改为URI请求以模拟浏览器；未放宽站内白名单。后续资料衔接与续费返回标签改动均完成全套回归。

sh -n两个预览脚本、源码空白/冲突标记检查通过；本地STAFF随机密码文件mode600且被Git忽略，未在工具输出或工作日志中暴露密码值。无并发编译。

浏览器实际演示：姓名“演示访客”、example.invalid邮箱、临时演示文本；密码不一致收到400，密码框清空；注册结果→登录后资料正确；会员四种状态、续费禁用付款、退出后续费经登录回原位置均走通。最终构建重启后复核续费返回文案和手机注册错误。1440/390/320 CSS像素下无页面横向溢出，viewport已reset，新标签保留干净登录页。

截图docs/previews/account-login-desktop.jpg、account-profile-desktop.jpg、account-membership-desktop.jpg、account-membership-mobile.jpg、account-register-error-mobile.jpg，仅属开发演示，不是最终课程证据、真实认证或会员开通证明。

实际HTTP：登录200、会员200、无展示资料的个人中心302、缺CSRF POST403、health UP。当前dev-ui服务127.0.0.1:8080，dev,local-staff,booking-ui,account-ui，工具会话87075/Java PID52477（需接续复核存活）。Room初始8间未改，当前浏览器未进入账户展示/未真实STAFF登录，无生产账号。旧92919已停止，历史92933会话已失效。

## 剩余边界

A接真实身份/账号/密码策略/会员规则；C接报价/Booking/支付/核对；C/D统一库存事务，D接数据库。真实页面必须后端认证/归属验证，不能将真实账号服务挂匿名preview。工作人员预订管理UI、正式Room/UI设计、Q3/Q4与统一版本证据仍需按B清单逐项推进。未做真实JDK17/Windows/MySQL/支付/邮件或最终用户测试，无Git提交/远程/CI/coverage报告。
