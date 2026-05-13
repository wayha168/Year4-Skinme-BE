# Exploratory Testing Assignment - Individual

Student Name: ____________________  
Student ID: ____________________  
Course/Section: ____________________  
Application Under Test: Skinme Backend Web Admin (UI forms/modules)  
Environment: ____________________  
Date: ____________________

---

## Session 1 Sheet

### Step 1: Test Charter
**EXPLORE Authentication and Access Control Forms WITH Boundary Value Analysis, Error Guessing, and Role-Switch Testing TO DISCOVER input-validation weaknesses, unauthorized access risks, and confusing failure behavior.**

### Session Setup
- **Timebox:** 45 minutes (start: ____ / end: ____)
- **Feature Name:** Login + Password Reset
- **Target URL:** `https://backend.skinme.store/auth/login` and `https://backend.skinme.store/reset-password`
- **Screenshot:** Insert screenshot of login/reset-password page here (required)

### Step 2: Session Execution Log (Actions + Timestamps)
| Timestamp | Action Performed | Tactic/Technique Used | Expected Result | Actual Result | Notes |
|---|---|---|---|---|---|
| 00:00 | Open login page and inspect visible fields/messages | Baseline survey | Page loads with valid controls |  |  |
| 05:00 | Submit empty username/password | Error guessing | Validation errors shown |  |  |
| 10:00 | Test min/max length inputs | Boundary value analysis | Rejected outside limits |  |  |
| 15:00 | Try special chars/SQL-like payload strings | Attack simulation | Safely rejected/sanitized |  |  |
| 20:00 | Attempt repeated failed logins | Abuse/DoS heuristic | Lockout/rate-limit behavior |  |  |
| 25:00 | Test forgot/reset flow with invalid token/email | Negative path tour | Clear errors; no leakage |  |  |
| 30:00 | Login as different roles (if available) | Role-switch testing | Proper route/permission handling |  |  |
| 35:00 | Browser refresh/back during auth flow | State transition testing | Consistent auth state |  |  |
| 40:00 | Retest suspicious behavior | Focused follow-up | Stable response |  |  |
| 45:00 | Stop exactly at timebox end | Session discipline | Session closed |  |  |

### Unusual Behaviors / Risks Observed
- 1. ____________________________________________
- 2. ____________________________________________
- 3. ____________________________________________

### Step 3: Debrief (PROOF)
- **P (Past):**  
  What I did: ______________________________________________
- **R (Results):**  
  Bugs/observations found: ______________________________________________
- **O (Obstacles):**  
  Blocks/slowdowns: ______________________________________________
- **O (Outlook):**  
  Quality assessment: ______________________________________________
- **F (Feelings):**  
  Confidence/anxiety level and why: ______________________________________________

---

## Session 2 Sheet

### Step 1: Test Charter
**EXPLORE Product and Catalog Management Forms WITH Data Variation Matrices, CRUD Tour, and Pairwise Input Combinations TO DISCOVER data-integrity risks, save/update failures, and workflow inconsistencies.**

### Session Setup
- **Timebox:** 45 minutes (start: ____ / end: ____)
- **Feature Name:** Product Form + Category/Brand Mapping
- **Target URL:** `https://backend.skinme.store/products` and `https://backend.skinme.store/product-form`
- **Screenshot:** Insert screenshot of product form/list page here (required)

### Step 2: Session Execution Log (Actions + Timestamps)
| Timestamp | Action Performed | Tactic/Technique Used | Expected Result | Actual Result | Notes |
|---|---|---|---|---|---|
| 00:00 | Open products list and create form | Baseline survey | Components render correctly |  |  |
| 05:00 | Create minimal valid product | CRUD tour | Record created successfully |  |  |
| 10:00 | Create product with large text and max numbers | Boundary values | Stored/validated correctly |  |  |
| 15:00 | Use pairwise combinations (category, brand, status, price) | Pairwise | No illegal combinations accepted |  |  |
| 20:00 | Upload invalid/oversized image (if supported) | Error guessing | Rejected with clear message |  |  |
| 25:00 | Edit product and verify reflected updates | CRUD tour | Accurate update persistence |  |  |
| 30:00 | Delete/archive and verify list consistency | Data lifecycle test | Correct record state |  |  |
| 35:00 | Rapid repeated save / double-click submit | Concurrency heuristic | No duplicate insertion |  |  |
| 40:00 | Filter/search products after changes | Workflow tour | Results consistent with data |  |  |
| 45:00 | Stop exactly at timebox end | Session discipline | Session closed |  |  |

