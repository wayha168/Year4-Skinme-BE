# TODO - KHQR POS UI fix (ABA logo + generate after click)

## Step 1 (Plan verification)
- [x] Identify POS KHQR flow in `src/main/resources/templates/pos.html`
- [x] Identify backend endpoints for KHQR: `/payment/generate-khqr` and `/payment/status/{orderId}`
- [x] Confirm gateway selection requirement: generate ABA KHQR with ABA logo

## Step 2 (Backend)
- [ ] Adjust KHQR generation response to include a stable `gatewayLogo` (or similar) used by POS UI.

## Step 3 (Frontend)
- [ ] Update `src/main/resources/templates/pos.html`:
  - [ ] Ensure KHQR generation uses the *fresh* `orderId` from `checkout()` (fix null/stale `pendingOrderId` issues)
  - [ ] Show ABA logo in KHQR modal
  - [ ] Ensure QR image is displayed when received

## Step 4 (Test)
- [ ] Run `mvn -DskipTests spring-boot:run`
- [ ] Test POS:
  - [ ] Add products
  - [ ] Click KHQR
  - [ ] Click “Generate QR & process”
  - [ ] Verify logo + QR show
  - [ ] Verify polling completes after webhook

