# SkinMe Testing Results - Slide Content

Date checked: 2026-05-12

## Slide 1: Project Overview

SkinMe is a Spring Boot e-commerce backend and web admin system for skincare product management. The system includes authentication, product/category/brand management, cart and checkout, order and payment tracking, KHQR/Stripe payment support, promotions, feedback, notifications, WebSocket updates, and uploaded product images.

Recommended image:
`docs/skinme-api-db-service-flow.png`

Image description:
Architecture diagram showing the database entities and API/service flow. Use it on the methodology or system architecture slide.

## Slide 2: Testing Objective

The testing objective was to verify that the backend application can compile, start its Spring application context, connect to a local test database, initialize required default data, and keep the main application wiring valid.

Testing approach:
- Automated smoke test with JUnit and Spring Boot.
- Local H2 in-memory database profile to avoid depending on remote MySQL.
- Repository, service, controller, security, WebSocket, and configuration beans loaded through the application context.

## Slide 3: Automated Test Result

Command used:

```powershell
mvn test '-Dspring.profiles.active=local'
```

Result:

```text
Tests run: 1
Failures: 0
Errors: 0
Skipped: 0
Build status: SUCCESS
```

Description for slide:
The automated Spring Boot smoke test passed successfully. This confirms that the application can compile, load the full Spring context, create the local H2 schema, initialize default roles/users, and start the backend configuration without startup errors.

## Slide 4: Issue Found and Fixed

Issue found:
The first local test run failed because H2 treats `USER` as a reserved keyword, while the project uses a `User` entity/table.

Fix applied:
Added `NON_KEYWORDS=USER` to the local H2 datasource URL in `src/main/resources/application-local.properties`.

Result after fix:
The test passed with 1 test run, 0 failures, and 0 errors.

## Slide 5: Exploratory Testing Plan

Manual exploratory testing is prepared in:
`src/test/Assignment testing individual/Exploratory_Testing_Assignment.md`

Recommended manual sessions:
- Authentication and access control: login, reset password, invalid input, role switching.
- Product and catalog management: create, update, image upload, category/brand mapping.
- Checkout, orders, and payments: happy path, invalid payment, refresh/interruption, status consistency.

## Slide 6: Images to Put in Slides

1. `docs/skinme-api-db-service-flow.png`
   - Size: 1376 x 768
   - Best for: architecture, methodology, backend flow.
   - Description: shows entity relationships and API/service/database flow.

2. `uploads/Madagascar Centella Cream.webp`
   - Size: 1500 x 1500
   - Best for: product/catalog feature slide.
   - Description: sample skincare product image used by the upload/product image feature.

3. `uploads/3d786a20-fd7d-43c5-89fe-cd996d6c8271.png`
   - Size: 1638 x 2048
   - Best for: product image or UI upload evidence slide.

4. `uploads/502739125_122112046172862017_5120576653890986647_n.jpg`
   - Size: 1280 x 1280
   - Best for: sample uploaded image slide.

5. `src/main/resources/static/assets/skin_me_logo.gif`
   - Size: 2000 x 480
   - Best for: title slide or footer branding.

## Slide 7: Final Testing Summary

Final result:
The backend automated smoke test passed after fixing the local H2 profile. The project is ready for slide presentation as a working Spring Boot backend with API, database, payment, order, product, image, and notification features.

Remaining recommendation:
Add more automated tests for controllers, services, checkout/payment workflows, authentication, and product image upload validation so the test coverage goes beyond the current startup smoke test.

Important security note:
Some configuration secrets appear to be stored directly in `application.properties`. Before deployment or public submission, move sensitive keys and tokens into environment variables and rotate any exposed credentials.
