# 2026年10月27日 工程骨架

## 任务与范围

用户是团队 B。团队已同意启动方案，用户要求在 SE 目录下建立独立项目目录，集中放代码、数据和相关文件，并将进度记在 log/handoff。

本次只建立公共基础和模块位置，不实现其他成员的业务规则。用户询问 Java 17 的原因后要求继续，故保留已确认的 Java 17 编译目标，使用本机 JDK 23 构建，没有安装或替换本机 JDK。

## 实际产出

- 建立 `holiday-resort-booking/` 和本地 Git main 分支；未创建提交或配置远程。
- `pom.xml` 固定 Spring Boot 3.5.16、Java 17 目标，加入 Web、Thymeleaf、Validation、Security、JDBC、Actuator、H2/MySQL 和测试依赖。
- 官方 Maven Wrapper 3.3.4 only-script；Maven 3.9.11 分发包已核对 SHA-512，并在 Wrapper 中配置 SHA-256 校验。
- `scripts/` 提供开发、验证和项目内缓存入口，附 Windows PowerShell 包装入口。
- 建立各成员的包职责位置、共享 Clock/配置、安全默认策略、首页和 Room 占位页面。
- Bootstrap 5.3.8 静态资源本地保存，保留 MIT 许可证。
- `data/` 预留 fixtures、local、uploads；数据库迁移目录仅有说明，没有虚构 schema。
- 建立 README、AGENTS、docs、log、handoff，使未来可从文件恢复进度。

## 检查与结果

环境：macOS 14.5 arm64，Oracle JDK 23，Maven 3.9.11。Spring Boot 补丁版本从 Maven Central 3.5 系列中选定并固定。

1. 第一次 `verify`：4 项测试中 1 项失败，原因是 CSS 注释的空格与测试中的精确文本不符。改为验证静态资源成功返回且 Content-Type 为 CSS；其余行为断言保留。
2. 再执行 `./scripts/maven.sh --batch-mode --no-transfer-progress verify`：BUILD SUCCESS，4 项测试全部通过，无错误或跳过，并生成可运行 JAR。
3. 基础测试覆盖：两页真实模板渲染、共享资源、health 不暴露详情、管理接口默认拒绝、CSRF 拒绝无 token 写请求、独立 H2 test 连接。
4. `javap -verbose`：应用类 major version 61，验证 Java 17 字节码目标。
5. 实际执行 JAR 并使用随机端口，GET 首页、Room 页、CSS、Bootstrap CSS/JS 和 health 均为 200；health 为 UP；GET `/admin/rooms` 为 403。检查后停止进程。
6. Shell 语法和 Git 忽略规则通过检查。构建缓存、target、本地数据和上传文件不进入版本控制。

构建生成物在 `target/`。Surefire 报告在 `target/surefire-reports/`；这些为可再生成的忽略文件。没有将本次骨架测试冒充业务模块或课程最终测试结果。

## 限制与下一步

真正 JDK 17、Windows 入口、MySQL 和浏览器视觉布局尚未实测。无登录、支付、邮件、Room 业务或业务持久化，也没有 CI 和覆盖率配置。

下一批从 Room/RoomType 与 B/C/D 接口确认开始，再实现 B 的 domain、Mock Repository、搜索及管理页面。最新状态统一维护在 `handoff/CURRENT.md`。
