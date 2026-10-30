# Room 查询测试登记

本轮实际结果：45 项测试通过，无失败、错误或跳过。其中 Room 专项 41 项，原有基础集成 4 项；参数化输入按实际执行次数计数。

命令：`./scripts/maven.sh --batch-mode --no-transfer-progress -DfailIfNoTests=true verify`。

| 登记 ID | 需求 | 自动化测试位置与行为 |
| --- | --- | --- |
| TC-ROOM-001 | FR-ROOM-003 | StayPeriodTest：7 组区间重叠与邻接边界，双向检查。 |
| TC-ROOM-002 | FR-ROOM-003 | StayPeriodTest：跨月/闰日晚数，2 组空区间与逆序拒绝。 |
| TC-ROOM-003 | FR-ROOM-002/003/004 | DefaultRoomQueryServiceTest：停用排除、可用数与整数分价格。 |
| TC-ROOM-004 | FR-ROOM-003 | doesNotCombineDifferentRoomsThatAreFreeOnDifferentNights：不同晚空闲的不同房间不能拼接。 |
| TC-ROOM-005 | FR-ROOM-003 | permitsArrivalOnAnEarlierGuestsDepartureAndDepartureOnANextGuestsArrival：退房与新入住日期邻接不冲突。 |
| TC-ROOM-006 | FR-ROOM-003 | countsARoomOnceEvenWhenSeveralBlocksOverlapIt：同一房间多个占用不重复扣减。 |
| TC-ROOM-007 | FR-ROOM-002/006 | 筛选单一房型、重复查询结果不变、超出库存数量无结果。 |
| TC-ROOM-008 | FR-ROOM-005 | 服务层拒绝缺日期、同日/逆序、过去日期、非正数量及未知房型。 |
| TC-ROOM-009 | FR-ROOM-003/005 | 共享 Clock 的 Asia/Shanghai 午夜边界；快照引用与不可变性。 |
| TC-ROOM-010 | FR-ROOM-001/004 | RoomSearchWebTest：无日期目录和单房型目录，显示基础价但无假造可用数。 |
| TC-ROOM-011 | FR-ROOM-002/003/004 | 日期查询返回庭院剩余 2 间、98000 分基础价；邻接与停用样例场景。 |
| TC-ROOM-012 | FR-ROOM-006 | Web 无结果返回 200、空列表与可读状态；指定被占用房型同样为空。 |
| TC-ROOM-013 | FR-ROOM-005 | 12 组直接 HTTP 非法输入：日期、数量、类型均为 400；不依赖浏览器校验。 |
| TC-ROOM-014 | FR-ROOM-005 | 不完整查询、显式空数量、未知目录类型拒绝。 |

登记 ID 映射到上述真实测试方法和参数化场景；不是额外生成的测试执行次数。测试源码分别在 `src/test/java/com/holidayresort/booking/room/domain/`、`application/` 和 `web/`；Surefire XML 在 `target/surefire-reports/`。

浏览器另行检查：日期与数量回填、房型筛选的服务器响应、价格详情、错误与空状态，桌面 1440 / 手机 390 CSS 像素无横向溢出。截图保存在 `docs/previews/room-query-*.jpg`，没有将浏览器人工检查计入 JUnit 数量。

环境为 JDK 23，编译目标 17；没有在真实 JDK 17、MySQL 并发、生产数据、Booking、支付、会员、通知或管理员写操作上执行本批测试。
