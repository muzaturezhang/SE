# Group 6 Stage 4 成品使用与测试说明

Group 6：Nacy Lin, Muzature Zhang, Freya Wang, Mila Liu

## 先看这一点

`http://127.0.0.1:8080` 只表示“运行服务的那台电脑”。它不能作为公共链接直接发给队友。队友需要在自己的电脑上启动一份应用，或者由团队把应用部署到共享服务器。

## 当前团队共享测试网址（推荐）

<https://788f17126e8464.app.lhr.life>

- 队友不需要安装 Java，直接打开该网址。
- 所有人共享同一套测试数据和流程。
- 该地址是临时公网隧道，可能在隧道重启后变化。
- 只有在托管电脑、应用服务和 SSH 隧道都保持运行时才能访问。
- 只允许使用虚构姓名、虚构邮箱和 sandbox 支付，不得输入真实个人或支付信息。

STAFF 密码由项目负责人私下发送给负责 STAFF 测试的成员。

## 可选：每人本地启动

推荐方式：每名队友在自己的电脑运行同一版代码。

## 本地运行准备（可选）

每台电脑需要：

1. Windows PowerShell
2. Java 17
3. Git（或者直接在 GitHub 页面下载 ZIP）

仓库地址：<https://github.com/muzaturezhang/SE>

如果使用 Git：

```powershell
git clone https://github.com/muzaturezhang/SE.git
cd SE
```

如果没有 Git：

1. 打开 <https://github.com/muzaturezhang/SE>
2. 点击 `Code` → `Download ZIP`
3. 解压 ZIP
4. 在解压目录打开 PowerShell

## 本地启动（可选）

在项目根目录运行：

```powershell
.\scripts\run-stage4.ps1
```

如果 PowerShell 阻止脚本执行，只对当前窗口临时放行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\run-stage4.ps1
```

脚本会自动：

- 使用项目内 JDK 17，若不存在则使用系统 Java 17
- 启动客户、会员、预订、支付和 STAFF 管理正式路由
- 使用本地持久化 H2 测试数据库
- 生成随机 STAFF 密码
- 输出测试网址

停止服务：在运行脚本的 PowerShell 窗口按 `Ctrl+C`。

## 正式使用网址

以下地址在“启动服务的那台电脑”上打开：

- 首页：<http://127.0.0.1:8080/>
- 客房与可用性：<http://127.0.0.1:8080/rooms>
- 客户注册：<http://127.0.0.1:8080/account/register>
- 客户登录：<http://127.0.0.1:8080/account/login>
- 安排入住：<http://127.0.0.1:8080/stays/new>
- 我的预订：<http://127.0.0.1:8080/stays>
- 会员页面：<http://127.0.0.1:8080/account/membership>
- STAFF 房间管理：<http://127.0.0.1:8080/admin/rooms>
- STAFF 预订管理：<http://127.0.0.1:8080/admin/stays>

## 四、STAFF 登录

用户名：

```text
localstaff
```

密码在每个人的本机读取：

```powershell
Get-Content .\.cache\staff-preview\password.txt
```

不要把自己的密码文件发给别人。每台电脑第一次运行脚本时会自动生成自己的密码。

## 五、客户测试流程

1. 注册虚构客户
2. 登录
3. 搜索可用房间
4. 查看服务器报价
5. 创建预订
6. 检查预订状态是否为“待支付”
7. 进入 sandbox 支付
8. 测试“支付成功”“支付失败”“结果未知”
9. 检查我的预订列表
10. 测试改期和取消
11. 检查会员费支付

预期结果：

- 金额由服务器计算
- 预订状态与支付状态分开显示
- 支付结果未知时显示“支付待核对”
- 客户只能看到自己的预订
- 改期会重新报价
- 取消会释放房间
- sandbox 不产生真实扣款

## 六、STAFF 测试流程

1. 使用 `localstaff` 登录
2. 打开 `/admin/rooms` 检查房间和基础价管理
3. 打开 `/admin/stays`
4. 按预订编号、入住日期、状态筛选
5. 打开一条“支付待核对”的预订
6. 测试“确认支付成功”
7. 或用 3–255 字拒绝原因测试“拒绝并标记支付失败”
8. 检查预订状态、支付状态和库存释放

STAFF 页面不允许任意修改金额、客户归属、状态或房间分配。

## 七、权限与安全测试

1. 退出登录后访问 `/stays`，应跳转登录
2. CUSTOMER 访问 `/admin/stays`，应返回 403
3. 客户 A 访问客户 B 的预订编号，应返回 404
4. 所有 POST 都必须带 CSRF
5. 页面不得显示密码、银行卡号或支付凭证
6. 只能使用虚构姓名和 `@example.invalid` 邮箱

## 八、Q5/Q7 正式用户测试

正式记录前先阅读：

- `docs/test/user-test-consent-and-privacy.md`
- `docs/test/user-test-plan.md`
- `docs/test/user-test-observation-sheet.csv`

建议每名观察者：

1. 先取得参与者同意
2. 分配匿名编号 `P-C-01`、`P-S-01`
3. 不提前演示界面
4. 记录完成情况、时间、错误、恢复次数和困惑点
5. 对截图去除姓名、邮箱、预订号、支付参考号
6. 修复问题后进行复测
7. 将真实结果汇总给 D，更新 Test Report 和 Q7 章节

## 九、本地数据

测试数据库：

```text
data/local/stage4-use.mv.db
```

重启后数据保留。停止服务后如需清空：

```powershell
Remove-Item -LiteralPath .\data\local\stage4-use.mv.db
```

## 十、最终交付材料

- 报告 PDF：<https://github.com/muzaturezhang/SE/releases/download/group6-stage4-engineering-final-2026-11-14/Group6-Stage4.pdf>
- 可编辑报告：<https://github.com/muzaturezhang/SE/releases/download/group6-stage4-engineering-final-2026-11-14/Group6-Stage4-Report.docx>
- 演示 PPT：<https://github.com/muzaturezhang/SE/releases/download/group6-stage4-engineering-final-2026-11-14/Group6-Stage4.pptx>
- 完整交付 ZIP：<https://github.com/muzaturezhang/SE/releases/download/group6-stage4-engineering-final-2026-11-14/Group6-Stage4-Engineering-Final-2026-11-14.zip>

## 可直接发到群里的消息

```text
第六组 Stage 4 测试说明：

仓库：
https://github.com/muzaturezhang/SE

注意：127.0.0.1 只能在运行服务的本机打开，不能直接发公共链接。每人在自己电脑运行同一版。

1. 安装 Java 17，克隆或下载仓库 ZIP
2. 在项目根目录运行：
   .\scripts\run-stage4.ps1
3. 打开：
   客户注册 http://127.0.0.1:8080/account/register
   我的预订 http://127.0.0.1:8080/stays
   STAFF 登录 http://127.0.0.1:8080/account/login
   STAFF 预订管理 http://127.0.0.1:8080/admin/stays
4. STAFF 用户名 localstaff，密码本机读取：
   Get-Content .\.cache\staff-preview\password.txt
5. 全部使用虚构姓名和 @example.invalid 邮箱，支付只用 sandbox，不能输入真实卡号/密码。
6. 正式 Q5/Q7 记录请按 docs/test/user-test-plan.md 和观察表执行，截图要匿名化。

报告/PPT：
https://github.com/muzaturezhang/SE/releases/tag/group6-stage4-engineering-final-2026-11-14
```