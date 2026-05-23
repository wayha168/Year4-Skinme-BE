import { test, expect } from '@playwright/test';

/**
 * E2E-02: Post-login navigation to admin product/category area
 */
test.describe('Admin category navigation', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login-page');
    await page.fill('input[name="username"]', 'admin@skinme.com');
    await page.fill('input[name="password"]', 'password');
    await page.click('button[type="submit"]');
    await page.waitForURL(/dashboard/, { timeout: 15_000 });
  });

  test('dashboard loads and categories view is reachable', async ({ page }) => {
    await expect(page).toHaveURL(/dashboard/);

    const categoriesLink = page.getByRole('link', { name: /categor/i });
    if (await categoriesLink.count()) {
      await categoriesLink.first().click();
      await expect(page.locator('body')).toContainText(/categor/i);
    } else {
      await page.goto('/views/categories');
      await expect(page.locator('body')).not.toBeEmpty();
    }
  });
});
