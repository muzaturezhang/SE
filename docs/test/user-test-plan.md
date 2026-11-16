# User Test Plan — Q5/Q7

Status: ready-to-execute protocol. No participant results are recorded in this file. Actual evidence must be collected only after real sessions and anonymisation.

Owner: D (Quality, Notification and Persistence Lead).
Reviewers: A (scenario/compliance), B (UI/task design), C (booking/payment state evidence).

## 1. Purpose

Evaluate whether representative customers and staff can complete the agreed resort journeys without assistance, and convert observed problems into traceable defects and retest evidence.

This plan supports:

- Q5: user-testing evidence for implemented customer flows.
- Q7: usability findings, improvements, before/after evidence and retest results.
- Consistency checks between requirements, UI, service states and the final demonstration build.

## 2. Participants and ethics

- Use the sample size agreed by the team and module leader; do not invent participants or results.
- Recruit two user groups: customer/member users and management-staff users.
- Every participant receives the information sheet and gives consent before the session.
- Use participant codes such as `P-C-01`, `P-S-01`; do not store unnecessary names, phone numbers, home addresses or payment details.
- Record audio/video only with explicit consent. Otherwise use written observations.
- Explain that the product is a coursework prototype and that no real payment is required.
- A participant may stop at any time without giving a reason.

## 3. Roles

| Role | Responsibility |
| --- | --- |
| Facilitator | Reads the task, keeps neutral wording, does not guide the participant unless the session is blocked |
| Observer | Records success/failure, time, errors, confusion, quotations and severity |
| Note taker | Captures evidence filenames and anonymises records |
| Safety/technical support | Restores fixtures and stops the session if a real external service or personal data is about to be used |

One person may hold more than one role if the team records that limitation.

## 4. Environment and data

- Use one tagged build and record its commit SHA/tag.
- Use the explicit `dev`/preview profiles and fictional data only; never use real email recipients, payment credentials or participant email addresses.
- Reset fixtures before each session.
- Use desktop and mobile viewport checks where the task requires layout evidence.
- Keep browser zoom at the agreed baseline and record any assistive technology used.
- The facilitator must not explain hidden button locations or tell the participant what to click.

## 5. Customer tasks

| Task ID | Scenario | Success condition | Requirement link |
| --- | --- | --- | --- |
| UT-C-01 | Find a room type for a valid stay | Search completes and a matching room type or explicit empty state is shown | FR-ROOM-001..006 |
| UT-C-02 | Register and sign in | Account is created and the participant reaches their own profile | FR-MEM-001..005 |
| UT-C-03 | Open or renew membership | Plan, fee and validity are readable; payment state is not misrepresented | FR-MEM-006..008 |
| UT-C-04 | Create a booking draft and review the quote | Dates, room count, room type and server-provided quote are understandable | UI-STAY-001..008 |
| UT-C-05 | Follow payment state | Pending, success, failure and under-review wording is distinguishable | FR-BOOK-002..005, FR-PAY-001..005 |
| UT-C-06 | Find the customer's own booking | The participant reaches the correct booking and cannot see another customer's data | FR-BOOK-004 |
| UT-C-07 | Cancel or change a booking, if the team has enabled the feature | Participant completes the supported flow or receives a clear unavailable state | FR-BOOK-006 / Q8 |

If a task is not implemented in the tagged build, record it as **Not Available**, not as a participant failure.

## 6. Staff tasks

| Task ID | Scenario | Success condition | Requirement link |
| --- | --- | --- | --- |
| UT-S-01 | Filter rooms by type and state | Staff sees the correct filtered set and clears the filter | FR-ROOM-007 |
| UT-S-02 | Add and edit a room | Validation is understandable and successful changes are visible | FR-ROOM-008..009 |
| UT-S-03 | Deactivate/reactivate a room | Staff understands occupancy refusal and successful deactivation | FR-ROOM-010 |
| UT-S-04 | Find a customer booking and inspect payment state | Staff reaches the detail and distinguishes booking state from payment state | UI-STAFF-STAY-001..008 |
| UT-S-05 | Review an unknown payment, if enabled | Staff understands why the payment is under review and what action is safe | FR-PAY-005 |

## 7. Session procedure

1. Welcome, consent and privacy explanation (5 minutes).
2. Warm-up questions about hotel booking habits, without discussing the implementation.
3. Read each task exactly as written; do not demonstrate the UI first.
4. Start timing when the task is read; stop when the success condition is met or the participant gives up.
5. Record errors, hesitation, recovery attempts and direct quotations.
6. Ask neutral follow-up questions: “What were you expecting here?”, “What made this easy or difficult?”
7. Complete the short post-session questionnaire.
8. Debrief, thank the participant and confirm what evidence will be retained.

## 8. Metrics

Record per task:

- completion: success / partial / failure / not available;
- time to completion;
- number of errors or recoveries;
- assistance required: none / hint / intervention;
- confusion points;
- severity: S1 blocking, S2 major, S3 minor, S4 cosmetic;
- affected requirement and UI location;
- recommendation owner.

Do not report an average time if the feature was unavailable or the participant was interrupted.

## 9. Defect and improvement handling

Each finding receives a defect or improvement ID with:

- evidence file and anonymised participant code;
- expected and actual behaviour;
- linked requirement/test;
- severity and priority;
- owner and target build;
- fix committed in the repository;
- retest result by the original observer or a second participant where practical.

A finding is not closed merely because code changed; it needs retest evidence.

## 10. Evidence and anonymisation

Store evidence under a team-approved folder and include:

- consent record status (not the participant's identity);
- observation sheet;
- timing and completion summary;
- anonymised screenshots before/after;
- defect/retest records;
- tagged build SHA.

Redact names, email addresses, booking references that could identify a person, payment identifiers, browser account avatars and any unrelated desktop content. Keep raw unredacted recordings only if the consent and storage policy permit it.

## 11. Exit criteria

- All planned sessions have consent and observation records.
- Every failure is classified and linked to a requirement/test.
- No S1/S2 defect is left unowned.
- Fixed issues have retest evidence.
- The report distinguishes prototype limitations from participant errors.
- Q5/Q7 evidence references the same build/tag as the final demonstration.
