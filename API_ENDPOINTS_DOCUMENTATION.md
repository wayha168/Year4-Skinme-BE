# API Endpoints Documentation

## Language Switching (i18n)

API responses support **English (en)** and **Khmer (km)**. To get responses in Khmer, use either:

1. **Accept-Language header** (recommended):
   ```
   Accept-Language: km
   ```

2. **Query parameter** (for GET requests):
   ```
   GET /api/v1/delivery/track/1?lang=km
   ```

Example with `curl`:
```bash
# English (default)
curl -H "Authorization: Bearer <token>" https://backend.skinme.store/api/v1/delivery/track/1

# Khmer
curl -H "Authorization: Bearer <token>" -H "Accept-Language: km" https://backend.skinme.store/api/v1/delivery/track/1
```

---

All API endpoints return `ApiResponse` format with the following structure:
```json
{
  "message": "Success message or error description",
  "data": { ... } // Response data or null
}
```

---

## Postman & Frontend Integration

### Base URL
- Local: `http://localhost:8080`
- API prefix: `/api/v1` (all endpoints below are under this prefix)

### Authentication (for secured endpoints)
Most payment, order, cart, and delivery endpoints require a JWT. After **Login**, use the token from the response:

1. **Login** (no auth): `POST /api/v1/auth/login`  
   Body: `{ "email": "user@example.com", "password": "your_password" }`  
   Response: `{ "message": "...", "data": { "id": 1, "jwtToken": "<token>", "roles": ["ROLE_USER"] } }`

2. **Use token in requests**:  
   Header: `Authorization: Bearer <jwtToken>`

### Postman collection
Import the collection from the repo to test all endpoints in Postman:

- **File**: `postman/Skinme_API.postman_collection.json`
- **Collection variables**: `baseUrl` (e.g. `http://localhost:8080`), `token` (set automatically after **Login** if you use the collection’s Login request).

### Frontend examples (JavaScript fetch)

**Login and store token:**
```javascript
const res = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'user@example.com', password: 'your_password' })
});
const { data } = await res.json();
const token = data.jwtToken; // use in subsequent requests
```

