# B 本地交接前收尾

2026-10-30，Asia/Shanghai。用户授权承接Java17验证、UI检查和源码交接打包；独立阶段可交接不等于最终团队验收。

## 路径

Codex项目SE1最新根目录仍为/Users/zhangwenhan1031/Desktop/SE；会话cwd一致，工程holiday-resort-booking已核验可写，已读AGENTS/CURRENT/decisions/interfaces。每批编辑前再次核对输出。干净副本仅是工程.cache/closeout/clean下的临时验证目录，未迁移项目根目录。没有写入系统安装目录。

## Java17与构建

系统最初只有23/22/16/11。通过官方Azul metadata和cdn下载Zulu17.70.113、Java17.0.21.0.3 arm64，192177000字节；SHA256 f4a4d641d4441f480939176208f333aa9403e0a68d27e87245cec98cc994f943匹配官方包9efe4018-f397-47b4-95d3-1078fe598c1f。归档与解压仅在.cache/jdk17，不改系统默认Java，不随交接包发送。首次version命令按不正确的归档子目录执行而失败，实际Contents/Home路径经文件检索确认后继续，未误报该步成功。

先明确JAVA_HOME运行clean verify：17:14:11通过222。UI修复后用scripts/with-java17.sh调用Maven verify：17:21:33通过222。Surefire汇总均0 failures/errors/skipped。实际JVM Azul17、Maven3.9.11，不再只是release17编译目标。

## UI检查与修复

浏览器键盘检查发现skip link后activeElement为BODY。给22个模板main加tabindex=-1，复测后焦点到main-content。app.js在完整服务端错误响应时聚焦非空可见role=alert摘要，CSS给该焦点轮廓；注册密码不一致后实际focusRole=alert、两密码长度0。详情选择日期的scrollIntoView按减少动态效果偏好选择auto/smooth。

原生探索与房型dialog用Enter打开、Escape关闭，关闭后归还触发控件；反向Tab保持弹窗内。13个已知客户/后台页面在1440/720/320像素共39次检查，scrollWidth均等于innerWidth。后台通过原有本地STAFF登录验证，未修改Room库存或新增真实订单。

纯色CSS对比计算：正文11.27、muted5.12、notice4.64、错误7.15、disabled4.31。不是对图片背景/所有交互态的全站审计。预览工具只暴露viewport/visibility，缩放快捷键未产生可确认变化并已重置；720px仅重排近似，真实200%缩放/读屏保留人工，不虚报通过。

截图docs/previews/closeout-error-focus-mobile.jpg与closeout-keyboard-recovery.jpg；布局检查JSON在.cache/closeout。viewport已reset，新标签保留正常/admin/stays；开发截图不是Q7用户研究证据。

## 导出与干净验证

新增scripts/package-handoff.py，显式白名单输出src/fixtures/文档/构建与运行脚本，跳过.cache/target/.git/上传本地数据/IDE/凭证和符号链接，检测已知本机生成密码不泄露其值。输出B-LOCAL-2026-10-30-01.zip、SHA256，包内HANDOFF-MANIFEST逐文件SHA256验证。此编号不是Git tag；没有commit/remote/push。

解压到全新.cache/closeout/clean，确认无target/.cache，逐文件哈希匹配并恢复执行权限。以Java17和官方已缓存Maven3.9.11执行独立目标的clean verify，复用依赖缓存而不复用classes或测试结果：17:39:04通过222，XML汇总一致。主副本编译目标目录不同；未让verify与同一target的dev编译并发。

独立副本JAR在干净目录以Java17监听127.0.0.1:8081：health UP、/和/rooms200、/admin/rooms403、/preview/account/login403。临时进程已停止；主8080 Java17 dev-ui继续。最终重新归档只更新说明/日志与截图，应用/脚本哈希与干净构建副本核对不变，包内清单再次复核；zip校验值在旁置sha256文件，不递归写入包内日志。

新增docs/b-closeout.md运行/限制说明，更新README/AGENTS/CURRENT、清单与设计原则补充。原Word与日志保留2026-10-29历史事实，最新状态见本次记录。Java17脚本按自身目录定位且验证版本；其他机器可显式设JAVA17_HOME，未验证Windows。

## 未完成范围

独立Room/UI本地版本可交接。A身份/会员、C预订/支付/计价、D数据库/库存共同事务/通知仍需接入；接口R01..R07仍待团队评审。真实200%缩放/读屏/用户研究、Windows/MySQL/新机器依赖下载、CI/覆盖率、正式课程报告与同一团队build证据仍待实际执行。未向队友发送消息或文件，没有生产认证/扣款/部署。