### Unusual Behaviors / Risks Observed
- 1. ____________________________________________
- 2. ____________________________________________
- 3. ____________________________________________

### Step 3: Debrief (PROOF)
- **P (Past):**  
  What I did: ______________________________________________
- **R (Results):**  
  Bugs/observations found: ______________________________________________
- **O (Obstacles):**  
  Blocks/slowdowns: ______________________________________________
- **O (Outlook):**  
  Quality assessment: ______________________________________________
- **F (Feelings):**  
  Confidence/anxiety level and why: ______________________________________________

---

## Session 3 Sheet

### Step 1: Test Charter
**EXPLORE Order/Payment/Checkout Workflow WITH End-to-End Scenario Chaining, State Transition Testing, and Interruption/Recovery Techniques TO DISCOVER transaction integrity issues, status-sync defects, and resilience gaps.**

### Session Setup
- **Timebox:** 45 minutes (start: ____ / end: ____)
- **Feature Name:** Checkout + Orders + Payments
- **Target URL:** `https://backend.skinme.store/checkout`, `https://backend.skinme.store/orders`, and `https://backend.skinme.store/payments`
- **Screenshot:** Insert screenshot of checkout/order/payment page here (required)

### Step 2: Session Execution Log (Actions + Timestamps)
| Timestamp | Action Performed | Tactic/Technique Used | Expected Result | Actual Result | Notes |
|---|---|---|---|---|---|
| 00:00 | Start from checkout with valid cart/order data | End-to-end chaining | Flow starts normally |  |  |
| 05:00 | Complete happy path payment | Scenario test | Status changes correctly |  |  |
| 10:00 | Retry with invalid card/payment payload | Negative flow | Safe failure with no bad state |  |  |
| 15:00 | Interrupt during payment (refresh/back/close) | Interruption/recovery | System recovers safely |  |  |
| 20:00 | Resume flow and verify idempotency | State transition | No double charge/order |  |  |
| 25:00 | Check order details and payment details pages | Traceability tour | Data consistency across modules |  |  |
| 30:00 | Trigger delayed/failed payment status (if possible) | Fault injection heuristic | Correct pending/failed handling |  |  |
| 35:00 | Cross-check audit/activity logs (if available) | Forensic tour | Events recorded accurately |  |  |
| 40:00 | Re-run risky path one more time | Confirmation pass | Stable and repeatable behavior |  |  |
| 45:00 | Stop exactly at timebox end | Session discipline | Session closed |  |  |

### Unusual Behaviors / Risks Observed
- 1. ____________________________________________
- 2. ____________________________________________
- 3. ____________________________________________

### Step 3: Debrief (PROOF)
- **P (Past):**  
  What I did: ______________________________________________
- **R (Results):**  
  Bugs/observations found: ______________________________________________
- **O (Obstacles):**  
  Blocks/slowdowns: ______________________________________________
- **O (Outlook):**  
  Quality assessment: ______________________________________________
- **F (Feelings):**  
  Confidence/anxiety level and why: ______________________________________________

---

## Final Submission Checklist

- [ ] Three unique charters are defined and each uses different tactics.
- [ ] Each session is timeboxed to exactly 45 minutes.
- [ ] Actions, timestamps, and unusual behaviors are logged.
- [ ] Each sheet explicitly includes:
  - [ ] Feature Name
  - [ ] Target URL
  - [ ] Screenshot
- [ ] PROOF section completed for all 3 sessions.
- [ ] All three completed sheets merged into one PDF.

## Note on Success

This assignment is information-focused. Finding no bug can still be a successful exploratory session if advanced tactics were used and the feature remained stable.
