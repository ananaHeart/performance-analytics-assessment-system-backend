# Development Gantt Schedule

Documentation timeline note: This schedule was first prepared around May 2026 and updated through July 31, 2026 to reflect completed integration work, TiDB Cloud preparation, Render Docker deployment preparation, deployment debugging, SF1-based section handling, database redesign/polishing review, range-only part-skill mapping cleanup, cloud mobile sync validation, and ongoing testing/debugging.

Project:
A Mobile and Web Performance Analytic Assessment System

The development schedule follows a module-based approach. Core backend modules such as synchronization, analytics, school setup, assessment setup, export reports, import/student setup, and authentication are developed first before proceeding to web and mobile integration.

| Phase | Task | Start Date | End Date | Duration | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Project Planning and Requirements Analysis | Apr 28, 2026 | May 01, 2026 | 4 days | Completed |
| 2 | System Architecture Design | May 02, 2026 | May 05, 2026 | 4 days | Completed |
| 3 | Database Schema Design | May 06, 2026 | May 10, 2026 | 5 days | Completed |
| 4 | Backend Project Setup | May 11, 2026 | May 13, 2026 | 3 days | Completed |
| 5 | Sync Module Development | May 14, 2026 | May 16, 2026 | 3 days | Completed |
| 6 | Analytics Module Development | May 17, 2026 | May 21, 2026 | 5 days | Completed |
| 7 | School Setup Module Development | May 21, 2026 | May 22, 2026 | 2 days | Completed |
| 8 | Assessment Setup Module Development | May 22, 2026 | May 23, 2026 | 2 days | Completed |
| 9 | Export Report Module Development | May 23, 2026 | May 23, 2026 | 1 day | Completed |
| 10 | Import / Student Setup Module | May 24, 2026 | May 24, 2026 | 1 day | Completed |
| 11 | Authentication and Teacher Approval | May 24, 2026 | May 25, 2026 | 2 days | Completed |
| 12 | Temporary RBAC Testing | May 25, 2026 | May 25, 2026 | 1 day | Completed |
| 13 | Web Dashboard Development | May 26, 2026 | Jun 08, 2026 | 14 days | Completed |
| 14 | Mobile App Development | Jun 09, 2026 | Jun 22, 2026 | 14 days | Completed |
| 15 | Web-Mobile-Backend Integration | Jun 23, 2026 | Jun 30, 2026 | 8 days | Completed |
| 16 | JWT / Production Security Upgrade | Jul 01, 2026 | Jul 05, 2026 | 5 days | Partially Completed |
| 17 | TiDB Cloud and Render Deployment | Jul 06, 2026 | Jul 12, 2026 | 7 days | Partially Completed |
| 18 | System Testing and Debugging | Jul 11, 2026 | Jul 20, 2026 | 10 days | In Progress |
| 19 | Documentation and Final Revision | Jul 13, 2026 | Jul 31, 2026 | 19 days | In Progress |

## Dated Backend Update Timeline

This section records the major backend functions and deployment-related fixes added after the original May 2026 schedule. Dates are based on the actual project update period and are written for panel timeline presentation.

| Date / Period | Backend Function or Activity | Module / Area | Status |
| --- | --- | --- | --- |
| Late May 2026 | Initial Spring Boot backend setup, base API response format, SQL schema, and local MySQL configuration | Backend Foundation | Completed |
| Late May 2026 | Sync download and upload APIs for mobile offline workflow | Sync Module | Completed |
| Late May 2026 | Item analysis, least mastered skills, affected students, trends, and sync activity endpoints | Analytics Module | Completed |
| Late May 2026 | School setup APIs for grade levels, subjects, teachers, students, sections, and class assignments | School Setup Module | Completed |
| Late May 2026 | Assessment creation, test parts, competency options, and answer key storage | Assessment Setup Module | Completed |
| Late May 2026 | Excel export support for item analysis and LMS reports | Export Module | Completed |
| Late May 2026 | SF1 student import, student enrollment, manual student input, and validation workflow | Import / Student Setup | Completed |
| Late May 2026 | Teacher registration, login, approval status, and temporary role checks | Authentication Module | Completed |
| Late June 2026 | Grading period table and grading period API support | Assessment Setup / Grading Period | Completed |
| Late June 2026 | Rule-based LMS mapping using `parent_competency_id`, `part_skill_mapping`, and branch item coverage | Deeper LMS Mapping | Completed |
| Late June 2026 | Part skill mapping preview, save, and retrieval APIs | Assessment Setup / Skill Mapping | Completed |
| Late June 2026 | Teacher-facing intervention recommendation endpoint | Analytics Module | Completed |
| Late June to Early July 2026 | Student-centered skill mastery endpoint for student profile dashboard | Analytics Module | Completed |
| Early July 2026 | Selected assessment student score Excel export endpoint | Export Module | Completed |
| July 2026 | TiDB Cloud profile configuration with environment variables | Deployment Configuration | Completed |
| July 12, 2026 | Deployment approach changed from expected Render Java runtime to Render Docker deployment due to available runtime options | Deployment Configuration | Completed |
| July 12, 2026 | Dockerfile and `.dockerignore` preparation for Render Docker deployment | Deployment Configuration | Completed |
| July 12, 2026 | CORS update for deployed React frontend origin and OPTIONS preflight | Deployment Debugging | Completed |
| July 12, 2026 | TiDB SQL compatibility fix for subqueries inside `JOIN ON` conditions | Deployment Debugging / Analytics SQL | Completed |
| July 13, 2026 | SF1-based section creation and available section filtering for class assignment | School Setup / Import Workflow | Completed |
| July 13, 2026 | Gantt documentation updated with dated backend function timeline | Documentation | Completed |
| July 27, 2026 | Database redesign and polishing review documented | Database Design / Documentation | Documented |
| July 27, 2026 | Curriculum, intervention, answer key, term period, student enrollment, and item result analytics meanings reviewed | Database Design | Under Review |
| July 29, 2026 | Part-skill mapping finalized as range-only; `skill_item` and `mapping_mode` removed from active design | Deeper LMS Mapping / Database Cleanup | Completed |
| July 31, 2026 | Render and TiDB mobile sync download/upload validated from React Native app | Mobile / Backend / Cloud Integration | Completed |
| July 31, 2026 | Temporary TiDB `test_result` compatibility alignment documented while final database recreation remains under review | Database Integration / Deployment Debugging | Completed |

## Current Progress

As of July 13, 2026, the backend has completed the following modules:
- Sync Module
- Analytics Module
- School Setup Module
- Assessment Setup Module
- Export Reports Module
- Import / Student Setup Module with Manual Input and SF1 Smart Import
- Basic Authentication, Teacher Approval, and Temporary RBAC Module
- Rule-Based LMS Mapping Module
- Teacher Intervention Recommendation Endpoint
- Student Skill Mastery Endpoint
- Student Scores Export Endpoint
- TiDB Cloud Profile Configuration
- Render Docker Deployment Preparation
- CORS Configuration for Deployed Frontend
- TiDB SQL Compatibility Fixes
- SF1-Based Section Creation and Available Section Filtering for Class Assignment
- July 27, 2026 Database Redesign and Polishing Documentation
- July 29, 2026 Range-Only Part-Skill Mapping Cleanup
- July 31, 2026 Cloud Mobile Sync Download and Upload Validation
- July 31, 2026 Temporary TiDB Schema Alignment for Active Backend Sync Contract

Next planned phase:
Finalize the database design decisions, recreate or migrate both local MySQL and TiDB only after adviser approval, complete final security hardening if required, and prepare final documentation evidence for panel review.
