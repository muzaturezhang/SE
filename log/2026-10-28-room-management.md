# 2026-10-28：B 第二批房间管理

## 授权与范围

用户在第一批查询完成、第二批建议列出后回复“好的，开始吧”。实现 B 的具体房间管理和房型基础价；正式身份归 A、Booking/Payment 归 C、数据库/有效占用映射与通知归 C/D。未触碰课程原始资料，未提交 Git 或发布外部服务。

## 实现

- 新增 `RoomManagementService`、`ManagedRoom` 和带 INVALID/CONFLICT/NOT_FOUND 分类的 `RoomManagementException`。
- 保留 `RoomRepository` 只读方法；新增 `RoomInventoryEditor.updateSnapshot(UnaryOperator<RoomInventory>)` 草案。内存适配器 volatile 快照 + synchronized 原子校验/替换；异常不改变旧快照。重复新增房号可并发验证，未宣称数据库并发安全。
- 新增房号唯一、格式约束、已有房型与启用状态校验；房号固定，表单不能重命名。更新不删除房间或 Block。
- Block 退房日 > 共享 Clock 当天时，拒绝停用与换房型；当日退房的历史区间不阻止变更。Block 仍是预先过滤的有效占用输入草案，不定义 Booking 状态。
- 基础价在 RoomType 统一维护，BigDecimal 精确转正 long 整数分，拒绝额外小数与溢出；前台价格复用整数分格式化。
- `RoomAdminController` 和 `templates/room/admin/` 提供清单/筛选、新增、编辑、停用确认、定价、可读错误；成功 PRG，错误保留输入，未提供物理删除。后台沿用已认可视觉，手机表格局部滚动。
- `/admin/**` 由后端要求 STAFF，POST 保留 CSRF。可选 `LocalStaffPreviewConfiguration`、Controller、`scripts/dev-staff.sh` 提供本机 dev 临时 Session；拒绝缺dev、mysql/test或非回环配置。默认启动不创建用户。
- 随机预览密码仅保存于被忽略且mode600的本地缓存，不打印或登记值。A 可通过关闭可选 profile 并接入正式身份替换。
- 更新 README、AGENTS、架构/开发/设计/决策/接口文档；新增需求 FR-ROOM-007..013、管理演示与测试映射。契约仍待 A/C/D 评审，不冻结 schema。

## 实际检查

运行时 Oracle JDK23、Maven3.9.11、macOS arm64，编译目标17。`javap -verbose` 检查 RoomManagementService class major version61。

最终命令：

```sh
./scripts/maven.sh --batch-mode --no-transfer-progress -DfailIfNoTests=true verify
```

2026-10-28 11:54:54 +08:00：BUILD SUCCESS。93 tests、0 failures/errors/skipped，Surefire XML 汇总一致。原45项保留，新服务23 + 管理Web18 + 本地登录Web2 + 配置守卫5 = 新48项。详细映射见 `docs/test/room-management-tests.md`。早期 Web 验证发现一个 option 闭合标签错误、异常页面未带导航模型，均修复并通过最终完整回归。

`sh -n scripts/dev-staff.sh`、密码文件权限/Git忽略检查、源文件空白和冲突标记检查通过。构建和开发服务没有并发编译。

实际浏览器执行本地登录、新增C106、查询5间、停用后查询4间、基础价1234.56前台显示、C101换房型冲突。有效CSS宽度1200/390检查，手机错误页宽度无溢出，表格在自身容器滚动；临时viewport已reset。截图在 `docs/previews/room-admin-*.jpg`，属于开发演示，不冒充最终课程测试。

完成后重启恢复初始8间和庭院980.00，重新登录管理页并保留标签；服务保持回环8080运行，当前工具会话65717。实际HTTP：客房200、匿名管理302、无CSRF POST403、health UP。当前状态记录在 `handoff/CURRENT.md`。

## 限制与对接

内存修改重启丢失，原始fixture不写回；未验证真实JDK17、Windows、MySQL、跨进程并发、真实账号、Booking或价格快照。A接正式身份；C/D共同过滤有效占用，并将管理与Booking写入放入统一事务/锁；D实现持久化读写适配器。接口及固定房号/格式为B模块草案，未替队友冻结模型。
