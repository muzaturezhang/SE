# 接口对接登记

配套设计与可转发交接见 [B设计材料](design/README.md) 和 [B模块接入说明](../handoff/B-team-integration.md)。

状态：责任和信息范围已按启动方案确认。B 已实现下面的只读查询契约用于本模块开发；这些 Java 签名、DTO 和异常仍为对接草案，待 C/D 评审后才成为跨模块基线。第二批追加管理写端口与 STAFF 页面契约，仍为待 A/C/D 评审草案；其他成员的业务接口尚未实现。

## B 已实现的只读查询草案

包：`com.holidayresort.booking.room.application`。

```java
interface RoomQueryService {
    List<RoomType> listRoomTypes();
    RoomSearchResult search(RoomSearchQuery query);
}

record RoomSearchQuery(LocalDate arrival, LocalDate departure,
                       int roomCount, String roomTypeId) {}
record RoomAvailability(RoomType roomType, int availableRooms) {}
record RoomSearchResult(StayPeriod stay, int requestedRooms,
                        List<RoomAvailability> options) {}
```

- Java 契约中 `roomTypeId == null` 表示全部房型；未知非空 ID 抛 `InvalidRoomSearchException`。
- 日期使用 LocalDate 与 [arrival, departure)，入住不得早于共享 Clock 中的当天，房间数量为正整数。
- availableRooms 是同一批物理房间在整个住期都可用的数量；仅返回数量不少于请求值的房型。
- RoomType.nightlyRateMinor 是 CNY 每间每晚整数分；本服务不计算折扣、总价或锁定价格。
- 无结果使用空 options；非法查询抛 InvalidRoomSearchException，其 field 指明 arrival/departure/rooms/roomType。
- 查询只读，不承诺后续一定可以创建 booking。C 创建 booking 时仍需与 B/D 共同完成事务内复核和占用。

包：`room.application.port`，持久化适配草案：

```java
interface RoomRepository {
    RoomInventory readSnapshot();
}
record RoomInventory(List<RoomType> roomTypes, List<Room> rooms,
                      List<RoomBlock> blocks) {}
```

一次 readSnapshot 返回一致且不可变的目录、房间与占用输入。RoomBlock 是用于只读可用性判断的不可用区间，不定义 Booking/Payment 状态。D 的持久化实现放在其适配层；本轮 B 的临时实现位于 `room.infrastructure.InMemoryRoomRepository`。

## 页面 GET 查询

`GET /rooms` 浏览目录，不提供无日期的可用数量。`?roomType=pool` 可以单独筛选目录。

`GET /rooms?arrival=2026-11-01&departure=2026-11-03&rooms=1&roomType=all` 查询可用性，HTTP 参数使用 ISO 日期与整数。all 或空房型表示全部。只要传 arrival/departure/rooms 中任意一个，即按查询处理并要求两个日期均合法；rooms 省略时默认 1，显式传空值或非法整数则拒绝。

成功与无结果均为 200；无法绑定的日期/数量、缺字段、过去日期、逆序或同日退房、非正数量、未知房型为 400，返回同一酒店页面与可读提示。暂无 JSON HTTP API；C 可通过应用内 Java Service 对接草案。

| Provider → Consumer | 信息与行为 | 共同评审人 |
| --- | --- | --- |
| Room → Booking | 房型、日期、数量、价格、可用结果；同一事务内分配和释放 | B C D |
| Member → Booking / Payment | 会员有效性、折扣规则、会员费标准 | A C |
| Booking / Payment → UI | 页面所需记录、总价、状态与用户可读错误 | B C |
| Member → UI | 注册结果、身份、会员状态与错误 | A B |
| Booking → Reminder | booking 日期、邮箱、调度时区与提醒事件 | C D |
| Repository → Services | 读写契约、事务、锁、约束、迁移和测试数据重置 | D 及模块 owner |

## 在写业务代码前补齐

1. 评审本轮 Room 与 RoomType 字段草案，确认是否有独立房型管理；样例 ID/价格不是业务基线。
2. 评审只读查询契约，确认 Booking 所需价格快照与可用性二次校验方式。
3. Room 分配和释放与 Booking 的同一事务边界、幂等和锁顺序。
4. 待支付、支付核对、超时关闭与释放所用的共享状态。
5. 会员折扣实际比例和有效期。
6. Repository 接口所属模块与 D 的适配实现位置。

