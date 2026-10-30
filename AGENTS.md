# Project working instructions

## Start here

- Read `handoff/CURRENT.md`, then `docs/decisions.md` and `docs/interfaces.md` before editing.
- The user is team member B. Authorized work so far includes the shared skeleton, approved customer UI, batch-one Room catalog/availability queries, batch-two STAFF room management with in-memory data and an opt-in loopback-only local login preview, batch-three booking/payment/list/detail UI, and batch-four login/registration/profile/membership UI, and batch-five read-only STAFF booking list/detail UI with shared admin navigation, using explicitly labelled local fixtures and page-facing adapter contracts. Do not implement other members' business rules without further authorization.
- The user also authorized organizing B design and handoff materials: docs/design contains source-aligned diagrams/CRC/navigation/query interaction/UI principles, with a shareable DOCX; handoff/B-team-integration.md lists proposed integration reviews. Keep these as reviewable documentation, not fabricated final course evidence.
- The user authorized the B local closeout: real JDK 17 validation, UI keyboard/reflow fixes, a source-only handoff archive, and clean extraction/build/startup verification. Preserve the distinction between local readiness and real team integration or user acceptance.
- Keep source, data, documentation and project-specific caches inside this directory. Do not modify the sibling course materials.

## Architecture

- Keep Java 17 source/bytecode compatibility unless the user changes the agreed baseline. Distinguish the compiler target from the JDK actually used to run tests.
- Use the shared Spring Boot/Thymeleaf application. A owns member/authentication and shared integration, B owns room and UI, C owns booking/payment, D owns notification/persistence and test infrastructure.
- Do not bypass Service boundaries by querying another module's tables from UI controllers.
- Cross-module DTOs, state enums, signatures and schema are not frozen yet. Document proposed changes and affected owners in `docs/interfaces.md`.
- Money is integer CNY minor units; stay dates use LocalDate and the half-open interval; use the shared Clock for business time.
- Keep CSRF protection and backend authorization. Never treat a UI control as access control.
- Do not create production credentials, fake completed business flows, invented test evidence, or real external service calls in the default profile.

## Verification and handoff

- Use `./scripts/maven.sh` or `./scripts/test.sh` so downloads stay in the project-local ignored cache.
- Run checks appropriate to the changes. Record exact commands, runtime, outcome and limitations. Do not label compilation on JDK 23 as a runtime test on JDK 17.
- Keep `handoff/CURRENT.md` as the current status. Append a dated Markdown entry under `log/` after meaningful work, linking changed files and relevant checks.
- Never store credentials or personal participant information in logs or fixtures. Keep disposable build and runtime output out of the tracked Markdown work log.
