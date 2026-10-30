"""Build the shareable B design summary beside its Markdown/diagram sources.

Use the Python runtime provided by the workspace dependency loader. Regenerate
SVG/PNG files first; render and visually inspect the DOCX after any change.
"""
from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

OUT = Path(__file__).resolve().parent
DOC = Document()
sec = DOC.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.top_margin, sec.bottom_margin = Cm(1.8), Cm(1.7)
sec.left_margin, sec.right_margin = Cm(2), Cm(2)
sec.footer_distance = Cm(.8)
for name, size in [('Normal',10.5),('Title',24),('Subtitle',11),('Heading 1',17),('Heading 2',12),('Caption',9)]:
    s = DOC.styles[name]
    s.font.name = 'Arial Unicode MS'; s.font.size = Pt(size); s.font.color.rgb = RGBColor(0,0,0)
    s.element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'),'Arial Unicode MS')
    pf=s.paragraph_format; pf.space_after=Pt(7); pf.line_spacing=1.18
    if name.startswith('Heading'): pf.space_before=Pt(10)
# Remove inherited template decorations and theme-font overrides.
for style in DOC.styles:
    for border in list(style.element.iter(qn('w:pBdr'))): border.getparent().remove(border)
    for fonts in style.element.iter(qn('w:rFonts')):
        for attr in list(fonts.attrib):
            if 'theme' in attr.lower(): del fonts.attrib[attr]
for n in ['Title','Subtitle','Heading 1','Heading 2','Caption']:
    DOC.styles[n].font.italic=False
    pp=DOC.styles[n].element.find(qn('w:pPr'))
    if pp is not None:
        for element in list(pp):
            if element.tag == qn('w:numPr'): pp.remove(element)
DOC.styles['Normal'].paragraph_format.widow_control=True
DOC.core_properties.title='Holiday Resort B 设计与接口交接'
DOC.core_properties.subject='Room设计 页面导航 可用性查询与团队接入'
DOC.core_properties.author='Team member B'
DOC.core_properties.keywords='B-DESIGN-2026-10-29-01'
footer=sec.footer.paragraphs[0];footer.alignment=WD_ALIGN_PARAGRAPH.RIGHT
run=footer.add_run('B 设计交接  ·  ');run.font.size=Pt(8)
fld=OxmlElement('w:fldSimple');fld.set(qn('w:instr'),'PAGE');footer._p.append(fld)

def p(text,style=None):return DOC.add_paragraph(text,style)
def h(text):return DOC.add_heading(text,level=1)
def sub(text):return DOC.add_heading(text,level=2)
def page(title):DOC.add_page_break();h(title)
def table(headers, rows, widths):
    t=DOC.add_table(rows=1,cols=len(headers));t.alignment=WD_TABLE_ALIGNMENT.CENTER;t.autofit=False
    for col,w in zip(t.columns,widths): col.width=Cm(w)
    for cell,w in zip(t.rows[0].cells,widths):cell.width=Cm(w)
    for i,head in enumerate(headers):t.rows[0].cells[i].text=head
    for row in rows:
        for cell,text,w in zip(t.add_row().cells,row,widths):cell.width=Cm(w);cell.text=text
    for ri,row in enumerate(t.rows):
        pr=row._tr.get_or_add_trPr();cant=OxmlElement('w:cantSplit');pr.append(cant)
        if ri==0:repeat=OxmlElement('w:tblHeader');pr.append(repeat)
        for ci,cell in enumerate(row.cells):
            cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
            cp=cell._tc.get_or_add_tcPr()
            borders=OxmlElement('w:tcBorders')
            for side in ['top','left','bottom','right']:
                b=OxmlElement('w:'+side);b.set(qn('w:val'),'single');b.set(qn('w:sz'),'4');b.set(qn('w:color'),'D9D9D9');borders.append(b)
            cp.append(borders)
            margins=OxmlElement('w:tcMar')
            for side in ['top','left','bottom','right']:
                m=OxmlElement('w:'+side);m.set(qn('w:w'),'85');m.set(qn('w:type'),'dxa');margins.append(m)
            cp.append(margins)
            if ri==0:
                shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'E7EBEE');cp.append(shade)
            for para in cell.paragraphs:
                para.paragraph_format.space_after=Pt(2);para.paragraph_format.space_before=Pt(1);para.paragraph_format.line_spacing=1.12
                for run in para.runs:run.font.size=Pt(9.3);run.bold=ri==0
    p('')
    return t

