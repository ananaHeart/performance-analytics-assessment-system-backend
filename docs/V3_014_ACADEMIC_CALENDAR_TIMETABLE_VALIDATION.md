# V3_014 Academic Calendar And Timetable Validation

Status date: 2026-09-03

## Scope

This record covers the controlled local application of
`V3_014_academic_calendar_and_class_schedules.sql` to
`performance_assessment_v3_db`.

It adds school-scoped academic-year integrity, four-term lifecycle integrity,
teacher-owned class timetable records, and auditable outside-schedule
assessment confirmation. It does not switch React, Mobile SQLite, V1/V2, TiDB,
analytics, sync upload, or scanner behavior.

## Safety And Application

- Pre-migration backup:
  `docs/backups/performance_assessment_v3_db_pre_V3_014_20260903_163000.sql`
- The migration was first applied to an exact disposable clone.
- Disposable result: 67 tables, 163 foreign keys, 87 checks, 99 unique
  constraints.
- The same migration was then applied once to the local central V3 database.
- The migration has a fail-closed preflight and is not intended to be rerun.

## Automated Verification

Focused Slice 4-5 service/readiness tests passed.

Full Maven result:

```text
Tests run: 193, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Live Runtime Verification

`GET http://localhost:8082/api/v3/system/readiness` returned HTTP 200 with:

- `ready=true`
- 67 tables
- 163 foreign keys
- 87 check constraints
- 99 unique constraints
- 19 of 19 readiness checks passed

Authorization and CORS checks:

- Calendar request without a token: HTTP 401
- Teacher timetable request without a token: HTTP 401
- Principal login: HTTP 200
- Principal school-scoped academic-year list: HTTP 200
- Principal logout: HTTP 200
- CORS preflight from `http://localhost:5173`: HTTP 200

## Automation Safety

The existing local academic year is the historical 2025-2026 demo year. The
automation scheduler is therefore disabled by default and requires
`V3_CALENDAR_AUTOMATION_ENABLED=true` to run.

The server was started with the default setting and the current year/term
statuses remained unchanged. Calendar dates must be corrected and approved
before enabling automatic rollover.

## Result

V3_014 is implemented, locally migrated, and structurally/runtime validated.
Frontend acceptance for calendar/timetable controls and a controlled automatic
rollover fixture remain separate gates.
