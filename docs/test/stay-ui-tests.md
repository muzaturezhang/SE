# 第三批 UI 开发验证

运行环境：macOS arm64、Oracle JDK23、Maven3.9.11，编译目标Java17；并非真实JDK17运行结果。

| 需求 | 验证 |
| --- | --- |
| UI-STAY-001 | StayPreviewWebTest.searchCarriesExactSelectionIntoConfirmationWithoutChangingInventory |
| UI-STAY-002 | invalidSelectionCannotBypassServerValidation（10输入）；postRejectsInvalidContactAndMissingRequiredFieldsWithoutDraft；inventoryChangesBeforePostAreRecheckedAndNoDraftIsCreated；emptyListAndOverlongContactHaveExplicitStates |
| UI-STAY-003 | validPostCreatesOnlyOwnSessionDraftAndIgnoresForgedAmountAndOwner；DemoStayPageGatewayTest.submissionIsOnlySessionDraftAndNeverChangesInventoryOrInventsFinalPrice |
| UI-STAY-004 | everyReadOnlyPaymentScenarioRendersCorrectState（5状态）；listUnknownRecordCsrfAndUnsupportedPaymentMutationsAreHandled；fixtureAmountsAndDeadlinesAreStableAcrossRepeatedReads |
| UI-STAY-005 | 列表和空状态Web用例，浏览器往返导航 |
| UI-STAY-006 | defaultEnvironmentDoesNotExposeBookingUiFixtures；BookingUiPreviewConfigurationTest（5配置）；会话隔离、登录时Session ID轮换、篡改输入、CSRF、404和guestInputIsEscapedOnDetailAndNeverRenderedAsMarkup |
| UI-STAY-007 | 实际浏览器1440/390/320 CSS像素检查与截图，详见log和handoff |
| UI-STAY-008 | docs/interfaces.md接入草案；未宣称真实服务已联调 |

新增33项：Demo Gateway5 + UI Web22 + 配置守卫5 + 基础隔离1；连同原93项共126项。以最终Surefire XML为准。早期库存变化测试误用重复HTTP参数，修正为替换参数后验证409；这不是业务代码失败后的绕过。

UI示例Gateway使用独立Session；Web测试固定Clock并使用独立booking-ui-test H2配置，不对真实用户数据或付款账号操作。未进行MySQL、真实支付、真实身份、跨模块事务、跨进程并发、Windows或JDK17实测。截图是开发预览，不是最终用户测试或支付成功证据。