接口新增或变更时记录：日期、owner、输入、输出、错误、事务、状态、受影响模块、评审结果。Mock 不能自行冻结这些业务约定。

## 2026-10-28：B 管理写契约草案

Owner：B；评审人：A（身份）、C（有效占用/价格快照）、D（持久化、锁与唯一约束）。原 RoomRepository 只读方法不变，以保留查询调用方与测试的兼容性。

```java
interface RoomInventoryEditor extends RoomRepository {
    void updateSnapshot(UnaryOperator<RoomInventory> change);
}
// RoomManagementService
List<ManagedRoom> list(String typeId, String state); // null/空 type=全部；state=all/enabled/disabled
ManagedRoom get(String roomId);
void create(String roomId, String typeId, boolean enabled);
void update(String roomId, String typeId, boolean enabled);
void disable(String roomId);
void updateRate(String typeId, BigDecimal nightlyRate); // CNY 元，精确转换为整数分
```

- `updateSnapshot` 回调必须在同一原子边界内读取、校验、生成与提交；异常不得提交。当前内存实现用 synchronized + volatile，不证明 MySQL 或跨进程并发安全。回调不可执行外部副作用。
- D 可以在评审后将整份快照回调改为适合数据库的事务端口，但必须保留原子语义。房间管理、C 的占用创建/释放必须共享事务、锁策略和顺序；不要各用独立锁。重复房号还需要数据库唯一约束。
- Room.id 暂等于固定房号；新增去首尾空白，接受 `[A-Z0-9][A-Z0-9-]{0,19}`。编辑不会重命名它；typeId 必须已存在。没有物理删除。
- 当前/未来占用判断为 `RoomBlock.period.departure > LocalDate.now(clock)`。当前 Block 只是 fixture 输入；C/D 后续须映射有效待支付占用与有效预订，不能把取消/失效记录直接当作有效 Block。
- 有占用时拒绝停用与切换房型；无占用时可改房型和状态。停用保留房间、类型与历史 Block。基础价更新不改变未来订单快照，快照尚未实现，归 C。
- 金额使用 BigDecimal 精确转换，非正值、不能精确到分或 long 溢出均拒绝。输出 RoomType.nightlyRateMinor 仍为整数分。
- RoomManagementException 分类 INVALID / CONFLICT / NOT_FOUND，含 field 和可读消息；HTTP 分别为 400 / 409 / 404。

| HTTP | 行为 |
| --- | --- |
| GET /admin/rooms?type=&state=all | 管理列表与筛选 |
| GET /admin/rooms/new | 新增表单 |
| POST /admin/rooms | 新增物理房间 |
| GET /admin/rooms/{roomId}/edit | 编辑与停用确认页 |
| POST /admin/rooms/{roomId} | 修改房型/启用状态，URL 决定固定房号 |
| POST /admin/rooms/{roomId}/disable | 停用，重新检查占用 |
| GET /admin/room-types/{typeId}/rate | 房型基础价表单 |
| POST /admin/room-types/{typeId}/rate | 更新基础价，nightlyRate 为 CNY 元 |

所有 `/admin/**` 由后端要求 ROLE_STAFF；POST 还需 CSRF。成功采用 POST/Redirect/GET 与一次性提示；失败表单保留输入。GET 不修改数据。未提供 JSON 写 API。默认 profile 无账号，测试通过模拟身份验证；可选 local-staff 的临时 Session 登录仅用于本机 dev，可直接被 A 的正式身份模块替换。

## 2026-10-28：B 预订流程页面投影草案

Owner：B；评审人 A/C。包 `web.stay`，没有修改 booking/payment 领域实现或定义共享状态。下列是 UI 所需数据的适配边界，不能当作已实现的 C 服务。

```java
interface StayPageGateway {
    Quote quote(Selection selection);
    Reservation submit(String viewerId, Submission submission);
    List<Reservation> listFor(String viewerId);
    Optional<Reservation> findFor(String viewerId, String reference);
}
record Selection(String roomType, LocalDate arrival, LocalDate departure, int rooms) {}
record Submission(Selection selection, String guestName, String guestEmail) {}
record Quote(StaySummary stay, int availableRooms, Long totalMinor, String discountLabel) {}
// StaySummary: 房型ID/名称/图片、入住/退房、间数、nightlyRateMinor
// Reservation: reference、StaySummary、联系资料、Long totalMinor、discountLabel、DisplayState、paymentDeadline
```

