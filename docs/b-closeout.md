# B 本地交接前检查

版本 B-LOCAL-2026-10-30-01，Asia/Shanghai。适用范围是B已实现的Room与UI本地版本；这是交接包编号，不是Git tag或团队最终发布。

## 当前结论

Room查询/管理、主要客户与工作人员页面、设计/接口说明已进入可交接阶段。真实账号/会员、Booking/Payment、数据库/库存事务仍需A/C/D接入；最终课程证据与真实用户验收仍待执行。

## Java 17

本机使用工程缓存中的Azul Zulu 17.0.21.0.3 arm64，源码与运行均为Java17，Maven3.9.11。官方API包ID为9efe4018-f397-47b4-95d3-1078fe598c1f，下载来自cdn.azul.com；归档SHA-256经官方metadata核对：

`f4a4d641d4441f480939176208f333aa9403e0a68d27e87245cec98cc994f943`

2026-10-30先执行clean verify；UI修复后再次verify均通过222项，0失败/错误/跳过。最终源工程verify完成于17:21:33 +08:00。仅修改本地调用环境，没有改系统默认Java，JDK不随交接包分发。

```sh
# 已有JDK17时可指定，不下载或安装系统软件
JAVA17_HOME=/your/jdk17 ./scripts/with-java17.sh ./scripts/test.sh
JAVA17_HOME=/your/jdk17 ./scripts/with-java17.sh ./scripts/dev-ui.sh
```

脚本会验证实际运行版本。当前开发机还支持已校验的工程本地JDK缓存；macOS可寻找已安装JDK17。没有合适版本时明确报错。其他环境也可自行设置JAVA_HOME后用原有Maven入口。Windows脚本本轮未实测。

## UI 开发检查

- 键盘：Tab可见焦点；探索/房型dialog支持Enter进入、Escape关闭及归还触发位置；检查了弹窗内反向Tab范围。
- 修复：22个main增加tabindex=-1，跳转主要内容后实际焦点到main-content；服务端返回的非空错误摘要自动获取焦点，摘要有可见轮廓；详情安排日期滚动尊重减少动态效果偏好。
- 表单：演示注册两次密码不一致时回到错误摘要，两框均清空；错误文案可见，没有记录密码值。
- 布局：8个客户页面、5个工作人员页面，分别检查1440/720/320 CSS像素共39次；全部scrollWidth等于innerWidth。Room列表表格仍允许局部滚动。
- 对比度：按当前CSS纯色计算，正文11.27、次要文字5.12、提示文字4.64、错误文字7.15；禁用控件样例4.31。不是全站图片背景/所有交互态的无障碍认证。
- 工具不提供真实页面缩放控制，快捷键没有产生可确认缩放变化；720px只作为宽1440时缩放后的重排近似检查。真实200%缩放、屏幕阅读器和用户研究保留人工复核，不标记已通过。

截图在docs/previews/closeout-error-focus-mobile.jpg和closeout-keyboard-recovery.jpg；原始布局检查JSON仅保存在忽略的.cache/closeout中。此记录是开发检查，不代表正式Q7用户测试。

## 交接包与运行

```sh
python3 scripts/package-handoff.py --release B-LOCAL-2026-10-30-01
```

输出在deliverables：源码ZIP、ZIP的SHA-256。包内HANDOFF-MANIFEST.json为每个文件记录SHA-256；导出后逐项复核。按显式目录白名单导出src、fixtures、文档、handoff/log、构建与运行脚本，排除.cache/target/.git/本地数据/上传/IDE与凭证文件；对本机已生成密码进行不泄露值的检测。

解压后先读README.md与handoff/B-team-integration.md。安装JDK17，普通浏览使用scripts/dev.sh，本机完整演示使用scripts/dev-ui.sh；首次构建需联网下载Maven依赖。员工预览凭证由接收者本机生成，不共享发送者密码。没有真实订单、支付或会员激活。

干净解压构建/启动的实际结果见log/2026-10-30-b-closeout.md；验证会复用本机Maven依赖缓存，不能标记为新机器、离线首次下载或Windows验证。设计Word日期为2026-10-29，其JDK23说明是当时的历史结果；最新状态以本文和CURRENT为准。
