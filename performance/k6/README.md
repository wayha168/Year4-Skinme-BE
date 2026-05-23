# K6 performance tests — SkinMe

Run with backend on port 8800 (`--spring.profiles.active=local` recommended).

```powershell
$env:BASE_URL="http://localhost:8800"
k6 run baseline.js
k6 run load-50.js
k6 run stress-ramp.js
```

Save JSON for graphs:

```powershell
mkdir -Force results
k6 run load-50.js -o json=results/load-50.json
```

Plot p95 vs VU using the stress-ramp stages in `docs/quality-engineering/05-performance-report.md`.
