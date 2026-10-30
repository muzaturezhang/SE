# Room 管理验证与需求映射

2026-10-28，本机 Oracle JDK 23，字节码目标 Java 17。自动化用例是开发验证，不是最终用户测试证据。

| 需求 | 主要自动验证 |
| --- | --- |
| FR-ROOM-007 | RoomManagementServiceTest.disabledCreationIsExcludedAndListFiltersAreApplied；RoomAdminWebTest.staffViewsListFiltersEditorsAndCsrfForms |
| FR-ROOM-008 | mutationsImmediatelyChangeQueriesAndPreserveOldSnapshots；invalidRoomIdentifiersNeverMutate（7 输入）；simultaneousDuplicateCreatesHaveExactlyOneWinner |
| FR-ROOM-009 | createEditDisableAndReenableKeepFixedRoomIdentity；mutationsImmediatelyChangeQueriesAndPreserveOldSnapshots |
| FR-ROOM-010 | currentAndFutureBlocksGuardDisableAndTypeChangeButCheckoutTodayIsFinished（4 日期场景）；duplicateAndActiveOccupancyReturnConflictWithoutMutation |
| FR-ROOM-011 | rateUsesExactMinorUnitsAndImmediatelyChangesQueryPrice；invalidRatesNeverRoundOrMutate（6 输入）；staffRateUpdateIsVisibleOnGuestPage；invalidRatesReturn400WithoutSaving（6 输入） |
| FR-ROOM-012 | everyMutationRequiresCsrfEvenForStaff（4 路径）；匿名与 CUSTOMER 对读/写路径拒绝；LocalStaffPreviewWebTest 的登录、错误密码、CSRF 和退出；LocalStaffPreviewConfigurationTest 的 5 个配置拒绝场景 |
| FR-ROOM-013 | duplicateUnknownTypeAndMissingRoomLeaveInventoryUnchanged；invalidRoomFieldsAreRejectedAndSafeValuesRetained；unknownRecordsAndInvalidFiltersHaveReadableErrors |

第二批新增 48 项（服务 23 + 管理 Web 18 + 本地登录 Web 2 + 配置守卫 5），连同第一批 45 项共 93 项。权威执行结果见 handoff/CURRENT.md 与 Surefire XML，不把表格行数当测试数。

浏览器实测：本地 STAFF 登录 → 新增 C106 → 查询庭院可用 5 间 → 停用 C106 → 可用 4 间；修改基础价 1234.56 在前台显示；C101 有未来占用，更换房型提交被拒绝。手机有效 CSS 宽度 390：错误提示无页面横向溢出，表格宽于容器但只在表格区滚动。完成演示后重启恢复 fixture。

未验证真实 MySQL、跨进程并发、真实 JDK 17、Windows、真实账号、Booking/支付或价格快照。并发用例仅验证当前单进程内存适配器。
