# 2026-11-07：D 通知与持久化完整实现

用户依据新版 RS/DS 要求完成 D 工作。本轮只修改 D 责任范围，并为 B 的既有 Room 端口补充持久化适配和冲突映射；未代写 A/C 领域服务。

## 变更

- 新增 Flyway V2–V7：Room、Member、Booking、Payment、提醒扩展/审计、固定锁表、初始房型目录。
- 扩展提醒请求与持久化模型：退房日期、房型、09:00 计划发送时间、取消状态、改期 reschedule、审计事件。
- 邮件失败只保存/记录安全错误类别；新增可选 SMTP adapter，默认仍为 Mock。
- 新增 `JdbcRoomInventoryRepository`、`JdbcPersistenceLockService`、`RoomInventoryConflictException` 与 B 的 409 映射。
- 新增 schema 契约、Room JDBC、锁、提醒服务与提醒 JDBC 测试。
- 新增 `.github/workflows/ci.yml`、`scripts/test.ps1`、90% JaCoCo BUNDLE 门槛和可复现构建时间戳。

## 验证

Windows、Temurin JDK 17.0.20.1、Maven Wrapper 3.9.11：

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
.\scripts\maven.ps1 --batch-mode --no-transfer-progress verify
```

结果：`BUILD SUCCESS`；245 tests / 0 failures / 0 errors / 0 skipped；7 个 Flyway migration 在全新 H2 schema 成功应用；JaCoCo 总行覆盖率 91.59%，90% BUNDLE 门槛通过。打包 JAR 在默认 dev profile 启动到 127.0.0.1:18080，健康检查返回 UP，随后正常停止。

运行时曾因 Codex 沙箱阻止 `javac` 关闭依赖 JAR 文件，改用同一命令的受控沙箱外执行完成验证；JDK、源码、Maven 与测试命令未改变。

## 未验证

- 真实 MySQL 8 实例。
- 真实 SMTP 或邮件供应商。
- GitHub Actions 远端执行。
- C 的 Booking/Payment 与 A 的 Member repository 端口及 adapter 联调。
- 进程崩溃后的 SENDING 租约恢复、provider 幂等、退信处理。
- Q8 三天前提醒。

## 关联文件

- `src/main/resources/db/migration/V2__create_room_catalog_schema.sql` … `V7__seed_room_catalog.sql`
- `src/main/java/com/holidayresort/booking/notification/**`
- `src/main/java/com/holidayresort/booking/persistence/**`
- `src/main/java/com/holidayresort/booking/room/application/port/RoomInventoryConflictException.java`
- `src/main/java/com/holidayresort/booking/room/application/RoomManagementService.java`
- `src/test/java/com/holidayresort/booking/notification/**`
- `src/test/java/com/holidayresort/booking/persistence/**`
- `.github/workflows/ci.yml`, `scripts/test.ps1`, `pom.xml`
- `docs/d-persistence-notification-foundation.md`, `docs/interfaces.md`, `docs/decisions.md`, `handoff/CURRENT.md`