# Final Build Screenshot Evidence — 2026-11-14

These screenshots were captured from the integrated application running with the
`dev,local-staff` profiles and a fresh in-memory H2 database. They are engineering
evidence and are **not** Q5/Q7 participant evidence.

- Browser: Microsoft Edge 157.0.4341.60, headless mode via Chrome DevTools Protocol.
- Application: current Java 17 Spring Boot build after PR #10 plus the shared-login input fix.
- Desktop viewport: 1440 × 1100.
- Mobile viewport: 390 × 844.
- Data: fictional screenshot customer, fictional local STAFF account, transient H2 only.
- No real account, recipient, payment credential or personal participant data was used.

## Files

| File | Route / state | Viewport |
| --- | --- | --- |
| `room-search-desktop.png` | `/rooms` availability results | 1440 × 1100 |
| `account-register-desktop.png` | customer registration | 1440 × 1100 |
| `account-membership-desktop.png` | authenticated membership page | 1440 × 1100 |
| `account-membership-mobile.png` | responsive membership page | 390 × 844 |
| `customer-new-desktop.png` | `/stays/new` | 1440 × 1100 |
| `customer-confirm-desktop.png` | server quote confirmation | 1440 × 1100 |
| `sandbox-payment-desktop.png` | booking sandbox payment, pending | 1440 × 1100 |
| `customer-detail-under-review-desktop.png` | customer booking, payment under review | 1440 × 1100 |
| `customer-detail-paid-desktop.png` | customer booking, paid | 1440 × 1100 |
| `customer-bookings-desktop.png` | `/stays` list with separate states | 1440 × 1100 |
| `customer-bookings-mobile.png` | `/stays` responsive list | 390 × 844 |
| `staff-bookings-desktop.png` | `/admin/stays` real booking list | 1440 × 1100 |
| `staff-bookings-mobile.png` | `/admin/stays` responsive list | 390 × 844 |
| `staff-booking-review-desktop.png` | `/admin/stays/{reference}` under-review reconciliation | 1440 × 1100 |
| `staff-booking-review-mobile.png` | under-review detail responsive layout | 390 × 844 |
| `jacoco-summary-desktop.png` | JaCoCo final coverage report | 1440 × 1100 |
| `ci-push-success-desktop.png` | final GitHub Actions push run | 1440 × 1100 |

## Notes discovered during capture

The browser walkthrough found that the shared login page used `type="email"` for
the username field, which blocked non-email STAFF accounts such as `localstaff`
before the POST reached Spring Security. The field is now `type="text"` with
`inputmode="email"`, and `MemberAccountWebTest` asserts the rendered contract.

The final course tag and report/PPT must reference screenshots from this build or
from a later documentation-only commit that keeps the application code unchanged.