**Generate KHQR (authenticated). Use `gateway=aba` for ABA Mobile or `gateway=khqr` for generic KHQR:**
```javascript
const orderId = 1, amount = 50, currency = 'USD', gateway = 'aba';
const res = await fetch(
  `http://localhost:8080/api/v1/payment/generate-khqr?orderId=${orderId}&amount=${amount}&currency=${currency}&gateway=${gateway}`,
  { headers: { 'Authorization': `Bearer ${token}` } }
);
const { data } = await res.json();
// data.qrImage (base64), data.qrData, data.merchantName, data.amount, data.currency
```

**Create payment intent (Stripe):**
```javascript
const res = await fetch('http://localhost:8080/api/v1/payment/create-payment-intent', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
  body: JSON.stringify({ orderId: 1, amountCents: 5000, cardholderName: 'John Doe' })
});
const { data } = await res.json();
// data.clientSecret for Stripe.js
```

**Update delivery address:**
```javascript
await fetch(`http://localhost:8080/api/v1/delivery/address/${orderId}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
  body: JSON.stringify({
    deliveryStreet: '123 Main St',
    deliveryCity: 'Phnom Penh',
    deliveryProvince: 'Phnom Penh',
    deliveryPostalCode: '12000',
    deliveryLatitude: 11.5564,
    deliveryLongitude: 104.9282,
    deliveryAddressFull: '123 Main St, Phnom Penh, Cambodia'
  })
});
```

**Public endpoints (no auth):**  
`/api/v1/auth/login`, `/api/v1/auth/signup`, `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/payment/verify-success`, `/api/v1/payment/webhook`

---

## Delivery Address Management API

### Base URL: `/api/v1/delivery`

#### 1. Create Shipment
- **Endpoint**: `POST /api/v1/delivery/ship/{orderId}`
- **Description**: Create shipment for an order after payment success
- **Response**: 
  ```json
  {
    "message": "Shipment created successfully",
    "data": { Order object }
  }
  ```

#### 2. Mark Order as Delivered
- **Endpoint**: `POST /api/v1/delivery/delivered/{orderId}`
- **Description**: Mark an order as delivered
- **Response**:
  ```json
  {
    "message": "Order marked as delivered",
    "data": { Order object }
  }
  ```

#### 3. Track Order
- **Endpoint**: `GET /api/v1/delivery/track/{orderId}`
- **Description**: Get tracking information for an order
- **Response**:
  ```json
  {
    "message": "Tracking information retrieved",
    "data": { Order object }
  }
  ```

#### 4. Update Delivery Address (CREATE/UPDATE)
- **Endpoint**: `PUT /api/v1/delivery/address/{orderId}`
- **Description**: Update or create delivery address for an order
- **Request Body**:
  ```json
  {
    "deliveryStreet": "123 Main Street",
    "deliveryCity": "Phnom Penh",
    "deliveryProvince": "Phnom Penh",
    "deliveryPostalCode": "12000",
    "deliveryLatitude": 11.5564,
    "deliveryLongitude": 104.9282,
    "deliveryAddressFull": "123 Main Street, Phnom Penh, Cambodia"
  }
  ```
- **Response**:
  ```json
  {
    "message": "Delivery address updated successfully",
    "data": { Order object }
  }
  ```

#### 5. Get Delivery Address (READ)
- **Endpoint**: `GET /api/v1/delivery/address/{orderId}`
- **Description**: Retrieve delivery address for an order
- **Response**:
  ```json
  {
    "message": "Delivery address retrieved successfully",
    "data": {
      "orderId": 1,
      "deliveryStreet": "123 Main Street",
      "deliveryCity": "Phnom Penh",
      "deliveryProvince": "Phnom Penh",
      "deliveryPostalCode": "12000",
      "deliveryAddressFull": "123 Main Street, Phnom Penh, Cambodia",
      "deliveryLatitude": 11.5564,
      "deliveryLongitude": 104.9282
    }
  }
  ```

#### 6. Clear Delivery Address (DELETE)
- **Endpoint**: `DELETE /api/v1/delivery/address/{orderId}`
- **Description**: Clear/remove delivery address from an order
- **Response**:
  ```json
  {
    "message": "Delivery address cleared successfully",
    "data": { Order object }
  }
  ```

## Order Management API

### Base URL: `/api/v1/orders`

#### 1. Create Order
- **Endpoint**: `POST /api/v1/orders/order?userId={userId}`
- **Response**: `ApiResponse` with OrderDto

#### 2. Get Order by ID
- **Endpoint**: `GET /api/v1/orders/{orderId}`
- **Response**: `ApiResponse` with OrderDto

#### 3. Get User Orders
- **Endpoint**: `GET /api/v1/orders/user/{userId}`
- **Response**: `ApiResponse` with List<OrderDto>

#### 4. Get All Orders (Admin)
- **Endpoint**: `GET /api/v1/orders/all`
- **Response**: `ApiResponse` with List<OrderDto>

#### 5. Mark as Shipped
- **Endpoint**: `PUT /api/v1/orders/{orderId}/ship?trackingNumber={trackingNumber}`
- **Response**: `ApiResponse` with OrderDto

#### 6. Mark as Delivered
- **Endpoint**: `PUT /api/v1/orders/{orderId}/deliver`
- **Response**: `ApiResponse` with OrderDto

#### 7. Update Delivery Address (Deprecated)
- **Endpoint**: `PUT /api/v1/orders/{orderId}/delivery-address`
- **Status**: Deprecated - Use `/api/v1/delivery/address/{orderId}` instead
- **Response**: `ApiResponse` with OrderDto

## Payment API

### Base URL: `/api/v1/payment`

#### 1. Create Checkout Session
- **Endpoint**: `POST /api/v1/payment/create-checkout-session/{userId}`
- **Response**: `ApiResponse` with checkout URL

#### 2. Create Payment Intent
- **Endpoint**: `POST /api/v1/payment/create-payment-intent`
- **Response**: `ApiResponse` with clientSecret

#### 3. Confirm Payment
- **Endpoint**: `POST /api/v1/payment/confirm-payment/{paymentIntentId}`
- **Response**: `ApiResponse` with payment status

#### 4. Generate KHQR QR Code
- **Endpoint**: `GET /api/v1/payment/generate-khqr?orderId={orderId}&amount={amount}&currency={currency}&gateway={gateway}`
- **Query params**: `orderId` (long), `amount` (number), `currency` (USD or KHR), `gateway` (optional: `aba` for ABA Mobile, `khqr` for generic KHQR; default `aba`). For KHR, pass amount in Riel.
- **Auth**: Bearer token required.
- **Response**: 
  ```json
  {
    "message": "KHQR QR code generated successfully",
    "data": {
      "qrData": "000201...",
      "qrImage": "data:image/png;base64,...",
      "amount": "100.00",
      "currency": "USD",
      "merchantName": "SkinMe Store",
      "gateway": "aba",
      "orderId": 1,
      "paymentId": 1
    }
  }
  ```

#### 5. Verify KHQR Payment
- **Endpoint**: `POST /api/v1/payment/verify-khqr/{orderId}`
- **Response**: `ApiResponse` with verification status

#### 6. Record Payment (API / Webhook input)
- **Endpoint**: `POST /api/v1/payment/record`
- **Description**: Record or update a payment (e.g. from external gateway webhook or manual input). Optionally stores card holder name, last 4 digits, and card brand.
- **Request Body** (JSON):
  - `orderId` (optional if transactionRef provided): order ID
  - `transactionRef` (optional): external reference (e.g. Stripe session/payment intent id)
  - `amount` (optional): payment amount
  - `status`: `PENDING`, `SUCCESS`, `PAYMENT_PENDING`, etc.
  - `method` (optional): `CREDIT_CARD`, `KHQR`, etc. (used when creating new payment)
  - `cardHolderName` (optional): card holder name
  - `cardLast4` (optional): last 4 digits of card
  - `cardBrand` (optional): e.g. visa, mastercard
  - `message` (optional): note
- **Response**: `ApiResponse` with `paymentId`, `orderId`, `status`

#### 7. Stripe Webhook
- **Endpoint**: `POST /api/v1/payment/webhook`
- **Response**: `ApiResponse` with processed events

## HTML Views

### Order Details Page
- **URL**: `/views/orders/{orderId}`
- **Template**: `order-details.html`
- **Displays**:
  - Order information
  - Order items
  - **Delivery address** (if available)
  - Order status
  - Tracking information
  - Map link for delivery location

### Checkout Page
- **URL**: `/checkout/{orderId}`
- **Template**: `checkout.html`
- **Features**:
  - Google Maps integration for address selection
  - Delivery address form
  - Payment method selection (Stripe/KHQR)
  - Address validation before payment

## CRUD Operations Summary

### Delivery Address CRUD

| Operation | Method | Endpoint | Status |
|-----------|--------|----------|--------|
| Create | PUT | `/api/v1/delivery/address/{orderId}` | ✅ |
| Read | GET | `/api/v1/delivery/address/{orderId}` | ✅ |
| Update | PUT | `/api/v1/delivery/address/{orderId}` | ✅ |
| Delete | DELETE | `/api/v1/delivery/address/{orderId}` | ✅ |

### Order CRUD

| Operation | Method | Endpoint | Status |
|-----------|--------|----------|--------|
| Create | POST | `/api/v1/orders/order` | ✅ |
| Read | GET | `/api/v1/orders/{orderId}` | ✅ |
| Read All | GET | `/api/v1/orders/all` | ✅ |
| Update Status | PUT | `/api/v1/orders/{orderId}/ship` | ✅ |
| Update Status | PUT | `/api/v1/orders/{orderId}/deliver` | ✅ |

### Payment CRUD

| Operation | Method | Endpoint | Status |
|-----------|--------|----------|--------|
| Create Intent | POST | `/api/v1/payment/create-payment-intent` | ✅ |
| Create Session | POST | `/api/v1/payment/create-checkout-session/{userId}` | ✅ |
| Generate KHQR | GET | `/api/v1/payment/generate-khqr` | ✅ |
| Verify Payment | POST | `/api/v1/payment/verify-khqr/{orderId}` | ✅ |
| Confirm Payment | POST | `/api/v1/payment/confirm-payment/{paymentIntentId}` | ✅ |

## Error Responses

All endpoints return consistent error format:
```json
{
  "message": "Error description",
  "data": null
}
```

Common HTTP Status Codes:
- `200 OK` - Success
- `400 BAD_REQUEST` - Invalid request
- `401 UNAUTHORIZED` - Authentication required
- `404 NOT_FOUND` - Resource not found
- `500 INTERNAL_SERVER_ERROR` - Server error

## Notes

1. All API endpoints return `ApiResponse` format consistently
2. HTML templates display delivery address data when available
3. Delivery address CRUD operations are fully implemented
4. Google Maps integration available on checkout page
5. Payment methods: Stripe (Credit Card) and KHQR supported
