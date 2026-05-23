# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin-login.spec.ts >> Admin Login >> invalid password shows error
- Location: tests\admin-login.spec.ts:20:7

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:8800/login-page
Call log:
  - navigating to "http://localhost:8800/login-page", waiting until "load"

```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | /**
  4  |  * E2E-01: Admin login flow (Thymeleaf session)
  5  |  */
  6  | test.describe('Admin Login', () => {
  7  |   test('valid admin credentials redirect to dashboard', async ({ page }) => {
  8  |     await page.goto('/login-page');
  9  |     await expect(page).toHaveURL(/login-page/);
  10 | 
  11 |     await page.fill('input[name="username"]', 'admin@skinme.com');
  12 |     await page.fill('input[name="password"]', 'password');
  13 |     await page.click('button[type="submit"]');
  14 | 
  15 |     await page.waitForURL(/dashboard/, { timeout: 15_000 });
  16 |     await expect(page).toHaveURL(/dashboard/);
  17 |     await expect(page.locator('body')).toContainText(/dashboard|Dashboard|Skin/i);
  18 |   });
  19 | 
  20 |   test('invalid password shows error', async ({ page }) => {
> 21 |     await page.goto('/login-page');
     |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:8800/login-page
  22 |     await page.fill('input[name="username"]', 'admin@skinme.com');
  23 |     await page.fill('input[name="password"]', 'not-the-password');
  24 |     await page.click('button[type="submit"]');
  25 | 
  26 |     await expect(page).toHaveURL(/login-page/);
  27 |   });
  28 | });
  29 | 
```