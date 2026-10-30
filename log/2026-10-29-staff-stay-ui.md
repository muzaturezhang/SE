# B 第五批：工作人员预订管理 UI

日期2026-10-29，Asia/Shanghai。用户“继续吧”承接第五批只读工作人员预订列表/详情与统一后台导航的建议。

## 路径与归属

开始时通过Codex项目配置确认SE1最新根目录仍为 `/Users/zhangwenhan1031/Desktop/SE`；会话cwd一致，工程在其子目录holiday-resort-booking。pwd -P与可写检查通过，已读工程AGENTS、CURRENT、decisions、interfaces、B清单。创建/编辑前再次核对cwd/写入权限，没有迁移或回退旧目录。源文件、文档、截图和缓存均在工程内，未改原课程材料。

B新增 `web.staffstay` 的StaffStayPageGateway / DemoStaffStayPageGateway / StaffStayPreviewController / StaffStayUiException，及templates/staffstay三页。没有修改A的member、C的booking/payment业务实现、D的数据库schema。Gateway与状态仍是待A/C评审的页面草案。

## 结果

- GET /admin/stays：六条独立合成记录；编号部分匹配/大小写与空白规范化、入住日精确匹配（允许历史）、状态组合筛选，空结果/重置/400字段错误。
- GET /admin/stays/{id}：联系资料、房型/住期/数量/晚数、费用快照与预订/支付状态；返回保留三个筛选条件，未知记录404。
- 固定金额为整数分的UI快照，null明确待报价；不计算优惠/总价，不查询/改写Room，未与客户Session草稿混用。没有实际预订、房号分配、价格快照持久化、支付、取消/改期或释放。
- 管理导航房间/预订切换、当前页标记；详情待接入操作禁用，没有业务写端点。
- staff-booking-ui独立可选dev回环profile，拒绝mysql/test/非回环。dev-ui.sh显式启用第五个profile；默认dev/dev-staff不安装工作人员预订fixture。
- SecurityConfiguration无改动；/admin/**继续ROLE_STAFF，未增加匿名例外。staffId从Authentication取得，fixture仅检查非空，真实服务授权待A/C。no-store、文本转义、已有CSRF和真实STAFF退出保持。
- 文档：staff-stay-ui-demo、requirements/staff-stay-ui、test/staff-stay-ui-tests；同步接口、决定、README、本地开发、UI规范、AGENTS和B清单。

## 实际检查

运行环境macOS arm64、Oracle JDK23、Maven3.9.11；源码/字节码Java17目标，StaffStayPreviewController major version61。没有真实JDK17运行验证。

1. 编译成功。定向测试初次37项中1项失败：Spring RequestParam defaultValue把显式空状态当默认all。改为只对缺失参数设默认值，显式空状态返回400；不是删除失败测试。
2. 最终 `./scripts/maven.sh --batch-mode --no-transfer-progress -DfailIfNoTests=true verify`：2026-10-29 20:47:16 +08:00 BUILD SUCCESS，222 tests，0 failures/errors/skipped。Surefire XML汇总一致。此前191项保留，新增31=Gateway5+Web19+profile6+Foundation1。
3. 覆盖匿名/CUSTOMER拒绝且不调用Gateway、真实后端主体与伪造staffId隔离、默认STAFF404和导航隐藏、筛选/详情返回/空/400/404、五状态六记录、空金额、转义/no-store、只读与库存快照不变、无CSRF403/带CSRF仍405、profile约束。
4. sh -n预览脚本通过；源码/文档空白与冲突标记检查、Git diff --check通过。凭证文件mode600且Git忽略，未输出其值。
5. 原8080没有进程，verify完成后才启动dev-ui，未并发编译。启动成功profiles dev,local-staff,booking-ui,account-ui,staff-booking-ui，回环8080。工具会话25187，Java PID21941，接续需重新检查存活。
6. 实际浏览器先被重定向本地登录，使用现有临时STAFF账号；从房间导航进入预订。002+2026-11-08+CONFIRMED只有1条；进入详情后点击返回，三个条件与1条结果保留。NOT-FOUND显示空状态；非法编号显示可读错误。
7. 1440桌面、390手机列表/详情/错误、320最窄列表/详情/错误均无整页横向溢出（scrollWidth等于innerWidth）。004详情支付待核对提示可见，操作按钮实际disabled。临时viewport已reset，最后干净全列表tab已markDeliverable，未退出或修改房间。
8. 保存并查看开发截图：staff-stay-list-desktop.jpg、staff-stay-detail-desktop.jpg、staff-stay-list-mobile.jpg、staff-stay-detail-mobile.jpg、staff-stay-error-mobile.jpg，均位于docs/previews。不包含凭证，不代表真实业务/可用性研究或最终课程证据。

## 边界与后续

未实现真实员工身份、服务数据范围/分页排序、Booking/Payment状态转移、支付核对/扣款、取消/改期、库存占用释放、MySQL或通知。A/C需确认Gateway投影与权限/分页/快照/字段/错误；C/D实现事务后再接入写操作。Q8仍待范围确认。

下一步B可独立整理Room class diagram/CRC、页面导航与availability交互设计、逐页UI原则映射和接口交接包，标明草案和待评审项。正式Q3/Q4、同版本四核心功能最终截图、真实用户研究与团队RS/DS/TR仍需实际集成和基线审查；不要宣称整个B任务已最终完成。Git无提交/远程/tag；无真实JDK17、Windows/MySQL、CI/JaCoCo或用户研究结果。
