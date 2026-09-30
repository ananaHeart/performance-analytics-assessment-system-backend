# Session recap: dynamic OMR rebuild and backend audit

Date: 2026-09-15 to 2026-09-16
Scope: `D:\CAPSTONE_2\backend\assessment` only. Mobile (`D:\ThesisProjects\MobileAssessmentApp`) and Frontend (`D:\CAPSTONE_2\web-dashboard`) sessions were coordinated with, never edited directly.

This is a working record of one session's worth of investigation and changes. It follows the project's own honesty rule: nothing here is marked "done" unless it was actually verified (compiled, tested, or read directly from source), and anything gated or pending says so plainly.

## 1. Initial backend audit

Scanned the whole backend repo before touching anything. Findings, all verified against live source rather than trusted from docs:

- One Spring Boot 3.5.16 app holds three coexisting API generations: V1 (default profile, 11 controllers, still live), V2 (`v2` profile, 9 controllers), V3 (`v3` profile, 25 controllers, where active work happens).
- V3 Mobile write/finalization feature flags (`app.v3.mobile.finalization-enabled`, `readback-enabled`, `reopen-enabled`, `correction-enabled`, `supersede-enabled`, `evaluation-reference-enabled`) all default to `false` in `application-v3.properties`.
- `.gitignore` does not exclude `.codex*.log`, `debug.log`, `output/`, `tmp/`, `patch_fix/`, or `docs/backups/`, despite the project's own handoff guide saying not to commit those.
- Two root-level `.patch` files no longer apply forward or in reverse against current source — dead scratch files.
- `main` already contains all of `agent/v2-sf1-import` plus one newer commit; the handoff guide's claim that main was behind was stale.
- `mvn -q -o -DskipTests compile` succeeds cleanly.

Full detail: see the architecture memory saved for this project (`capstone-backend-architecture`).

## 2. The dynamic OMR mismatch