- 金额输入不接受客户端总价/折扣/状态。Quote.totalMinor 与 Reservation.totalMinor 为 CNY 整数分、可空；空表示未获取最终报价，不是零元。金额与折扣由 C/A 返回，B 只格式化。
- DisplayState 的 DRAFT/WAITING/PAID/FAILED/REVIEW/EXPIRED 是展示投影，不是 Booking/Payment 共用枚举。由未来真实适配器把 C 的结果映射到 UI；不得据此实现真实状态转移。
- Demo 的 submit 只替换当前会话 UI-DRAFT；返回参考号用于 UI 导航，不创建实际订单。真实 submit 对应 C 的创建服务，需评审其报价快照、幂等键、归属与事务、库存二次复核、可读错误；本轮没有实现。
- Demo 的 viewerId 来自服务端 HttpSession ID，不接受请求参数；固定例子用 guest@example.invalid 和演示住客。本会话最多一个草稿，其他会话不可读取该草稿。示例金额为固定快照，刷新不触发支付流转，演示截止时间不执行定时业务。
- 未来真实 viewerId 必须取自 A 的已认证 CUSTOMER 身份，C 在 Service 内验证读取、创建、支付的订单归属。当前匿名 preview 路由不能改接真实数据源后继续开放。
- 表单校验姓名必填/最多80字、邮箱必填/格式/最多254字；日期和数量还调用 RoomQueryService 验证。提交时再读可用性仍不预留库存，不能替代 C/D 的事务校验。
- 输入错误 HTTP400、库存不足409、记录不存在/别的会话草稿404；错误页保留输入且不输出类型转换细节。提交用 POST+CSRF+PRG，页面 no-store，联系资料只存在本会话，不放进查询 URL 或日志。

| 仅本机演示 HTTP | 页面/行为 |
| --- | --- |
| GET /preview/stays/new | 回填选房参数，入住确认与联系表单；无参数预填 D+10 至 D+12、1间、演示资料 |
| POST /preview/stays | 验证并生成/替换本会话 UI 草稿；不占库存、不计算最终总价 |
| GET /preview/stays | 本会话草稿与5条固定示例；example=empty 显示空状态，未知示例参数400 |
| GET /preview/stays/{reference} | 草稿或固定记录详情 |
| GET /preview/stays/{reference}/payment | 只读支付展示，按钮禁用，无可发起扣款的 POST |

默认 profile 拒绝上述路由；booking-ui 只允许 dev+回环且不得与mysql/test共用。C/A接入清单：认证主体、报价与优惠结果、创建结果/幂等、本人列表和详情、支付入口与核对结果、服务错误映射。支付创建按钮、真实路由与联调必须在这些服务确认后接入；当前不声称可直接换一个 Bean 就完成真实业务集成。

## 2026-10-28：B 账户 / 会员页面投影草案

Owner B，评审人 A/C。包 web.account，没有实现 A 的账号存储/认证/会员服务。页面字段和姓名/邮箱/密码输入边界待 A 评审，不冻结数据库或生产密码策略。

```java
interface AccountPageGateway {
    Optional<Profile> profileFor(String viewerId);
    void previewSignIn(String viewerId, String email);
    void previewRegistration(String viewerId, String displayName, String email);
    Optional<Profile> registrationFor(String viewerId);
    void clearPreview(String viewerId);
    Membership membershipExample(String example);
}
record Profile(String displayName, String email) {}
record Membership(MemberDisplayState state, LocalDate startDate, LocalDate endDate,
                  Long feeMinor, String discountLabel, String validityLabel) {}
```