def fig(name,width=16.6,caption=''):
    para=DOC.add_paragraph();para.alignment=WD_ALIGN_PARAGRAPH.CENTER
    shape=para.add_run().add_picture(str(OUT/(name+'.png')),width=Cm(width))
    shape._inline.docPr.set('descr',caption)
    if caption:p(caption,'Caption')

DOC.add_paragraph('Holiday Resort B 设计与接口交接','Title')
p('Room 类图  页面导航  查询交互  团队接入','Subtitle')
p('2026年10月29日  文档版本 B-DESIGN-2026-10-29-01')
p('供 A B C D 共同评审。B已完成Room查询、内存房间管理和五批范围内的页面开发。队友可以在各自业务包继续实现服务，由B接入现有模板，减少重复UI和同文件覆盖。')
p('这份材料记录当前代码结构与接入需求。Java签名、页面投影和状态仍待对应owner评审；文档版本号不是Git tag，也不是统一验收build。')
sub('当前完成范围')
table(['范围','本地实现','仍需接入'],[
['Room查询','房型 基础价 日期数量 连续可用性','C/D事务复核与真实库存'],
['Room管理','STAFF新增 编辑 停用 重新启用 基础价','A正式身份 D数据库与跨进程锁'],
['客户预订和支付UI','确认 会话草稿 列表 详情 支付状态','C创建 计价 幂等 支付与归属'],
['账户会员UI','注册/登录后展示 个人中心 会员 开通续费','A真实账号/会员 C会员付款'],
['工作人员预订UI','只读列表/筛选/详情 后台导航','A/C权限 数据范围 状态与分页']],[3.2,6.9,6.9])
sub('责任与使用顺序')
p('A负责账号、身份、会员及共享集成；B负责Room与全系统UI；C负责Booking、Payment和最终计价；D负责持久化、通知与测试基础设施。共享安全配置和依赖由A协调，变更需同步受影响owner。')
p('先读第2至6页核对设计，再按第7页讨论接入问题。第8页映射UI原则，第9页列出验证范围与后续验收。工程中的完整CRC、路由与接口细节可通过docs/design/README.md查阅。')
p('最近一次应用验证：2026-10-29 20:47:16 +08:00，222项测试通过；运行Oracle JDK23，编译目标Java17。本文整理未改应用功能，也未新增真实业务验收。')

page('Room 领域对象与约束')
p('图中列关键字段。Room与RoomType、RoomBlock与Room通过字符串ID关联，未假定已有数据库外键或对象导航字段。')
fig('room-domain',16.8,'图1  当前Room领域关系  完整字段见room-model.md')
table(['对象','规则'],[
['Room / RoomType','enabled只是管理开关；基础价为正CNY整数分/间/晚。'],
['StayPeriod','日期非空且退房晚于入住；半开区间，退房当天可接下一笔入住。'],
['RoomBlock','只表示不可用输入，没有Booking/Payment状态。'],
['RoomInventory','列表不可变；房型ID/房号唯一；房间和占用引用必须存在。']],[4,13])
p('重叠判定为 arrival < other.departure 且 other.arrival < departure。过去入住限制属于客户查询Service，不属于通用StayPeriod构造器；工作人员历史查询允许过去日期。')

page('Room 服务依赖与职责')
fig('room-services',16.8,'图2  核心依赖子集  空心三角为继承或实现  虚线实现接口')
table(['类型','主要责任'],[
['RoomPageController','绑定输入与HTTP反馈，调用RoomQueryService，构建卡片模型。'],
['DefaultRoomQueryService','注入Clock，校验查询，读取一致快照，计算整段连续可用数。'],
['RoomManagementService','校验房号/房型、占用保护、精确基础价，在原子回调内更新。'],
['RoomRepository / Editor','只读快照边界；Editor增加原子校验与替换回调。'],
['InMemoryRoomRepository','开发fixture、不可变快照、volatile发布与synchronized更新。']],[6.1,10.9])
p('完整Room CRC覆盖24个顶层类型，另列私有fixture类型和UI协作者，见room-crc.md。Controller不访问其他模块表；RoomManagementService是具体类，当前没有同名接口。')
p('单进程同步不能替代MySQL事务或分布式锁。整份快照写端口便于本地开发；D接数据库前须与B/C评审，分配和释放库存的Java端口目前尚不存在。')

