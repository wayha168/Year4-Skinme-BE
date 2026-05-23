# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin-category.spec.ts >> Admin category navigation >> dashboard loads and categories view is reachable
- Location: tests\admin-category.spec.ts:15:7

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
  4  |  * E2E-02: Post-login navigation to admin product/category area
  5  |  */
  6  | test.describe('Admin category navigation', () => {
  7  |   test.beforeEach(async ({ page }) => {
> 8  |     await page.goto('/login-page');
     |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:8800/login-page
  9  |     await page.fill('input[name="username"]', 'admin@skinme.com');
  10 |     await page.fill('input[name="password"]', 'password');
  11 |     await page.click('button[type="submit"]');
  12 |     await page.waitForURL(/dashboard/, { timeout: 15_000 });
  13 |   });
  14 | 
  15 |   test('dashboard loads and categories view is reachable', async ({ page }) => {
  16 |     await expect(page).toHaveURL(/dashboard/);
  17 | 
  18 |     const categoriesLink = page.getByRole('link', { name: /categor/i });
  19 |     if (await categoriesLink.count()) {
  20 |       await categoriesLink.first().click();
  21 |       await expect(page.locator('body')).toContainText(/categor/i);
  22 |     } else {
  23 |       await page.goto('/views/categories');
  24 |       await expect(page.locator('body')).not.toBeEmpty();
  25 |     }
  26 |   });
  27 | });
  28 | 
```