# 2026-10-29 D 通知与持久化基础

## 授权与范围

用户明确表示负责 D 的工作并提供团队仓库。本轮只实现 D 自有、且不依赖未冻结跨模块 schema 的基础：一天前提醒、提醒调度表、JDBC 适配器、邮件抽象、Mock 适配器、可选调度器和 JaCoCo。没有修改 A/B/C 的业务规则，没有创建 Booking/Payment/Room/Member 表。

## 变更

- `pom.xml`：增加 Flyway core/MySQL 模块、JaCoCo 0.8.16、verify 报告和 BUNDLE 行覆盖率 70% 门槛。
- `src/main/resources/db/migration/V1__create_reminder_schedule.sql`：唯一键、到期索引、版本号和通知状态字段。
- `src/main/java/com/holidayresort/booking/notification/`：提醒领域对象、仓储端口、服务、JDBC 适配器、邮件端口、Mock adapter、配置和调度器。
- `src/test/java/com/holidayresort/booking/notification/`：提醒服务 5 项测试、JDBC 仓储 3 项测试。
- `src/main/resources/application.yml`：通知配置，默认关闭定时派发。
- `docs/interfaces.md`、`docs/decisions.md`、`docs/d-persistence-notification-foundation.md`、`handoff/CURRENT.md`：记录草案契约、决策、验证与限制。

## 验证

环境：Windows、Temurin JDK 17.0.20.1、Maven 3.9.11。

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\maven.ps1 --batch-mode --no-transfer-progress verify
```

结果：BUILD SUCCESS；230 tests，0 failures，0 errors，0 skipped；Flyway 在 H2 测试库成功应用 V1；JaCoCo 总行覆盖率 95.05%，通知包 86.72%，70% 门槛通过。报告：`target/site/jacoco/index.html`。

本机项目路径包含非 ASCII 字符，JaCoCo destFile 使用 `${java.io.tmpdir}/${project.artifactId}-${project.version}-jacoco.exec`，避免 agent 写入中文路径失败；报告仍输出到 `target/site/jacoco/`。

## 限制与未完成

- 未在 MySQL 8 实例验证；当前只有 H2 自动测试和 MySQL 兼容 SQL 的静态设计。
- 未接入真实邮件 provider，`live-email` profile 当前没有 adapter。
- 未实现 Booking 创建成功、取消或改期事件触发提醒。
- 未实现 Q8 CR3 三天前提醒。
- 进程在认领后崩溃可能留下 `SENDING`；尚未实现租约恢复。
- 采用至少一次投递语义；邮件成功后本地状态更新失败可能重复发送，provider 幂等未验证。
- 没有用户测试、系统截图、正式 Test Report 或最终验收证据。
