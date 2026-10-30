# Room CRC 与类型索引

Class Responsibility Collaborator。对应2026-10-29源码；record隐含字段/accessor不逐一重写为手写方法。`+`是public，`~`是包可见；私有辅助方法以源码为准。本表覆盖当前Room包24个顶层Java类型，不声称覆盖A/C/D或全系统全部类。

## 领域和应用

| Class | Responsibility | Collaborators | 关键方法或接口 |
| --- | --- | --- | --- |
| Room | 表达物理房间、所属房型与管理开关 | RoomInventory, RoomManagementService | +Room(String id,String roomTypeId,boolean enabled) |
| RoomType | 房型内容、不可变特征和正整数分基础价 | RoomInventory, RoomAvailability, RoomCardView | +RoomType(String id,String name,String englishName,String description,String imagePath,String imageAlt,List<String> features,long nightlyRateMinor) |
| StayPeriod | 合法半开住期、晚数与重叠 | RoomBlock, RoomSearchResult, query service | +long nights(); +boolean overlaps(StayPeriod other) |
| RoomBlock | 提供一间房的不可用区间 | RoomInventory, StayPeriod | +RoomBlock(String roomId,StayPeriod period) |
| RoomInventory | 一致不可变的目录/房间/占用快照 | RoomType, Room, RoomBlock | +RoomInventory(List<RoomType>,List<Room>,List<RoomBlock>) |
| RoomSearchQuery | 携带搜索条件；不自行预留 | RoomQueryService, RoomPageController | +RoomSearchQuery(LocalDate arrival,LocalDate departure,int roomCount,String roomTypeId) |
| RoomAvailability | 房型与整段连续可用数量 | RoomType, RoomSearchResult | +RoomAvailability(RoomType roomType,int availableRooms) |
| RoomSearchResult | 查询住期、请求数量和不可变结果列表 | StayPeriod, RoomAvailability | +RoomSearchResult(StayPeriod stay,int requestedRooms,List<RoomAvailability> options) |
| ManagedRoom | 房间管理列表/编辑投影 | Room, RoomType, RoomManagementService | +ManagedRoom(Room room,RoomType type,boolean activeOrFutureBlock) |
| RoomQueryService | 为客户查询与UI适配器定义只读边界 | RoomSearchQuery, RoomSearchResult, RoomType | +List<RoomType> listRoomTypes(); +RoomSearchResult search(RoomSearchQuery query) |
| DefaultRoomQueryService | Service校验、排除停用和重叠房间、按房型计数 | RoomRepository, Clock, StayPeriod | 实现RoomQueryService的两个方法；不调用Editor |
| RoomManagementService | 管理写规则/占用保护/精确基础价转换 | RoomInventoryEditor, Clock, ManagedRoom | +list/get/create/update/disable/updateRate；完整签名见room-model.md |
| InvalidRoomSearchException | 带字段的查询业务错误 | DefaultRoomQueryService, RoomPageController | +InvalidRoomSearchException(String field,String message); +String field() |
| RoomManagementException | INVALID/CONFLICT/NOT_FOUND及字段错误 | RoomManagementService, RoomAdminController | +RoomManagementException(Kind kind,String field,String message); +Kind kind(); +String field() |
| RoomRepository | 返回单次一致读快照 | RoomInventory, query service | +RoomInventory readSnapshot() |
| RoomInventoryEditor | 在同一原子边界执行校验与更新 | RoomRepository, management service | extends RoomRepository; +void updateSnapshot(UnaryOperator<RoomInventory> change) |
| InMemoryRoomRepository | 装载开发fixture、发布快照、同步原子更新 | Resource, ObjectMapper, Clock, RoomInventory | +RoomInventory readSnapshot(); +synchronized void updateSnapshot(UnaryOperator<RoomInventory> change) |

## Web 类型

| Class | Responsibility | Collaborators | 关键方法或边界 |
| --- | --- | --- | --- |
| RoomPageController | GET绑定、目录/查询分支、HTTP状态与页面模型 | RoomQueryService, RoomSearchForm, RoomCardView | ~String rooms(RoomSearchForm,BindingResult,HttpServletRequest,HttpServletResponse,Model) |
| RoomAdminController | STAFF房间管理路由、PRG、失败回填 | RoomManagementService, RoomQueryService, StaffNavigationAdvice | ~list/newRoom/create/edit/update/disable/rate/saveRate；实际路由见navigation.md |
| RoomSearchForm | 浏览器日期/间数/房型绑定 | RoomPageController, StaySearchModelAdvice | +getter/setter for arrival/departure/rooms/roomType |
| RoomEditForm | 新增/编辑房号房型和启用绑定 | RoomAdminController | +getter/setter for roomId/roomTypeId/enabled |
| RoomRateForm | 管理页CNY元金额输入 | RoomAdminController | +BigDecimal getNightlyRate(); +void setNightlyRate(BigDecimal) |
| RoomCardView | 房型卡片、基础价格和可空可用数展示 | RoomType, RoomPageController | +static RoomCardView of(RoomType type,Integer availableRooms); record访问器rateLabel() |
| LocalStaffPreviewController | 可选dev本地登录页；不执行密码认证 | Spring Security, local-staff profile | ~String login() |

RoomManagementException.Kind为嵌套枚举。InMemoryRoomRepository中的SeedData与BlockSeed为private record，只负责fixture反序列化，不是数据库/团队DTO。其余构造器、访问器、绑定辅助方法和Controller精确参数以源文件为准；本表不是Java签名替代品。

## UI 边界协作者

这些类型在B的web包，不属于Room领域类图。此处仅列接入所用主类型，完整UI辅助表单/record/enum仍按各源文件评审。

| UI | Controller | Port | 本地适配器 | 真实依赖owner |
| --- | --- | --- | --- | --- |
| 客户预订 | StayPreviewController | StayPageGateway | DemoStayPageGateway | A身份，C预订/支付/报价 |
| 账户会员 | AccountPreviewController | AccountPageGateway | DemoAccountPageGateway | A账号/会员，C会员支付 |
| 员工预订 | StaffStayPreviewController | StaffStayPageGateway | DemoStaffStayPageGateway | A员工身份，C管理查询/状态/快照 |

现有Preview Controller不能被当作生产入口：客户preview匿名、演示登录不认证、Demo持有展示状态。真实Controller必须落实A/C确认的身份、服务授权与状态/错误映射。接口详见 [登记](../interfaces.md)。