page('客户页面导航')
fig('customer-navigation',16.4,'图3  客户导航概览  同框可含多个页面  完整路由见navigation.md')
p('booking-ui和account-ui分别控制演示入口。所有草稿/会员/支付状态均为本地展示；注册结果不自动登录，登录预览不认证，也不授予CUSTOMER或STAFF角色。')
p('会员开通或续费缺展示资料时，先到登录预览，再按白名单next返回原页面。账户预览退出只清展示资料；它不会结束真正STAFF登录或删除Stay草稿。')
p('正式客户入口须由A提供认证主体，C在Service验证记录归属。当前匿名preview入口不可直接接真实账号或订单；生产URL与错误映射仍待评审。')

page('工作人员页面导航')
fig('staff-navigation',16.8,'图4  工作人员导航  预订入口由staff-booking-ui显式启用')
table(['路由组','行为与边界'],[
['/admin/rooms','列表筛选；/new新增；/{roomId}/edit编辑或停用。POST带CSRF，成功PRG。'],
['/admin/room-types/{typeId}/rate','GET表单与POST更新房型基础价；精确到分，不静默舍入。'],
['/admin/stays 及 /{id}','编号/入住日/状态交集筛选；详情返回保留条件；当前只读。'],
['错误与退出','字段非法400、冲突409、缺记录404；POST /logout结束本地STAFF后到/rooms。']],[6.2,10.8])
p('所有/admin/**请求均在后端要求ROLE_STAFF。默认匿名403；可选local-staff匿名转登录，认证成功固定到房间管理。客户账户展示不能取得管理权限。')
p('有退房日晚于今天的有效Block时，Room拒绝停用或换型。预订状态变更和支付核对按钮仍禁用，没有收款、取消/改期或库存释放接口。')

page('可用性查询交互')
fig('availability-query',16.5,'图5  已实现的只读查询顺序  绑定错误与无日期目录分支见正文')
p('Controller先读取目录；只有存在日期/数量参数且绑定成功才调用search。Service校验后使用单次一致快照，排除停用或任意重叠的整间房，再按房型计数。无结果为HTTP200空状态；字段错误为可读400。')
p('连续可用反例：A仅第一晚空、B仅第二晚空，每天各有1间也不能保证同一间连住两晚。搜索结果不占库存；真实创建必须由C/B/D在同一事务内重新复核并分配。目录读取与search不构成整个HTTP请求的一致事务。')

page('接口评审与联调顺序')
p('当前接口是可定位的开发契约。请按编号给出同意或修改意见，记录真正review结果后再冻结；本材料未代填队友已同意。')
table(['编号','共同owner','需要确定'],[
['R01','A B','主体类型/ID、真实登录退出、密码策略与客户路由。'],
['R02','B C D','Room/RoomType标识与关系、固定房号和停用语义。'],
['R03','B C D','分配/释放命令、共同事务/锁顺序、幂等与回滚。'],
['R04','A B C','会员资格/费率/有效期、报价与成交快照、字段错误。'],
['R05','A B C','本人/员工可见范围、独立预订/支付状态、房号和分页。'],
['R06','A B C D','Q8取消/改期/提醒的实际实现范围。'],
['R07','全组','Git基线、集成build、验收矩阵与同版本证据。']],[1.4,2.6,13])
sub('接入时各模块提供什么')
p('A提供真实账号、CUSTOMER/STAFF身份、本人资料和会员结果。C提供报价、创建/幂等、本人和员工预订查询、价格快照与支付核对。D提供数据库适配、schema/迁移、唯一约束、事务/锁和有效Block映射。B负责映射页面模型、错误和真实路由。')
p('RoomQueryService只读；RoomManagementService只处理Room管理。StayPageGateway和StaffStayPageGateway是页面投影；AccountPageGateway.preview方法专用于演示，不能作为生产认证接口。完整签名与空值/错误语义见docs/interfaces.md。')
sub('建议依赖顺序')
p('先确认必要接口并建立Git基线 → 接A身份与后端权限 → B/C/D验证Booking与库存事务 → 接会员报价与支付核对 → 目标环境回归并采集统一build证据。每个箭头都是未来联调工作，当前没有声称通过。')
p('不能用Controller中的读后写代替事务，不能把UI展示枚举当共享状态机，也不能让匿名preview路由接真实数据。共享配置、端口和状态变更要说明owner、原因、影响及需更新的测试。')

