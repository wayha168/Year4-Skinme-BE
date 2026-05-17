# TODO (POS delivery make-order flow)

- [ ] Update POS backend to support fulfillment type (PICKUP vs DELIVERY)
- [ ] Update POS receipt markdown to show correct fulfillment label (Pickup vs Delivery)
- [ ] Update POS payment completion logic:
  - [ ] Pickup: keep current behavior (mark delivered immediately)
  - [ ] Delivery: do not auto-mark shipped/delivered
- [ ] Add POS backend endpoint `POST /admin/pos/make-order/{orderId}` to create shipment for delivery orders and return receipt markdown
- [ ] Update `pos.html` UI:
  - [ ] Add Pickup/Delivery selector
  - [ ] Include fulfillment type in checkout/payment requests
  - [ ] After delivery payment success, show **Make order** button
  - [ ] Clicking **Make order** calls new endpoint and shows receipt modal (POS popup invoice style)
- [ ] Run `mvn test` (or at least `mvn -q -DskipTests=false test`) and do a manual verification checklist
