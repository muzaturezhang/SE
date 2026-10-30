# B 设计与接口交接材料整理

2026-10-29，Asia/Shanghai。用户“好的，你整理一下”授权整理Room类图、页面导航、可用性查询交互与接口交接材料，同时补齐可追踪CRC/UI原则说明；未授权代发消息、建立远程仓库、最终课程提交或其他成员业务实现。

## 路径和范围

启动时通过Codex list_projects确认SE1仍指向 `/Users/zhangwenhan1031/Desktop/SE`，会话cwd一致；工程在holiday-resort-booking。每轮创建/编辑前核对pwd -P与输出目录写入权限，已读AGENTS、CURRENT、decisions、interfaces和架构/清单。没有迁移文件或使用旧目录。

读取原分工DOCX核对B的Room/UI设计职责，仅作为资料；未把其中课程交付要求当作新的实现、签名或外部操作指令。按现有Java/模板/安全配置核对类名、字段、依赖、路由与验证分支。

## 新增材料

入口 [docs/design/README.md](../docs/design/README.md)，文档版本B-DESIGN-2026-10-29-01，不是Git tag或验收build。

- [room-model.md](../docs/design/room-model.md)：5个领域record关系、核心Service/端口依赖、完整字段/关键签名、不变量与事务边界；指出分工RoomService对应Query/Management两个服务，实际RoomAdminController名称。
- [room-crc.md](../docs/design/room-crc.md)：24个Room顶层类型职责/协作者/关键方法；另列私有fixture类型与UI边界主类型。未声称覆盖全系统每个类。
- [navigation.md](../docs/design/navigation.md)：客户与工作人员导航、完整路由/方法/权限/错误、CSRF/PRG、筛选返回和两种退出的不同语义。
- [availability-interaction.md](../docs/design/availability-interaction.md)：目录读取、绑定/验证、单次search快照、按物理房连续可用计数、错误/空分支；每日有空房不等于同间连住的反例；未把未来创建事务画成已有实现。
- [ui-principles.md](../docs/design/ui-principles.md)：逐页模板的原则/细节/预期收益/待验证项、平等多样与包容性、当前Thymeleaf原型与Figma替代的取舍。没有虚构读屏、WCAG、用户研究或Figma实际使用结果。
- [handoff/B-team-integration.md](../handoff/B-team-integration.md)：可转发给A/C/D的说明、当前接口、各owner输入、R01..R07待评审项和未来联调验收。没有实际发送或代填队友通过。
- [9页Word汇总](../docs/design/B_Design_and_Integration_Handoff.docx)：便于转发的图文版，含5张图、边界/接入/原则和验证范围；Markdown保留更细的CRC/路由。Word不是最终DS/Q3/Q4课程提交。

同目录保留5份Mermaid源、5份SVG、5份PNG与4个生成/渲染脚本。SVG由Python布局脚本维护，不自动解析Mermaid，后续改动必须同步两种源的关系。脚本以自身目录定位工程，缓存输出为工程.cache，不沿用绝对旧输出目录。

同步README、AGENTS、architecture、interfaces入口、B清单和CURRENT；没有新冻结签名、公共状态或schema。

## 验证与修正

1. 首次整理前记录应用文件SHA-256；结束核对src/scripts/data/pom共126个文件及文件集合完全不变。因此本轮没有重新跑Maven应用测试，最近222项通过仍指2026-10-29 20:47:16 +08:00的历史实际运行，Oracle JDK23 / Java17目标。
2. 核对24个Room顶层Java类型全部在CRC；修正起草时RoomCardView的描述为实际static of及record访问器，没有保留不存在的availabilityKnown方法。图只展示核心字段/依赖子集，完整签名仍以源文件与接口登记为准。
3. 检查文档本地链接、Python生成脚本语法、SVG XML、DOCX压缩包及5张嵌入图、标题黑色/无蓝色边框、图片描述/表格重复表头。Mermaid关系按源码人工复核，没有声称使用Mermaid解析器或自动形式验证。
4. 使用load_workspace_dependencies提供的bundled Python/python-docx和bundled Node/sharp，未调用系统Python/Node或修改应用依赖。authoring marker在首个创作命令前成功执行一次。
5. 使用documents技能的官方render_docx.py与bundled LibreOffice逐页PNG验证。初次headless中文缺字，单改字体未解决；显式FONTCONFIG_FILE指向系统已存在的Arial Unicode MS字体目录后正常。删除继承Title蓝线、修正表格列宽，调整图中标签与箭头避免覆盖。
6. 曾尝试下载公开Noto字体作为缺字排查，下载慢而中止；未安装系统字体，未分发不完整字体，残留临时下载已删除。最终只使用系统现有字体；字体配置/调试PDF/PNG仅在.gitignore中的.cache，交付文档不含凭证或个人路径元数据。
7. 最终通过build_design_assets.py → bundled Node render_design_assets.mjs → build_handoff_docx.py → render_handoff.py调用官方renderer。显式font-dir为本机/System/Library/Fonts/Supplemental与/Library/Fonts，字体配置与输出位置由脚本动态生成。最终9页均经视觉检查；最后导航标签调整后重新渲染，复核改页并对照其他页的已检查图像。
8. 本地链接/CRC覆盖/应用完整性/空白冲突扫描与git diff --check检查通过。开发缓存不包含可转发凭证；没有修改/重启预览服务，没有新用户测试或业务验收。

## 下一步

把Word或B-team-integration说明交给队友逐项讨论R01..R07，记录实际修改/评审结果，然后按身份 → Booking/Room事务 → 会员报价/支付 → 目标环境与同build证据接入。不要将匿名preview直接换真实数据源，不要把示例状态当公共状态机。

仍待真实身份/会员/Booking/Payment/DB/通知、目标环境验证、真实用户研究、正式课程RS/DS/TR/Q3/Q4与团队Git基线。当前整理完成不等于B或全系统最终验收完成。