page('页面设计原则与包容性')
p('下面按页面族汇总已定位的设计。逐页模板、用户收益和待验证项见ui-principles.md；采用设计原则不等于已完成真实可用性或无障碍认证。')
table(['页面','设计细节与预期收益'],[
['首页与客房目录','明确搜索入口、房型摄影/名称、CNY每间每晚基础价，帮助比较居所。'],
['客房查询与dialog','标签/回填/空状态/可读错误；原生详情dialog减少离开查询的操作。'],
['房间列表与编辑','当前导航、房号/状态文字、局部表格滚动、固定房号与停用限制。'],
['基础价与员工登录','明确CNY元/精度，后端拒绝非法金额；区分本地STAFF身份用途。'],
['入住确认与预订记录','分段表单和住期摘要、编号/状态、列表空结果，便于核对和恢复。'],
['支付展示','成功/失败/核对/过期文字；禁用未接入操作，解释结果不明的处理边界。'],
['登录 注册与资料','真实标签/错误、密码不回显、白名单返回、资料核对和预览退出说明。'],
['会员与开通续费','4状态文字、空费用待报价、不同入口；不把未知费用展示为免费。'],
['员工预订列表与详情','组合筛选、返回保留条件、入住/联系/费用分组、预订与支付双状态。'],
['各类错误页','role=alert、可读原因、返回/重置、动态文本转义，帮助安全恢复任务。']],[4.5,12.5])
sub('平等 多样性与包容性')
p('现有公共布局包含中文lang、跳转主要内容、真实label、文字状态与可见键盘焦点；CSS支持减少动态效果，图片有alt，主要页面已做窄屏开发检查。中文和英文辅助标题不等于双语本地化。')
p('仍需实际验证对比度、200%缩放、完整键盘顺序/焦点归还、屏幕阅读器、触屏目标与不同输入方式。没有真实用户访谈、样本数或满意度结论。姓名不限制拉丁字母，但正式账户输入策略需A确认。')

page('验证证据与接入前检查')
sub('已经发生的开发验证')
p('2026-10-29 20:47:16 +08:00执行Maven verify，222项，0失败/错误/跳过；环境macOS arm64、Oracle JDK23、Java17源码/字节码目标。第五批做过1440/390/320 CSS像素的浏览器开发检查，截图位于docs/previews。')
p('本轮只整理材料，应用源码和数据未改动，未重跑应用测试。没有真实JDK17、Windows/MySQL、正式身份、真实支付、CI/覆盖率或真实用户研究的完成记录。当前仓库无提交、远程和tag。')
sub('原型方式与替代')
p('当前可运行Thymeleaf页面配本地fixture，能验证路由、服务端校验、CSRF与Session边界，代码可继续用于集成；代价是评审依赖本机服务。Figma可点击原型可用于布局变体与队友批注，更容易分享，但不能证明授权、库存并发或支付正确性；这里没有声称实际制作或测试过Figma版本。')
sub('之后需要真实执行的验收')
table(['范围','接入后的验收条件'],[
['身份与数据归属','匿名/角色拒绝、跨用户读记录失败、CSRF、正式登录与退出。'],
['Booking和Room事务','两笔争用同库存仅一笔成功；停用与创建一致；异常回滚无残留占用。'],
['金额与支付','服务端报价/快照；改基础价不改已成交价；幂等、核对、不重复扣款或提前释放。'],
['持久化与最终证据','目标JDK/OS/MySQL、重启数据、统一build、四核心功能截图和真实用户记录。']],[4.4,12.6])
sub('维护与定位')
p('设计入口 docs/design/README.md；完整Room CRC room-crc.md；路由 navigation.md；查询 availability-interaction.md；逐页原则 ui-principles.md。接口登记 docs/interfaces.md；队友接入说明 handoff/B-team-integration.md；运行/历史结果 handoff/CURRENT.md与log。')
p('图保留Mermaid源、SVG与PNG。生成脚本按脚本目录输出；Word更新后重新渲染并逐页检查。最终RS/DS/TR、Q3/Q4和用户研究需团队按真实基线完成，本材料仅支持评审和接入。')

path=OUT/'B_Design_and_Integration_Handoff.docx'
DOC.save(path)
print(path)