- preview* 只是当前 Session 的页面展示操作，不验证密码、创建账号、发送邮件或写 SecurityContext，不能作为实际认证接口。密码在请求表单内验证并清空；Gateway 签名没有密码，不允许 Session/flash/log 保存表单。
- Profile 是页面资料投影，不是 Customer/Member 实体；正式本人资料需要 A 认证后返回。演示 viewerId 取当前服务端 Session ID，跟随登录时的 ID 轮换，拒绝别的会话读取或改写。
- MemberDisplayState 的 NONE/ACTIVE/EXPIRED/PENDING 为 UI 展示投影，不是团队公共状态机。切换例子不修改资格；开通/续费页无写操作和支付接口。
- feeMinor 为可空整数分，空表示待报价，不是免费。discountLabel/validityLabel 待 A 返回，B 不确定费率、优惠比例、会员有效期或资格。会员费仍按已确认决定独立于住宿房费。
- 登录后返回由 AccountReturnTarget 校验：允许 /preview/account、/membership、/membership/join?intent=join或renew，以及 /preview/stays 和 /preview/stays/new 的 roomType/arrival/departure/rooms 参数。拒绝站外/协议相对/编码/控制字符/重复未知参数和管理地址。不是任意URL重定向能力。
- 姓名必填最多80字，邮箱必填/格式/最多254字；演示密码必填最多256字符，注册重复一致。256是当前UI传输边界，不代表A的实际密码策略。错误400，未知展示状态400，别的会话/缺注册结果404。
- 表单 POST+CSRF+PRG，页面 no-store，密码不绑定HTML value，错误时清空；后续登录可从本会话注册结果回填演示邮箱，同邮箱展示相应演示姓名。不是账号查询或认证。

| 仅开发预览 HTTP | 行为 |
| --- | --- |
| GET/POST /preview/account/login | 表单/只切换登录后页面展示，next为白名单地址；example=denied只展示错误 |
| GET/POST /preview/account/register | 表单/只生成姓名邮箱预览；example=duplicate只展示邮箱已注册提示 |
| GET /preview/account/registration-result | 本会话注册表单预览结果，保留后续登录返回位置 |
| GET /preview/account | 个人中心；无展示资料则跳演示登录 |
| POST /preview/account/logout | 清理账户展示资料，保留真正SecurityContext和其他模块草稿 |
| GET /preview/account/membership | none/active/expired/pending只读例子 |
| GET /preview/account/membership/join?intent=join或renew | 开通/续费方案展示；缺展示资料时经登录返回，没有付款POST |

account-ui 仅 dev+回环，拒绝 mysql/test，默认不启用。未来需要 A 提供真实注册/认证/本人资料/会员状态/资格/费率/有效期及可读错误，C 提供会员支付与核对结果；真实Controller需CUSTOMER鉴权、后端归属检查、密码策略与正式退出。当前预览Controller不能只换Bean就接入生产数据。

## 2026-10-29：B 工作人员预订页面只读草案

Owner B，评审人A/C，包web.staffstay。没有修改C的booking/payment或A的member领域。新增可选staff-booking-ui，仅dev+回环，拒绝mysql/test；所有路由仍受ROLE_STAFF保护，没有匿名例外。

```java
interface StaffStayPageGateway {
    List<Entry> search(String staffId, Query query);
    Optional<Entry> find(String staffId, String reference);
}
record Query(String reference, LocalDate arrival, State state) {}
// Entry: reference、guestName/guestEmail、roomType、arrival/departure、rooms、
// nullable Long totalMinor、discountLabel、State
```

staffId只来自后端Authentication.getName，不接收请求身份参数。Mock仅拒绝缺失身份；真正服务必须由A/C校验工作人员权限和数据范围，不能把字符串存在当作服务授权。Query编号规范化并限制40位字母/数字/短横线、部分匹配；日期是入住日精确匹配、允许历史，状态为可空过滤条件。多个条件取交集；空列表表示无结果，find空为404。

State的WAITING/CONFIRMED/FAILED/REVIEW/EXPIRED只是展示投影，预订和支付文案分开显示，不是共享枚举或真实状态转换。金额为CNY整数分、空值待报价；B不自算总价/优惠。房号待C返回，不由UI分配。六条固定记录与客户Session草稿独立，启动Clock锚定示例日期；只读不随刷新流转。

GET /admin/stays 支持reference/arrival/state；GET /admin/stays/{id}展示详情并只保留上述已知筛选字段用于返回，无任意next跳转。非法筛选400、缺记录404；no-store、转义保留。没有管理预订/支付POST；CSRF依旧启用。

待A/C评审：真实员工身份与服务授权、列表可见范围/联系字段、分页/排序与查询上限、完整预订/支付状态映射、价格快照/优惠/房号和错误。真实变更/支付核对/取消/改期、事务与库存释放由C/D提供后另行接入。当前不能直接替换Mock后宣称业务集成完成。
