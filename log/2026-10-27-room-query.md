# 2026年10月27日 第一批 Room 查询

## 授权与范围

用户认可当前安缦参考方向的 UI 后，要求执行第一批：房间查询。完成 Room/RoomType、只读契约、内存样例数据、后端日期/房型/数量查询、基础价、错误与无结果处理。保持 A/C/D 边界，不实现会员、Booking、支付、数据库写入或管理员功能。

## 实际实现

- 新增 Room、RoomType、StayPeriod、RoomBlock、RoomInventory。日期区间为半开区间；房间停用与日期占用分开；快照检查重复 ID 和悬空引用，集合不可变。
- 新增 RoomQueryService/DefaultRoomQueryService、查询/结果记录与 InvalidRoomSearchException。一次查询使用一致快照，排除任一住夜被占用的物理房间，不按各晚最小数量拼接不同房间。
- RoomRepository 只读草案与 InMemoryRoomRepository 适配，样例文件保存在 data/fixtures，Maven 打包到 classpath。样例占用相对启动当天生成；mysql profile 不注册此适配。
- RoomPageController 绑定白名单查询字段，验证参数，返回 200 目录/结果/空状态或 400 可读错误。无日期不假造库存；价格从服务器的 CNY 整数分生成。
- 页面循环服务器返回的房型，展示每间每晚基础价、连续可用数量、错误和无结果。JS 不再伪造查询结果，只辅助日期、筛选 GET 与弹窗。
- 需求、接口草案、演示数据说明、测试映射及开发文档同步更新；接口与 schema 仍待 C/D 评审。

## 实际验证

- 首轮基础构建 4 项通过，加入 Room 专项后共 45 项通过。
- 其中一轮 verify 仅输出构建成功、未输出测试计数，未将该次日志当作新测试执行证据；最终独立执行 `./scripts/maven.sh --batch-mode --no-transfer-progress -DfailIfNoTests=true verify`，确认 45 tests、0 failures/errors/skipped。Surefire XML 汇总一致。
- 区间测试 10、服务测试 14、HTTP/页面测试 17、基础集成 4；包括跨夜连续性、邻接、停用、重复占用、时区、只读性、非法绑定和空结果。
- Node `--check`、源码空白检查通过。查询服务 class major version 61；实际构建/运行仍是 JDK 23，未宣称 JDK 17 运行已测。
- 实际 dev HTTP 目录/可用/空结果为 200，数量 0 与非法日期为 400，health 为 UP。
- 浏览器输入 2026-10-29 至 2026-10-31、1 间，返回庭院 2 间与每晚 980.00；同期泳池无结果；详情价格正确。直接访问非法查询 URL 显示后端错误。
- 桌面 1440、手机 390 CSS 像素无横向溢出。截图时最初位于 collection 锚点，固定导航影响长图；回到页面顶端重新保存并检查。截图是样例数据功能演示，不是生产或最终课程证据。

## 服务与交接

旧开发会话 31652 已停止，新会话 26212 使用 `./scripts/dev.sh` 运行在 8080，完成后保留。浏览器保留成功查询页面并恢复默认视口。当前状态在 handoff/CURRENT.md；样例和演示日期在 docs/room-query-demo.md；需求/测试映射分别在 docs/requirements/room-query.md 与 docs/test/room-query-tests.md。

下一批建议 Room 管理，但尚未实现。D 需提供持久化适配，A 接入 Staff 身份，C 与 B/D 共同实现预订前复核和事务占用；查询结果不保证之后一定能预订。
