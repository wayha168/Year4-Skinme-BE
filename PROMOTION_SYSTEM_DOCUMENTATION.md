# Promotion System Documentation

## Overview

A complete promotion management system that allows admins to create, read, update, and delete promotions with automatic discount calculation. Promotions are linked to products and include deadline management.

## Features

1. **CRUD Operations**: Full Create, Read, Update, Delete functionality
2. **Deadline Management**: Set start date and deadline for promotions
3. **Product Linking**: Link promotions to specific products
4. **Automatic Discount Calculation**: Calculates discounted price based on percentage
5. **Dashboard Interface**: HTML dashboard with table for managing promotions
6. **API Response Format**: All endpoints return consistent ApiResponse format

## Database Schema

### Promotion Table

```sql
CREATE TABLE promotions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    link VARCHAR(500),
    discount_percentage DECIMAL(5,2) NOT NULL,
    deadline DATETIME NOT NULL,
    start_date DATETIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    product_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    FOREIGN KEY (product_id) REFERENCES product(id)
);
```

## API Endpoints

### Base URL: `/api/v1/promotions`

#### 1. Create Promotion
- **Endpoint**: `POST /api/v1/promotions/create`
- **Access**: Admin only
- **Request Body**:
  ```json
  {
    "title": "Summer Sale",
    "description": "Get 20% off on all products",
    "link": "https://example.com/summer-sale",
    "discountPercentage": 20.00,
    "startDate": "2026-02-20T00:00:00",
    "deadline": "2026-03-20T23:59:59",
    "productId": 1,
    "active": true
  }
  ```
- **Response**:
  ```json
  {
    "message": "Promotion created successfully",
    "data": {
      "id": 1,
      "title": "Summer Sale",
      "discountPercentage": 20.00,
      "discountedPrice": 80.00,
      "originalPrice": 100.00,
      ...
    }
  }
  ```

#### 2. Get Promotion by ID
- **Endpoint**: `GET /api/v1/promotions/{id}`
- **Response**: `ApiResponse` with PromotionDto

#### 3. Get All Promotions
- **Endpoint**: `GET /api/v1/promotions/all`
- **Response**: `ApiResponse` with List<PromotionDto>

#### 4. Get Active Promotions
- **Endpoint**: `GET /api/v1/promotions/active`
- **Response**: `ApiResponse` with List<PromotionDto> (only currently active)

#### 5. Get Active Promotion by Product
- **Endpoint**: `GET /api/v1/promotions/product/{productId}`
- **Response**: `ApiResponse` with PromotionDto

#### 6. Get Discounted Price
- **Endpoint**: `GET /api/v1/promotions/product/{productId}/discounted-price`
- **Response**:
  ```json
  {
    "message": "Discounted price calculated successfully",
    "data": 80.00
  }
  ```

#### 7. Update Promotion
- **Endpoint**: `PUT /api/v1/promotions/{id}`
- **Access**: Admin only
- **Request Body**: Same as Create (all fields optional)

#### 8. Delete Promotion
- **Endpoint**: `DELETE /api/v1/promotions/{id}`
- **Access**: Admin only
- **Response**: `ApiResponse` with success message

## HTML Dashboard

### URL: `/views/promotions`

**Features**:
- Create/Edit form with validation
- Product selection dropdown
- Discount percentage input with real-time preview
- Date/time pickers for start date and deadline
- Table displaying all promotions
- Edit and Delete buttons for each promotion
- Automatic discount calculation preview
- Status indicators (Active/Inactive, Expired)

**Form Fields**:
- Title (required, max 200 chars)
- Product selection (required)
- Description (optional)
- Discount Percentage (required, 0.01-100)
- Start Date (required, datetime-local)
- Deadline (required, datetime-local)
- Link (optional, URL)
- Active checkbox

## Discount Calculation

The system automatically calculates discounted prices:

```java
discountedPrice = originalPrice - (originalPrice × discountPercentage / 100)
```

