# Evidence Index — Internal Verification

Build: D integration worktree, 2026-11-13 Asia/Shanghai.
This index lists engineering evidence only. It does not claim final Stage 3/4 acceptance, user-testing results or production operation.

| Evidence ID | Artifact | Verification | Status |
| --- | --- | --- | --- |
| EV-D-001 | `target/surefire-reports/` | 317 tests, 0 failures, 0 errors, 5 environment-gated skips | LOCAL PASS |
| EV-D-002 | `target/site/jacoco/jacoco.csv` and HTML report | 90.69% line, 74.47% branch | LOCAL PASS |
| EV-D-003 | `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar` | Spring Boot packaging via `verify` | LOCAL PASS |
| EV-D-004 | `src/main/resources/db/migration/V1..V8` | Flyway applies 8 migrations to clean H2 and H2-MySQL-mode schema | LOCAL PASS |
| EV-D-005 | `MySqlProfileIntegrationTest` | JDBC adapters, booking/payment/reminder lifecycle and release history | LOCAL PASS |
| EV-D-006 | `MySqlLiveIntegrationTest` | Real MySQL 8 migration, seed, JDBC persistence and booking/payment/reminder lifecycle | PASS — lifecycle verified in push run [43892676670](https://github.com/muzaturezhang/SE/actions/runs/43892676670) and PR run [43892690685](https://github.com/muzaturezhang/SE/actions/runs/43892690685); earlier migration evidence remains in [43881256201](https://github.com/muzaturezhang/SE/actions/runs/43881256201) / [43882193364](https://github.com/muzaturezhang/SE/actions/runs/43882193364) |
| EV-D-007 | `.github/workflows/ci.yml` | Java 17 verification and MySQL 8 service jobs | PASS — Java 17 and MySQL 8 jobs succeeded in push run [43892676670](https://github.com/muzaturezhang/SE/actions/runs/43892676670) and PR run [43892690685](https://github.com/muzaturezhang/SE/actions/runs/43892690685), with 0 annotations |
| EV-D-008 | `docs/test/d-test-report.md` | Requirement/test summary and known limitations | UPDATED |
| EV-D-009 | `docs/test/d-defect-log.csv` | Fixed integration defects and gated environment item | UPDATED |
| EV-D-010 | `docs/test/d-test-case-register.csv` | Requirement-to-test trace | UPDATED |
| EV-D-011 | SMTP tests | Mock transport mapping and provider error conversion; no live credentials used | LOCAL PASS |
| EV-D-012 | User test evidence | Requires real participants, anonymisation and retest | NOT COLLECTED |
| EV-D-013 | PR #2 merge | `main` merged at `aa1275d` after both CI jobs succeeded | MERGED |
| EV-D-014 | `docs/test/user-test-plan.md` + consent/observation templates | Q5/Q7 protocol ready; participant results not fabricated | READY |
| EV-D-015 | `docs/test/d-failure-injection-matrix.md` | Failure/boundary trace and explicit untested items | UPDATED |

## Reproduce

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
./scripts/test.ps1
```

For MySQL 8, set `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` and run `MySqlLiveIntegrationTest`; the GitHub Actions job supplies these values against a disposable MySQL service.

## Evidence hygiene

- No production passwords, payment keys or real recipient addresses are stored in the repository.
- Test fixture emails are fictional.
- The local skip of `MySqlLiveIntegrationTest` is expected without `MYSQL_URL`; the same test ran successfully in the GitHub Actions MySQL 8 jobs.
- Screenshots and user-test records, when added later, must remove names, email addresses and payment identifiers.
