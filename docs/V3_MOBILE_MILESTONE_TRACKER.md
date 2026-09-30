# SMART Mobile V3 milestone tracker

Updated: 2026-09-14, Milestone 5 — Prompt 18 local staging and client handoffs.

This is the consolidated grouping clarified with the user after Prompt 8.
A **milestone** is a deliverable area; a **prompt** is one bounded work session.
Target sessions are 30–45 minutes; tests, findings and deployment dependencies
can require a further session. Component completion never means production
acceptance or complete Mobile connectivity.

| Milestone | Deliverable | Current status |
|---|---|---|
| 1 — Contracts and alignment | Versioned DTOs, authentication policy, download alignment | Contract deliverables complete; secure persistent Mobile session remains pending |
| 2 — Objective scan workflow | Evidence, upload/recovery, MC/TF detections, verification, backend scoring, result/analytics readback and reconciliation | **Objective backend components complete through Prompt 9 in isolated tests; production release gated** |
| 3 — Written-answer workflow | Evaluation/rubric reference, crops/evidence, comments, manual/rubric score upload and official finalization | **Written backend components complete through Prompt 13 in isolated tests; production release gated** |
| 4 — Audited corrections | Reopen/correction history, superseding, score-version and analytics consistency | **Backend components complete through Prompt 16 in isolated tests; production release gated** |
| 5 — Production integration and acceptance | Reviewed migration/deployment, secure Mobile sessions, SQLite/wiring, Frontend report/design alignment, reachable URL and physical end-to-end/scanner validation | **IN PROGRESS — synthetic local staging and HTTP validation completed in Prompt 18; Mobile/Frontend wiring, USB device and physical acceptance pending** |

## Milestone 2 sessions

| Prompt | Component | Status / evidence |
|---|---|---|
| 3 | Original image storage | Complete — [storage handoff](V3_MOBILE_MILESTONE_2_STORAGE_HANDOFF.md) |
| 4 | Scan-page ingress and identity resolution | Complete — [ingestion handoff](V3_MOBILE_SCAN_INGESTION_HANDOFF.md) |
| 5 | Upload retries and recovery | Complete — [recovery handoff](V3_MOBILE_UPLOAD_RECOVERY_HANDOFF.md) |
| 6 | MC/TF detection upload and persistence | Complete — [detection handoff](V3_MOBILE_DETECTION_HANDOFF.md) |
| 7 | Objective teacher verification | Complete — [verification handoff](V3_MOBILE_TEACHER_VERIFICATION_HANDOFF.md) |
| 8 | Objective backend scoring and first finalization | Complete — [finalization handoff](V3_MOBILE_FINALIZATION_HANDOFF.md) |
| 9 | Result/analytics readback and reconciliation | Complete — [readback handoff](V3_MOBILE_READBACK_HANDOFF.md) |

The implemented analytics scope is backend official total/max/percentage.
Item analysis, mastery, interventions and Student 360 remain explicitly
unavailable in the Mobile analytics response. This tracker does not mark those
modules complete or claim production phone/scanner acceptance.

## Milestone 3 sessions

| Prompt | Component | Status |
|---|---|---|
| 10 | Written contract review and owned evaluation-reference | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_EVALUATION_REFERENCE_HANDOFF.md) |
| 11 | Written evidence/crop upload and persistence | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_ATTACHMENT_UPLOAD_HANDOFF.md) |
| 12 | Manual/rubric verification and scoring persistence, including reference binding and image-only representation | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_WRITTEN_SCORING_HANDOFF.md) |
| 13 | Written finalization and integrated written workflow validation | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_WRITTEN_FINALIZATION_HANDOFF.md) |

## Milestone 5 sessions

| Prompt | Component | Status |
|---|---|---|
| 17 | Fail-closed backend release profile, preflight endpoint, session decision and acceptance runbook | COMPONENT COMPLETE; deployment gated — [handoff](V3_MOBILE_MILESTONE_5_RELEASE_HANDOFF.md), [validation](V3_MOBILE_MILESTONE_5_RELEASE_VALIDATION.json) |
| 18 | Synthetic staging migration/restore, evidence storage, fixed local URL, live backend HTTP test and both client handoffs | Backend staging complete; user selected Android USB/adb reverse, no phone attached during setup — [handoff](V3_PROMPT_18_STAGING_HANDOFF.md) |
| 19 | Coordinated Mobile wiring, Frontend report/design integration and physical acceptance | Pending; bounded client sessions M1–M4 and F1–F2, followed by shared acceptance |

## Next bounded session

**Milestone 5 — Prompt 19: client integration, starting with M1 and F1.**
Use the [Mobile guide](V3_MOBILE_INTEGRATION_HANDOFF.md) for secure session/download
alignment and the [Frontend guide](V3_FRONTEND_INTEGRATION_DESIGN_HANDOFF.md) for
existing Assessment Results and official score display. Both guides include
copy-paste instructions, source anchors and 30–45 minute session targets. The
guides have been prepared, not sent to another AI task. Continue M2/M3 and F2,
then run M4 shared device acceptance. Backend handles concrete defects identified
by those integrations. These are parts of Milestone 5, not new feature milestones.

Known presentation limits: current original capture eligibility is one-page A4
ten-MC. Written/TF component persistence is not proof of printed-layout capture.
Frontend report rows currently lack resultUuid for direct UUID detail navigation;
secure evidence-image retrieval is not supplied by the reviewed Mobile controllers.
Existing report display can proceed while those optional review links stay gated.

## Milestone 4 sessions

| Prompt | Component | Status |
|---|---|---|
| 14 | Audited result reopen, retry and stale-score reconciliation | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_REOPEN_HANDOFF.md) |
| 15 | Audited correction upload and re-finalization | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_CORRECTION_HANDOFF.md) |
| 16 | Teacher-requested page rescan, result supersession, retry/link readback and report consistency | COMPONENT COMPLETE; release gated — [handoff](V3_MOBILE_SUPERSEDE_HANDOFF.md) |

Milestone 4 implementation is complete for the existing bounded backend workflow.
Physical rescan/phone acceptance is still Milestone 5. Synthetic written fixtures
do not validate printed layouts or scanner reliability.

## Release state

- Configured baseline remains V3_014: 67 tables / 163 FKs / 87 checks / 99 uniques.
- V3_015..V3_023 were applied to a synthetic local staging database after a baseline
  backup/restore rehearsal; totals are 79 tables / 200 FKs / 131 checks / 118 uniques.
  Supersession and affected sync readback require V3_023. School deployment remains pending.
- Mobile scan/detection/verification/attachment/reopen/correction/supersede writes and supersession GET remain denied under the normal
  `v3` profile. The new `v3-mobile-release` profile plus master switch can open them only after release preflight passes. All release
  switches still default to false. Prompt 18 explicitly opens only the dedicated
  synthetic local instance at http://127.0.0.1:8080; this is not a production release.
- No production Mobile SQLite migration, secure token persistence, APK change,
  physical-phone validation or school database migration was performed here.
- SF1 remains excluded. Preserve working V1/V2 throughout the coordinated release.
