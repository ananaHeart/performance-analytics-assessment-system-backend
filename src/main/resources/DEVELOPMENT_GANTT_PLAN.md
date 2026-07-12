# Development Gantt Plan

Documentation timeline note: This plan was prepared around May 2026 as the phase-level development guide and remained the project planning reference through July 2026.

## Project Title

A Mobile and Web Performance Analytic Assessment System

## Development Approach Note

The development schedule follows a module-based approach. Backend foundation, synchronization, analytics, school setup, assessment setup, and export modules are developed and tested before mobile and web integration. This helps ensure that data accuracy, synchronization integrity, and analytics computation are stable before connecting the user interfaces.

## Phase 1. Project Planning and Requirements Analysis

**Objective**  
Define the project scope, users, system purpose, and major functional requirements.

**Main tasks**
- Identify direct users and their roles.
- Gather requirements for mobile, web, backend, analytics, and synchronization.
- Define business rules for assessments, reporting, and student data usage.
- Confirm project boundaries and capstone deliverables.

**Expected output**  
Approved project scope, requirements summary, and functional module list.

## Phase 2. System Architecture Design

**Objective**  
Design the overall system structure for mobile, web, backend API, database, and synchronization flow.

**Main tasks**
- Define mobile, web, and backend responsibilities.
- Plan offline-first mobile behavior using SQLite.
- Design REST API communication flow.
- Define synchronization and deployment direction.

**Expected output**  
System architecture diagram and module interaction plan.

## Phase 3. Database Schema Design

**Objective**  
Design the database structure needed for school setup, assessments, analytics, and synchronization.

**Main tasks**
- Identify required tables and relationships.
- Define primary keys, foreign keys, indexes, and constraints.
- Align schema with MySQL-compatible local database and future TiDB Cloud use.
- Review schema against analytics and sync requirements.

**Expected output**  
Final database schema design and SQL structure plan.

## Phase 4. Backend Project Setup

**Objective**  
Prepare the Spring Boot backend foundation for module development.

**Main tasks**
- Initialize the Spring Boot project.
- Configure base package structure.
- Add shared exception handling and API response format.
- Prepare development configuration and initial SQL files.

**Expected output**  
Working backend project foundation ready for module implementation.

## Phase 5. Sync Module Development

**Objective**  
Build backend support for mobile download and upload synchronization.

**Main tasks**
- Create sync DTOs, repository, service, and controller.
- Implement teacher download endpoint for mobile setup data.
- Implement upload endpoint for test results and item responses.
- Add duplicate-prevention checks and sync logging.

**Expected output**  
Working sync module for offline mobile data exchange.

## Phase 6. Analytics Module Development

**Objective**  
Build backend analytics features based on synchronized item-level results.

**Main tasks**
- Implement item analysis computation.
- Implement least mastered skills computation.
- Implement intervention and affected student reports.
- Implement school-wide LMS, trends, and sync activity analytics.

**Expected output**  
Working analytics module with teacher and principal reporting endpoints.

## Phase 7. School Setup Module Development

**Objective**  
Build backend support for grade levels, subjects, sections, teachers, students, and class assignments.

**Main tasks**
- Create school setup DTOs, repository, service, and controller.
- Implement listing endpoints for setup reference data.
- Implement creation endpoints for sections and class assignments.
- Validate duplicate and invalid setup records.

**Expected output**  
Working school setup module for academic structure management.

## Phase 8. Assessment Setup Module Development

**Objective**  
Build backend support for creating assessments and assessment parts.

**Main tasks**
- Create DTOs for assessments, parts, and competency options.
- Implement repository, service, and controller layers.
- Validate assessment metadata and competency-linked test parts.
- Ensure only metadata, answer keys, and competency tags are stored.

**Expected output**  
Working assessment setup module for teacher assessment preparation.

## Phase 9. Export Report Module Development

**Objective**  
Generate downloadable Excel reports for analytics outputs.

**Main tasks**
- Create export service and controller.
- Generate item analysis Excel reports using Apache POI.
- Generate LMS Excel reports using Apache POI.
- Set file naming, headers, and output formatting.

**Expected output**  
Working export report module for downloadable analytics files.

## Phase 10. SF1 Import Module Development

**Objective**  
Support import of student and enrollment data from official school files.

**Main tasks**
- Review SF1 source format and import rules.
- Design import validation and parsing workflow.
- Implement backend import processing for student and enrollment records.
- Handle duplicate, incomplete, and invalid rows safely.

**Expected output**  
Working SF1 import module for structured student data intake.

## Phase 11. Authentication and Role-Based Access

**Objective**  
Secure the backend and restrict access by user role.

**Main tasks**
- Implement authentication flow for principal and teacher accounts.
- Add password handling and login support.
- Define role-based access for module endpoints.
- Replace temporary open access configuration.

**Expected output**  
Secure backend with role-based access control.

## Phase 12. Mobile App Development

**Objective**  
Build the React Native mobile app for offline-first teacher use.

**Main tasks**
- Set up local SQLite storage.
- Create mobile screens for sync, assessments, and result capture.
- Implement download and upload integration with backend sync APIs.
- Validate offline behavior and local record storage.

**Expected output**  
Working mobile app for teacher assessment use and synchronization.

## Phase 13. Web Dashboard Development

**Objective**  
Build the React web dashboard for school setup, analytics, assessment setup, and reporting.

**Main tasks**
- Create dashboard structure and navigation.
- Connect web screens to backend REST APIs.
- Build pages for school setup, assessment setup, analytics, and exports.
- Validate dashboard behavior for teacher and principal workflows.

**Expected output**  
Working web dashboard integrated with backend modules.

## Phase 14. Integration Testing

**Objective**  
Verify that backend, mobile, web, synchronization, and analytics work together correctly.

**Main tasks**
- Test API endpoints across completed modules.
- Validate sync flows between mobile and backend.
- Verify analytics results using synchronized data.
- Test module-to-module consistency and error handling.

**Expected output**  
Tested integrated system with resolved module interaction issues.

## Phase 15. Deployment Preparation

**Objective**  
Prepare the system for cloud deployment and final runtime configuration.

**Main tasks**
- Review deployment requirements for backend hosting.
- Prepare environment variables and database connection settings.
- Align the backend for future TiDB Cloud and Render deployment.
- Check production-ready build and configuration settings.

**Expected output**  
Deployment-ready backend and environment preparation checklist.

## Phase 16. Documentation and Final Revision

**Objective**  
Finalize project documents, review outputs, and prepare for capstone presentation.

**Main tasks**
- Update technical documentation and API notes.
- Finalize diagrams, module summaries, and testing records.
- Review code structure and cleanup remaining issues.
- Prepare final revision for submission and defense.

**Expected output**  
Complete capstone documentation set and final reviewed system build.
