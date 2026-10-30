"""Regenerate editable SVG design overviews in this script's directory."""
from pathlib import Path
from html import escape

OUT = Path(__file__).resolve().parent
INK = '#26313a'
MUTED = '#596671'

class Diagram:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img">',
          '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#26313a" stroke-width="1.3"/></marker><marker id="triangle" markerWidth="13" markerHeight="13" refX="12" refY="6.5" orient="auto"><path d="M0,0 L12,6.5 L0,13 Z" fill="white" stroke="#26313a" stroke-width="1.3"/></marker></defs>',
          f'<rect width="{width}" height="{height}" fill="white"/>']
    def text(self,x,y,s,size=22,anchor='start',color=INK):
        self.parts.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" font-family="Arial, PingFang SC, sans-serif" font-size="{size}" fill="{color}">{escape(s)}</text>')
    def note(self,x,y,s,size=22):
        width=sum(1 if ord(c)>255 else .56 for c in s)*size+18
        self.parts.append(f'<rect x="{x-width/2}" y="{y-size}" width="{width}" height="{size+9}" fill="white"/>')
        self.text(x,y,s,size,'middle')
    def line(self, points, dashed=False, marker='arrow'):
        self.parts.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" fill="none" stroke="{INK}" stroke-width="2"'+(' stroke-dasharray="8 6"' if dashed else '')+(f' marker-end="url(#{marker})"' if marker else '')+'/>')
    def box(self,x,y,w,h,title,lines,stereo=None):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="2" fill="#f5f7f8" stroke="#88949e" stroke-width="1.5"/>')
        yy=y+(25 if stereo else 32)
        if stereo:
            self.text(x+w/2,yy,stereo,18,'middle',MUTED); yy+=27
        title_size=min(24, (w-24)/max(1,sum(1 if ord(c)>255 else .56 for c in title)))
        self.text(x+w/2,yy,title,title_size,'middle')
        yy+=14
        self.line([(x,yy),(x+w,yy)],marker=None)
        for s in lines:
            yy+=28; self.text(x+16,yy,s,21)
    def save(self,name):
        self.parts.append('</svg>'); (OUT/f'{name}.svg').write_text('\n'.join(self.parts))

# Domain associations refer to IDs; they are not database foreign keys.
d=Diagram(1200,790)
d.box(420,30,360,150,'RoomInventory',['-roomTypes: List<RoomType>','-rooms / blocks: Lists'],'record')
d.box(30,290,340,200,'RoomType',['-id: String','-name: String','-nightlyRateMinor: long'],'record')
d.box(430,290,340,200,'Room',['-id: String','-roomTypeId: String','-enabled: boolean'],'record')
d.box(830,290,340,170,'RoomBlock',['-roomId: String','-period: StayPeriod'],'record')
d.box(830,590,340,170,'StayPeriod',['-arrival / departure: LocalDate','+nights(): long','+overlaps(other): boolean'],'record')
d.line([(470,180),(200,180),(200,290)]);d.text(220,227,'0..* roomTypes',20)
d.line([(600,180),(600,290)]);d.text(620,240,'0..* rooms',20)
d.line([(730,180),(1000,180),(1000,290)]);d.text(1020,235,'0..* blocks',20)
d.line([(430,390),(370,390)]);d.text(400,267,'roomTypeId → 1',20,'middle')
d.line([(830,390),(770,390)]);d.text(800,527,'roomId → 1',20,'middle')
d.line([(1000,460),(1000,590)]);d.text(1020,545,'period 1',20)
d.text(40,630,'ID associations are resolved inside RoomInventory.',22)
d.text(40,675,'Key fields shown; full fields and invariants are in room-model.md.',20,color=MUTED)
d.text(40,715,'No Booking or Payment entity is implemented by this diagram.',20,color=MUTED)
d.save('room-domain')

d=Diagram(1200,770)
d.box(25,25,370,105,'RoomPageController',['GET /rooms'])
d.box(805,25,370,105,'RoomAdminController',['GET / POST room management'])
d.box(25,235,370,130,'RoomQueryService',['+listRoomTypes()','+search(query)'],'interface')
d.box(805,235,370,130,'RoomManagementService',['+list / get / create / update','+disable / updateRate'])
d.box(25,450,370,105,'DefaultRoomQueryService',['-repository / -clock'])
d.box(805,450,370,105,'RoomInventoryEditor',['+updateSnapshot(change)'],'interface')
d.box(415,650,370,105,'RoomRepository',['+readSnapshot()'],'interface')
d.box(805,650,370,105,'InMemoryRoomRepository',['volatile + synchronized'])
d.line([(210,130),(210,235)])
d.line([(990,130),(990,235)])
d.line([(850,130),(850,180),(350,180),(350,235)]);d.text(550,165,'catalog reads',19,'middle')
d.line([(210,450),(210,365)],dashed=True,marker='triangle')
d.line([(990,365),(990,450)])
d.line([(210,555),(210,700),(415,700)])
d.line([(805,505),(600,505),(600,650)],marker='triangle');d.text(620,540,'extends',19)
d.line([(990,650),(990,555)],dashed=True,marker='triangle')
d.text(35,610,'Clock injected',19,color=MUTED)
d.text(35,639,'into both services.',19,color=MUTED)
d.save('room-services')

d=Diagram(1200,1080)
d.text(300,38,'客户选房与预订预览',27,'middle');d.text(900,38,'账户与会员预览',27,'middle')
left=[(80,'首页',['/']),(270,'客房目录与查询',['/rooms · 详情为 dialog']),(460,'入住确认',['GET /preview/stays/new']),(650,'我的预订与详情',['/preview/stays · /{reference}']),(840,'支付展示',['/{reference}/payment · 按钮禁用'])]
right=[(80,'登录预览与注册入口',['/preview/account/login · /register']),(270,'注册表单结果',['/registration-result · 只保存展示资料']),(460,'个人中心',['/preview/account · 不授予身份']),(650,'会员状态',['/membership · 固定只读例子']),(840,'开通或续费展示',['/membership/join · 无付款 POST'])]
for y,t,ls in left:d.box(45,y,510,110,t,ls)
for y,t,ls in right:d.box(645,y,510,110,t,ls)
for a,b in [(190,270),(380,460),(570,650),(760,840)]:d.line([(300,a),(300,b)])
d.text(320,230,'选择日期与数量',21)
d.text(320,610,'POST + CSRF → UI-DRAFT',21)
d.text(320,810,'只读状态示例',21)
d.line([(900,190),(900,270)]);d.text(920,230,'注册提交',21)
d.line([(1155,325),(1180,325),(1180,135),(1155,135)]);d.text(1050,245,'返回登录',20)
d.line([(645,135),(600,135),(600,515),(645,515)]);d.note(600,425,'登录后展示',19)
d.line([(900,570),(900,650)]);d.line([(900,760),(900,840)])
d.line([(645,895),(620,895),(620,160),(645,160)],dashed=True);d.text(890,1004,'开通缺展示资料时经登录返回',21,'middle')
d.text(300,1005,'概览将列表与详情合框；完整跳转见路由表。',19,'middle',MUTED)
d.text(600,1050,'仅本地 UI 预览；不代表真实注册 预订 支付或会员激活。',22,'middle')
d.save('customer-navigation')

d=Diagram(1200,860)
d.box(420,35,360,110,'本地 STAFF 登录',['/staff-preview/login'])
d.box(40,280,480,110,'房间与房型',['/admin/rooms'])
d.box(680,280,480,110,'工作人员预订',['/admin/stays · 只读'])
d.box(40,530,480,180,'新增 编辑与基础价',['/new · /{roomId}/edit','/room-types/{typeId}/rate','POST + CSRF → PRG 返回'])
d.box(680,530,480,180,'预订详情',['/admin/stays/{id}','返回保留筛选条件','状态变更与核对按钮禁用'])
d.line([(600,145),(600,220),(280,220),(280,280)]);d.text(355,202,'成功固定到房间管理',21)
d.line([(520,320),(680,320)]);d.line([(680,355),(520,355)]);d.text(600,270,'后台导航',22,'middle')
d.line([(280,390),(280,530)]);d.text(300,470,'选择操作',21)
d.line([(920,390),(920,530)]);d.text(940,470,'查看详情',21)
d.line([(40,620),(15,620),(15,335),(40,335)],dashed=True)
d.line([(1160,620),(1185,620),(1185,335),(1160,335)],dashed=True)
d.text(600,770,'所有 /admin/** 强制 ROLE_STAFF；写请求与退出保留 CSRF。',23,'middle')
d.text(600,818,'POST /logout → /rooms；账户预览退出不等于退出 STAFF。',22,'middle',MUTED)
d.save('staff-navigation')

d=Diagram(1250,1160)
xs=[110,440,800,1140]
for x,name in zip(xs,['浏览器','RoomPageController','RoomQueryService','RoomRepository']):
 d.box(x-105,20,210,80,name,[])
 d.line([(x,100),(x,1060)],dashed=True,marker=None)
def msg(a,b,y,label,dashed=False):
 d.line([(xs[a],y),(xs[b],y)],dashed=dashed);d.text((xs[a]+xs[b])/2,y-13,label,21,'middle')
msg(0,1,150,'GET /rooms?条件')
d.note(440,200,'绑定输入与日期整数解析')
msg(1,2,255,'listRoomTypes()')
msg(2,3,305,'readSnapshot()')
msg(3,2,355,'目录快照',True)
msg(2,1,405,'房型目录',True)
d.note(625,454,'只有查询意图且绑定/房型检查通过才继续')
msg(1,2,510,'search(RoomSearchQuery)')
d.note(800,560,'校验日期 数量 Clock')
msg(2,3,620,'readSnapshot() 一次')
msg(3,2,675,'一致不可变快照',True)
d.note(800,740,'排除停用或重叠房间')
d.note(800,785,'按房型计数并过滤数量')
msg(2,1,850,'RoomSearchResult',True)
msg(1,0,930,'渲染 HTML 200 或空结果',True)
d.text(625,1100,'绑定或服务字段错误 → 可读 400；任何查询都不占用库存。',23,'middle')
d.save('availability-query')
print('Generated 5 SVG diagrams')
