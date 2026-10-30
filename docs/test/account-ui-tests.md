# 第四批账户 UI 开发验证

运行时macOS arm64、Oracle JDK23、Maven3.9.11；编译目标Java17，不冒充真实JDK17运行。最终结果以handoff和Surefire XML为准。

| 需求 | 自动验证与开发检查 |
| --- | --- |
| UI-ACCOUNT-001 | AccountReturnTargetTest共24输入；AccountPreviewWebTest.invalidReturnTargetIsRejectedOnGetAndPostWithoutChangingPresentation（7输入）；登录保持选房参数、续费经登录返回 |
| UI-ACCOUNT-002 | 注册结果、缺字段/邮箱、密码不一致、4种传输长度边界、注册资料接续登录，固定错误示例只读 |
| UI-ACCOUNT-003 | 成功/错误响应不出现演示密码，错误模型密码清空；演示登录后仍unauthenticated、无管理权限，客户端roles字段被忽略 |
| UI-ACCOUNT-004 | Demo Gateway会话隔离与ID轮换；退出保留Stay草稿；@WithMockUser STAFF退出预览后仍有管理身份 |
| UI-ACCOUNT-005 | Gateway与Web各4种会员展示，不造费率、空费用不等于0，未知状态400 |
| UI-ACCOUNT-006 | joinAndRenewReturnThroughLoginAndHaveNoPaymentMutation，付款POST不支持，禁用按钮 |
| UI-ACCOUNT-007 | profile守卫5种无效配置、默认环境隔离、3个POST的CSRF、注册结果XSS转义和跨会话404、no-store |
| UI-ACCOUNT-008 | 浏览器1440/390/320检查与docs/interfaces.md接入清单；截图为开发预览 |

新增65项=返回地址24 + Demo Gateway8 + Account Web27 +配置守卫5 +默认隔离1；连同此前126项共191项。复用既有Room/Stay/STAFF回归。初次返回续费测试用String重新构建已编码URL导致MockMvc二次编码，改用URI跟随真实Location；未放宽返回地址白名单。

尚未验证真实认证/会员/付款/邮件、MySQL、跨模块事务、Windows/JDK17或最终用户测试；固定示例不能作为会员开通或付款证据。
