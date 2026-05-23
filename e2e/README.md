# SkinMe E2E (Playwright)

Requires backend running on port 8800 with seeded admin.

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"

cd e2e
npm install
npx playwright install chromium
$env:BASE_URL="http://localhost:8800"
npm test
```

Reports: `playwright-report/` after run.
