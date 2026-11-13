# 2026-11-01 A 第一批成员与认证接入

用户 A 在 B 已下载的仓库（D:\Users\Huawei\Desktop\SE-project\SE）上继续开发，完成 A 负责的 Customer/Member/注册/登录/会员与共享安全整合的第一批实现。

## 事实

- 新增 `member` 领域与端口：Customer、Membership（PENDING/ACTIVE/EXPIRED 推导）、MemberRepository、MembershipRepository、StaffDirectory。
- 新增服务：RegistrationService（邮箱规范化、BCrypt、密码 8–72 且含字母数字）、MembershipService（plan/status/discount/request/confirm，会员基线 68000 分、5%、365 天）、ResortUserDetailsService（STAFF 目录优先，顾客按邮箱查，principal 为内部 ID）。
- 新增 `member/infrastructure` 三个内存适配，Profile 与 Room 适配一致：`!mysql & (dev | test)`。
- 新增 Web 层：MemberAccountController（/account/**）、MemberRegistrationForm、MemberUiException、MemberNavigationAdvice；模板 `member/login|register|profile|membership|join|error.html` 复用 B 的视觉系统，共享 header 增加账户入口与 memberNav 片段。
- 安全整合：SecurityConfiguration 主链加表单登录、角色感知跳转、/account/** 匿名跳登录、其余匿名 403；新增 StaffPreviewSecurityConfiguration(@Order(1), profile local-staff) 保持 /staff-preview/login 与 /admin/** 原行为；LocalStaffPreviewConfiguration 改为预置 StaffDirectory。
- 配置：MembershipProperties（app.membership.*）加入 application.yml；ResortBookingApplication 注册该属性类；新增 MemberConfiguration 提供 BCrypt PasswordEncoder。
- 文档：docs/requirements/member-account.md、docs/test/member-account-tests.md、docs/glossary.md 新增；docs/interfaces.md、docs/decisions.md、handoff/CURRENT.md 更新。
- 测试：RegistrationServiceTest、MembershipServiceTest、MemberAccountWebTest（对应 TC-MEM-001..005）。

## 验证

- Windows 本机 2026-11-01 22:53：`mvn --batch-mode --no-transfer-progress -DfailIfNoTests=true verify` BUILD SUCCESS；249 项测试，0 failures / 0 errors / 0 skipped（B 原 222 项保持通过，A 新增 27 项）。运行环境 JDK 24.0.1，编译与字节码目标 Java 17；Maven 3.9.11 为项目本地 `.cache/maven-dist`，SHA-256 与 wrapper 声明一致（0d7125e8…）。
- JAR 以 dev profile 在 127.0.0.1:8081 冒烟：`/`、`/account/login`、`/account/register` 200；匿名 `/account` 302 到登录页；匿名 `/admin/rooms` 403；注册→登录→个人中心（显示姓名/邮箱）→会员页（显示 CNY 680.00）全流程 200。临时进程已停止。
- 已知边界：本机是 JDK 24 而非 JDK 17，不能据此声称真实 JDK17 验收；Windows/MySQL、浏览器人工检查、200% 缩放/读屏、B/C/D 评审与联调仍未完成；mysql profile 在 D 提供适配前不启动（与 Room 现状一致）。

## 变更文件

- 新增：member 包 17 个源文件、6 个模板、3 个测试、3 个文档。
- 修改：SecurityConfiguration、LocalStaffPreviewConfiguration、ResortBookingApplication、application.yml、fragments/layout.html、README、interfaces.md、decisions.md、CURRENT.md。