**Example**:
- Original Price: $100.00
- Discount: 20%
- Discounted Price: $80.00
- Savings: $20.00

## Promotion Status

A promotion is considered **currently active** if:
1. `active` flag is `true`
2. Current date is after `startDate`
3. Current date is before `deadline`

## Model Structure

### Promotion Model
- `id`: Long (Primary Key)
- `title`: String (required, max 200)
- `description`: String (TEXT)
- `link`: String (max 500)
- `discountPercentage`: BigDecimal (required, 0.01-100)
- `deadline`: LocalDateTime (required)
- `startDate`: LocalDateTime (required)
- `active`: boolean (default: true)
- `product`: Product (ManyToOne relationship)
- `createdAt`: LocalDateTime (auto-generated)
- `updatedAt`: LocalDateTime (auto-updated)

### Methods
- `calculateDiscountedPrice()`: Calculates discounted price for the product
- `isCurrentlyActive()`: Checks if promotion is currently active

## Service Layer

### PromotionService Methods

1. `createPromotion(CreatePromotionRequest)`: Creates new promotion
2. `updatePromotion(Long, UpdatePromotionRequest)`: Updates existing promotion
3. `getPromotionById(Long)`: Gets promotion by ID
4. `getAllPromotions()`: Gets all promotions (ordered by creation date)
5. `getActivePromotions()`: Gets currently active promotions
6. `getActivePromotionByProductId(Long)`: Gets active promotion for a product
7. `deletePromotion(Long)`: Deletes a promotion
8. `calculateDiscountedPrice(Long)`: Calculates discounted price for a product
9. `convertToDto(Promotion)`: Converts entity to DTO

## Validation Rules

1. **Title**: Required, max 200 characters
2. **Discount Percentage**: Required, between 0.01 and 100.00
3. **Start Date**: Required, cannot be in the past
4. **Deadline**: Required, must be after start date
5. **Product ID**: Required, product must exist
6. **Link**: Optional, max 500 characters, must be valid URL format

## Usage Examples

### Create Promotion via API

```bash
curl -X POST http://localhost:8800/api/v1/promotions/create \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "title": "Black Friday Sale",
    "description": "50% off on selected items",
    "discountPercentage": 50.00,
    "startDate": "2026-11-25T00:00:00",
    "deadline": "2026-11-30T23:59:59",
    "productId": 1,
    "active": true
  }'
```

### Get Discounted Price

```bash
curl http://localhost:8800/api/v1/promotions/product/1/discounted-price
```

### Access Dashboard

Navigate to: `http://localhost:8800/views/promotions`

## Integration with Products

Products now have a `promotions` relationship:
- One product can have multiple promotions
- Promotions are linked via `product_id` foreign key
- When fetching products, you can access their promotions

## Security

- All create/update/delete operations require `ROLE_ADMIN`
- Read operations are accessible to authenticated users
- Validation prevents invalid data entry

## Error Handling

All endpoints return consistent error format:
```json
{
  "message": "Error description",
  "data": null
}
```

Common HTTP Status Codes:
- `200 OK` - Success
- `201 CREATED` - Promotion created
- `400 BAD_REQUEST` - Invalid input data
- `404 NOT_FOUND` - Promotion/Product not found
- `500 INTERNAL_SERVER_ERROR` - Server error

## Files Created

1. **Model**: `Promotion.java`
2. **Repository**: `PromotionRepository.java`
3. **Service Interface**: `IPromotionService.java`
4. **Service Implementation**: `PromotionService.java`
5. **REST Controller**: `PromotionController.java`
6. **View Controller**: `PromotionViewController.java`
7. **Request DTOs**: `CreatePromotionRequest.java`, `UpdatePromotionRequest.java`
8. **Response DTO**: `PromotionDto.java`
9. **HTML Template**: `promotions.html`

## Next Steps

1. Add promotion code/coupon functionality
2. Add promotion usage tracking
3. Add bulk promotion creation
4. Add promotion analytics
5. Add email notifications for new promotions
6. Add promotion banners on product pages