The user handed over confirmed-correct reference files (three manifest/PDF pairs for A4, US Letter, US Legal, byte-identical to what's bundled inside the Mobile app) that a prior Codex session had audited against the backend's existing dynamic answer-sheet generator. Confirmed mismatches, verified directly against source on both sides:

| Detail | Approved contract | Old backend output |
|---|---|---|
| Layout | Mixed: MC, true/false, identification, enumeration, essay | MC-only |
| QR position/size | Bottom-right, 72pt | Upper area, 83pt |
| Registration markers | Hollow top-left, three solid corners | All four identical "nested square" |
| QR `tv` field | JSON number `3` | JSON string `"3"` |
| Scanner version | `3.0.0-prototype.2` | `3.0.0` |
| Manifest structure | Explicit `registrationMarkers[]`, `registrationMarkerPattern`, `qrRectangle` fields | Generic flat `regions[]` list |
| Item capacity | Content-driven packing (short/medium/long/full_page per type) | Fabricated fixed 16-items-per-page, 192-question ceiling |

Root cause: the old dynamic generator was built as an extension of the fixed MC-only renderer instead of from the actual approved spec.

## 3. What was built

Read the entire 1276-line reference generator (`generate_dynamic_answer_sheet.py`, OMRPrototype repo) and the full manifest-parsing logic in `DynamicOmrDetector.kt` (Mobile repo) before writing any code. New, isolated package: `com.capstone.assessment.v3.answersheet.service.dynamic`.

- **`DynamicSheetModels.java`** — geometry/manifest data model matching the Mobile wire contract field-for-field.
- **`DynamicCanonicalHash.java`** — canonical JSON hashing (sorted keys, compact separators, SHA-256) and a Java port of Python's `uuid.uuid5` for deterministic page/region UUIDs.
- **`DynamicSheetPacker.java`** — full port of the reference packing algorithm: per-question-type region heights, two-column lane pairing, page-break rules, marker/QR geometry, manifest/QR-payload assembly and hashing.
- **`DynamicSheetPdfRenderer.java`** — PDFBox port of all five region-type drawing routines (the old renderer only knew multiple_choice).

**Verification, not just claims:**
- `DynamicSheetPackerTest` reconstructs the exact 12-question fixture behind the confirmed reference manifest and asserts against it. First run caught a real bug (a duplicated first page from a copy-paste mistake); fixed, then: page UUIDs, region UUIDs, marker order/style, QR rectangle, and every checked region rectangle matched the reference file exactly.
- Generated real PDFs for all three paper sizes (`output/dynamic-sheet-preview/backend-dynamic-mixed-{a4,us_letter,us_legal}.pdf`) and visually compared the A4 output against the reference PDF — layout, markers, QR placement, and five-region-type rendering all line up.
- Caught and fixed a real gap during that visual check: the renderer wasn't receiving school name/assessment/subject/section context at all, so the header printed blank. Fixed and reverified.

## 4. The `generateDynamic()` discovery

Before wiring the new engine into the live API, read `V3AnswerSheetService.java` and `V3AnswerSheetRepository.java` in full. Found something that changed the plan:

- The service already has a **different, older** feature called `generateDynamic()`, live in the real `generate()` endpoint — same-type multiple-choice questions across many pages, not mixed question types. It reuses the exact template code string `OMR-A4-DYNAMIC-CTX-V3` that Mobile's real contract uses for something incompatible.
- `V3AnswerSheetRepository.findValidatedTemplate()` is hardcoded to look up `template_code = V3DynamicLayout.CODE` — the dynamic code, not the fixed one. If that dynamic template ever gets an active database row, **every** generation request, including the working fixed 10-item sheet, would route through the incompatible branch, because that lookup can never return the fixed template's row at all. This is a pre-existing risk, not something introduced this session, and it was not fixed — only discovered and flagged.
- The eligibility layer (`V3AnswerSheetService.evaluate()`) also hard-blocks any non-multiple-choice question type and requires exactly options A-D, regardless of which template is matched.

Given this, `V3AnswerSheetService`, `V3AnswerSheetPdfRenderer` (the shared fixed-sheet one), and `V3ScanPageIngestionService` were deliberately **not** edited.

## 5. The isolated preview endpoint

Built instead: `POST /api/v3/answer-sheets/dynamic-preview`, teacher-authenticated, no database read or write, no eligibility check, nothing persisted. Takes a self-contained JSON body (parts/questions) and returns a real mixed-question-type PDF straight from the verified packer/renderer.

New files only:
- `V3DynamicAnswerSheetPreviewRequest.java` (DTO)
- `DynamicSheetRequestMapper.java` (DTO → packer request, with auto-numbering and default options)
- `V3DynamicAnswerSheetPreviewController.java`

One additive line in `V3SystemSecurityConfig.java` (`POST /api/v3/answer-sheets/dynamic-preview` → `hasRole("TEACHER")`, same pattern as the other answer-sheet routes) — nothing else in that file touched.

`DynamicSheetRequestMapperTest` verifies the mapping end to end (real manifest, real `%PDF`-prefixed bytes). Reran the pre-existing fixed-sheet tests (`V3AnswerSheetServiceTest`, `V3AnswerSheetPdfRendererTest`) afterward — all passing, zero regression.

This endpoint is what the physical scanner acceptance test used — printable PDFs for A4/US Letter/US Legal, generated with real assessment content, ready to print at 100% and scan with the Mobile app's `__DEV__`-gated dynamic scanner test screen.

## 6. Cross-team coordination

**Mobile** (peer session `mobileassessmentapp-24`) independently verified the manifest field contract, hash algorithm question (closed by reading the OMRPrototype generator directly — canonical JSON, sorted keys, `separators=(",",":")`, SHA-256, per-page/per-region/whole-manifest hash scoping), and confirmed the three bundled manifests are the complete reference set. Also found and fixed a real gap on their side: the dynamic-scanner test button was reachable in release builds (not just debug) via `LoginScreen.tsx` and `App.tsx`; gated it behind `__DEV__`, reran their full Jest suite (73/73 passing), no regression.

**Frontend** (peer session `web-dashboard-f3`) read all 25 V3 controllers and the existing frontend integration handoff, then cross-checked real `apiV3Client.js` call sites (not just export existence) against the backend's actual endpoint list. Produced a prioritized gap report:

| Gap | Extends | Priority | Status |
|---|---|---|---|
| Result detail (readback/analytics/supersession) | New drawer on the existing Assessment Results table | High | Was blocked on missing `resultUuid` — now unblocked (see §7) |
| Academic calendar write actions | Existing read-only "School year and term periods" card | Medium | Not blocked |
| V3 answer-sheet print surface | New sibling of the existing V2 print page | Medium-low | Not started |
| Dynamic-preview endpoint UI | Nothing yet | Low | Testing utility only, by design |

Correction the frontend caught on the backend side: class-schedule CRUD was already wired (`V3ClassScheduleController` called from `ClassRecordsPage.jsx`) — an initial backend-side grep missed it by searching path strings instead of checking actual call sites.

As of this recap, the frontend session has made **zero code edits** — confirmed directly via live cross-session status check, not assumed. It's holding for its own user's go-ahead on which gap to build next.

## 7. `resultUuid` fix

Verified the frontend's blocking claim against source rather than trusting it: `test_results.result_uuid` already exists in the reviewed `V3_002_assessment_capture.sql` migration (not a draft) and is already used throughout the mobile scoring pipeline — it just wasn't selected by this one report query.

Four small, additive edits:
- `AssessmentResultRow` (repository model) and `StudentResultRow` (`V3AssessmentResultsReportResponse`) both gained a `resultUuid` field next to `testResultId`.
- `V3ReportRepository`'s report query now selects `result.result_uuid`.
- `V3ReportService.mapRows()` threads it through.
- Updated three existing positional-constructor test fixtures in `V3ReportServiceTest`.

All 10 tests in that suite pass; full project compile and test-compile both clean afterward.

## 8. MariaDB/XAMPP corruption investigation

The user reported InnoDB corruption in their local XAMPP MariaDB (`performance_assessment_v3_db`) and asked for a forensic review of this session's actions, with an explicit instruction not to run any repair/restart/DB commands.

Reviewed the full tool-call history for this session. Findings:
- `C:\xampp2` and port 3306 never appear anywhere in this session's commands.
- The Spring Boot application itself was never started (no `spring-boot:run`, no `java -jar`) — only `mvn compile`, `mvn test-compile`, and `mvn test` with explicit class filters were run.
- Every test class actually executed was verified by reading its source: three are plain Java with zero Spring/JDBC code (written this session), and the three pre-existing ones (`V3AnswerSheetServiceTest`, `V3AnswerSheetPdfRendererTest`, `V3ReportServiceTest`) all use Mockito mocks or have no Spring/DB annotations at all — none can reach a real database.
- The `resultUuid` SQL edit changed a string literal inside `.java` source; it was never executed against any database since the application was never run.

Conclusion reported to the user: no action in this session started, stopped, connected to, or wrote to the XAMPP MariaDB instance or its data files. No repair or diagnostic action was taken, per instruction — investigation only.

## 9. Current status and what's next

Done and verified:
- Dynamic OMR packing/geometry/rendering engine, matching the confirmed Mobile contract exactly.
- Standalone, zero-risk preview endpoint for physical scanner acceptance testing.
- `resultUuid` now available on the assessment-results report response.
- Mobile-side test-button gating (Mobile's own work, confirmed compatible).

Explicitly not done, and not started without further direction:
- Wiring mixed question types into the real eligibility/persistence pipeline (`V3AnswerSheetService`, `V3AnswerSheetRepository`, `V3ScanPageIngestionService`) — a separate, larger feature blocked on deliberate design decisions about the template-code collision described in §4.
- Any frontend code changes (frontend session is holding for its own user's priority call).
- Physical scan-acceptance result — PDFs were generated and handed off for printing/scanning; the actual scan outcome hasn't been reported back yet as of this recap.
- Investigating the actual cause of the XAMPP MariaDB corruption (ruled out as caused by this session; root cause still open).
