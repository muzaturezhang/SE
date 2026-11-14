# Evidence Index — Integrated Engineering Verification

Group 6: Nacy Lin, Muzature Zhang, Freya Wang, Mila Liu.
Build: integrated `main` after PR #11, 2026-11-14 Asia/Shanghai.
This index lists engineering evidence only. It does not claim external email delivery, Q5/Q7 user-test results, production operation or final course acceptance.

| Evidence ID | Artifact | Verification | Status |
| --- | --- | --- | --- |
| EV-D-001 | `target/surefire-reports/` | 338 tests, 0 failures, 0 errors, 5 environment-gated skips | LOCAL PASS |
| EV-D-002 | `target/site/jacoco/jacoco.csv` and HTML report | 90.52% line, 74.06% branch | LOCAL PASS |
| EV-D-003 | `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar` | Spring Boot packaging via Maven verify | LOCAL PASS |
| EV-D-004 | `src/main/resources/db/migration/V1..V8` | Flyway applies 8 migrations to clean H2 and H2-MySQL-mode schema | LOCAL PASS |
| EV-D-005 | `MySqlProfileIntegrationTest` | JDBC adapters, booking/payment/reminder lifecycle and release history | LOCAL PASS |
| EV-D-006 | `MySqlLiveIntegrationTest` | Real MySQL 8 migration, seed, JDBC persistence and lifecycle | PASS — final push [44019910978](https://github.com/muzaturezhang/SE/actions/runs/44019910978) and PR [44019929419](https://github.com/muzaturezhang/SE/actions/runs/44019929419) |
| EV-D-007 | `.github/workflows/ci.yml` | Java 17 verification and MySQL 8 service jobs | PASS — both final push/PR runs are green |
| EV-D-008 | `docs/test/d-test-report.md` | Integrated engineering report and known limitations | UPDATED |
| EV-D-009 | `docs/test/d-defect-log.csv` | Fixed integration defects and gated environment items | UPDATED |
| EV-D-010 | `docs/test/d-test-case-register.csv` | Requirement-to-test trace | UPDATED |
| EV-D-011 | SMTP tests and `docs/evidence/smtp-loopback-*` | Mock mapping/error conversion plus real loopback SMTP command sequence; no external provider contacted | LOCAL PASS |
| EV-D-012 | User-test evidence | Requires real participants, consent, anonymisation and retest | NOT COLLECTED |
| EV-D-013 | PR #2 merge | Earlier D foundation merged at `aa1275d` | MERGED |
| EV-D-014 | `docs/test/user-test-plan.md` + consent/observation templates | Q5/Q7 protocol ready; participant results not fabricated | READY |
| EV-D-015 | `docs/test/d-failure-injection-matrix.md` | Failure/boundary trace and explicit untested items | UPDATED |
| EV-D-016 | PR #8 customer booking UI | Real quote/create/list/detail/change/cancel/payment routes | MERGED — CI [44018023452](https://github.com/muzaturezhang/SE/actions/runs/44018023452), [44018044384](https://github.com/muzaturezhang/SE/actions/runs/44018044384) |
| EV-D-017 | PR #9 STAFF booking administration | Real filters/pagination/detail and under-review reconciliation | MERGED — CI [44019392648](https://github.com/muzaturezhang/SE/actions/runs/44019392648), [44019409062](https://github.com/muzaturezhang/SE/actions/runs/44019409062) |
| EV-D-018 | PR #10 SMTP loopback evidence | Real local JavaMail SMTP exchange and retained Surefire result | MERGED — CI [44019910978](https://github.com/muzaturezhang/SE/actions/runs/44019910978), [44019929419](https://github.com/muzaturezhang/SE/actions/runs/44019929419) |
| EV-D-019 | `docs/screenshots/final-2026-11-14/` | Final integrated customer/STAFF desktop and mobile screenshots captured from a fresh H2 build with fictional data | PASS — 17 screenshots at 1440×1100 / 390×844 |
| EV-D-020 | `deliverables/final/` report, PDF and 12-slide deck | Q1–Q8 report and presentation prepared with checksums and PPT validation receipt; final course tag still pending | READY — TAG PENDING |

## Reproduce

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
./scripts/test.ps1
```

For MySQL 8, set `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` and run `MySqlLiveIntegrationTest`; GitHub Actions supplies these values against a disposable MySQL 8 service.

For the loopback SMTP evidence:

```powershell
./scripts/maven.ps1 --batch-mode --no-transfer-progress "-Dtest=SmtpLoopbackDeliveryTest" test
```

## Evidence hygiene

- No production passwords, payment keys or real recipient addresses are stored in the repository.
- Test fixture emails are fictional.
- The local skip of `MySqlLiveIntegrationTest` is expected without `MYSQL_URL`; the same test runs in GitHub Actions.
- The loopback SMTP test proves protocol delivery only; external-provider delivery remains unverified.
- Screenshots and user-test records must remove names, email addresses, booking references and payment identifiers before final submission.