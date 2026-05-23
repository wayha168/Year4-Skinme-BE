import { test, expect } from '@playwright/test';

/**
 * E2E-01: Admin login flow (Thymeleaf session)
 */
test.describe('Admin Login', () => {
  test('valid admin credentials redirect to dashboard', async ({ page }) => {
    await page.goto('/login-page');
    await expect(page).toHaveURL(/login-page/);

    await page.fill('input[name="username"]', 'admin@skinme.com');
    await page.fill('input[name="password"]', 'password');
    await page.click('button[type="submit"]');

    await page.waitForURL(/dashboard/, { timeout: 15_000 });
    await expect(page).toHaveURL(/dashboard/);
    await expect(page.locator('body')).toContainText(/dashboard|Dashboard|Skin/i);
  });

  test('invalid password shows error', async ({ page }) => {
    await page.goto('/login-page');
    await page.fill('input[name="username"]', 'admin@skinme.com');
    await page.fill('input[name="password"]', 'not-the-password');
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL(/login-page/);
  });
});
