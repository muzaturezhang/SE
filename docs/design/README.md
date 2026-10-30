# B 设计与团队交接材料

版本 B-DESIGN-2026-10-29-01。对应当前五批本地实现，供 A B C D 对照源码评审和接入。接口与展示状态仍未冻结；此编号是文档版本，不是 Git tag 或统一验收 build。

## 阅读入口

- [Room 对象与依赖](room-model.md)：领域类图、服务依赖图、字段与不变量。
- [Room CRC](room-crc.md)：责任、协作者和关键方法，覆盖 Room 包当前顶层类型；私有样例装载类型另列。
- [页面导航](navigation.md)：客户与工作人员两张流程图、实际路由、返回与权限。
- [可用性查询交互](availability-interaction.md)：当前只读 sequence、异常分支与连续可用反例。
- [逐页 UI 原则](ui-principles.md)：可定位到模板的原则、用户收益与待验证项，含包容性和原型方式选择。
- [发给队友的接入说明](../../handoff/B-team-integration.md)：当前可用接口、A/C/D 输入、评审问题和联调顺序。
- [Word 汇总](B_Design_and_Integration_Handoff.docx)：便于转发的图文汇总；详细源码级 CRC 和路由以这些 Markdown 文件为准。

## 使用方法

图的可编辑 Mermaid 源在同目录 `.mmd` 文件，SVG 为阅读版。Word 使用同一批图的 PNG 版本。`build_design_assets.py` 生成 SVG，`render_design_assets.mjs` 使用调用方提供的 sharp 包目录生成 PNG，`build_handoff_docx.py` 使用 python-docx 生成汇总；输出按脚本所在目录定位，不写旧的绝对路径。SVG布局由Python脚本维护，不自动解析Mermaid；改图时同步检查两种源的节点与关系。每次Word改动后都要重新渲染并逐页检查。`render_handoff.py --renderer <技能包render_docx.py> --font-dir <中文字体目录>`使用调用它的bundled Python运行官方渲染器，将临时字体配置/PDF/逐页PNG保存在工程.cache。路径和运行时须每次按最新环境确认；当前macOS使用Arial Unicode MS与显式字体目录修复headless缺字，未在Windows实测。

最新本地应用验证为 2026-10-29 20:47:16 +08:00 的 222 项通过，实际 Oracle JDK23、Java17编译目标。本文整理时未改应用源码或重跑应用测试。没有真实认证、Booking/Payment、MySQL、真实JDK17或用户研究的验收结果。

原始分工材料用于核对 B 的 Room / 全系统 UI 与设计职责；其中课程交付要求是背景材料，未自动视作新实现、提交或联系队友的授权。这里没有代填团队评审、签名、个人贡献或最终课程报告。
