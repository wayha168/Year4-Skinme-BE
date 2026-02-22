# Password Reset Flow Documentation

## Overview

This document describes the password reset functionality implemented in the SkinMe application. The system supports password reset for users who registered with email/password, while Google-authenticated users are directed to use Google login.

## Features

1. **Forgot Password**: Users can request a password reset by providing their email address
2. **Token-Based Reset**: Secure token-based password reset with 1-hour expiration
3. **Email Notification**: Password reset tokens are sent via email
4. **Google User Protection**: Prevents password reset for Google-only accounts
5. **Login Method Validation**: Ensures users use the correct login method (email/password or Google)

## Architecture

### Components

1. **User Model** (`User.java`)
   - `resetToken`: Stores the password reset token
   - `resetTokenExpiry`: Stores the token expiration timestamp

2. **EmailService** (`EmailService.java`)
   - Handles sending password reset emails
   - Generates reset links with tokens

3. **AuthService** (`AuthService.java`)
   - `forgotPassword()`: Generates reset token and sends email
   - `resetPassword()`: Validates token and resets password
   - `login()`: Validates login method (email/password vs Google)

## API Endpoints

### 1. Forgot Password

**Endpoint**: `POST /api/v1/auth/forgot-password`

**Request Parameters**:
```json
{
  "email": "user@example.com"
}
```

**Response** (Success):
```json
{
  "message": "Password reset email sent successfully",
  "data": null
}
```

**Response** (Google User):
```json
{
  "message": "This account was registered with Google. Please use Google login.",
  "data": null
}
```

**Response** (Email Not Found - Security):
```json
{
  "message": "If the email exists, a password reset link has been sent.",
  "data": null
}
```

**Flow**:
1. User submits email address
2. System checks if user exists
3. If user exists and is not Google-only:
   - Generates unique reset token (UUID)
   - Sets token expiry to 1 hour from now
   - Saves token to user record
   - Sends email with reset link and token
4. Returns success message (doesn't reveal if email exists)

### 2. Reset Password

**Endpoint**: `POST /api/v1/auth/reset-password`

**Request Parameters**:
```
email: user@example.com
token: <reset-token-from-email>
password: newPassword123
confirmPassword: newPassword123
```

**Response** (Success):
```json
{
  "message": "Password reset successful",
  "data": null
}
```

**Response** (Invalid Token):
```json
{
  "message": "Invalid or expired reset token",
  "data": null
}
```

**Response** (Expired Token):
```json
{
  "message": "Reset token has expired. Please request a new password reset.",
  "data": null
}
```

**Flow**:
1. User submits email, token, new password, and confirm password
2. System validates:
   - Token matches user's reset token
   - Token has not expired (within 1 hour)
   - Passwords match
3. If valid:
   - Encrypts new password
   - Clears reset token and expiry
   - Saves user
   - Records password reset activity
4. Returns success or error message

## Email Template

The password reset email contains:

**Subject**: Password Reset Request - SkinMe

**Body**:
```
Dear User,

You have requested to reset your password for your SkinMe account.

Please click on the following link to reset your password:
https://skinme.store/reset-password?token=<TOKEN>&email=<EMAIL>

Or use this token manually:
Token: <TOKEN>

This link will expire in 1 hour.

If you did not request this password reset, please ignore this email.

Best regards,
SkinMe Team
```

## Login Method Validation

### Email/Password Login

The `login()` method now validates the login method:

1. **Check User Existence**: Verifies user exists by email
2. **Google User Check**: 
   - If user has `googleId` but no password set
   - Returns error: "This account was registered with Google. Please use Google login."
3. **Normal Authentication**: Proceeds with standard email/password authentication

### Google Login

The `googleLogin()` method:
1. Validates Google OAuth token
2. Extracts user information from Google
3. Creates new user if doesn't exist (without password)
4. Updates Google ID if user exists but doesn't have it
5. Authenticates user and generates JWT

## Security Features

1. **Token Expiration**: Reset tokens expire after 1 hour
2. **One-Time Use**: Tokens are cleared after successful password reset
3. **Email Privacy**: Doesn't reveal if email exists in system
4. **Google User Protection**: Prevents password reset for Google-only accounts
5. **Password Encryption**: All passwords are encrypted using BCrypt

## Configuration

### Email Configuration (`application.properties`)

```properties
# SMTP Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:noreply@skinme.store}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Frontend URL
app.frontend.url=https://skinme.store
```

### Environment Variables

Set these environment variables for production:
- `MAIL_USERNAME`: Email address for sending emails
- `MAIL_PASSWORD`: Email password or app-specific password

## Database Schema

### User Table Updates

```sql
ALTER TABLE user ADD COLUMN reset_token VARCHAR(255);
ALTER TABLE user ADD COLUMN reset_token_expiry DATETIME;
```

## Frontend Integration

### Forgot Password Form

```javascript
// Request password reset
const forgotPassword = async (email) => {
  const response = await fetch('/api/v1/auth/forgot-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ email })
  });
  return response.json();
};
```

### Reset Password Form

```javascript
// Reset password with token
const resetPassword = async (email, token, password, confirmPassword) => {
  const formData = new URLSearchParams();
  formData.append('email', email);
  formData.append('token', token);
  formData.append('password', password);
  formData.append('confirmPassword', confirmPassword);
  
  const response = await fetch('/api/v1/auth/reset-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: formData
  });
  return response.json();
};
```

### URL Parsing

Extract token and email from reset link:
```javascript
const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');
const email = urlParams.get('email');
```

## Error Handling

### Common Errors

1. **Invalid Email**: Returns generic message (security)
2. **Google User**: Returns specific message directing to Google login
3. **Invalid Token**: Token doesn't match or user not found
4. **Expired Token**: Token older than 1 hour
5. **Email Send Failure**: Token cleared, user must request again

## Testing

### Test Cases

1. **Forgot Password - Valid Email**
   - Request reset for existing email/password user
   - Verify email sent
   - Verify token stored in database

2. **Forgot Password - Google User**
   - Request reset for Google-only user
   - Verify error message returned

3. **Reset Password - Valid Token**
   - Use valid token within expiry
   - Verify password updated
   - Verify token cleared

4. **Reset Password - Expired Token**
   - Use expired token
   - Verify error message
   - Verify token cleared

5. **Login - Google User with Email/Password**
   - Attempt email/password login for Google user
   - Verify error message

## Troubleshooting

### Email Not Sending

1. Check SMTP configuration in `application.properties`
2. Verify `MAIL_USERNAME` and `MAIL_PASSWORD` environment variables
3. Check email service logs for errors
4. Verify network connectivity to SMTP server

### Token Not Working

1. Verify token hasn't expired (check `resetTokenExpiry`)
2. Check token matches exactly (case-sensitive)
3. Verify user email matches token owner
4. Check database for token existence

### Google Login Issues

1. Verify Google OAuth credentials
2. Check redirect URI matches configuration
3. Verify user has `googleId` set in database
4. Check OAuth token validation

## Future Enhancements

1. **Rate Limiting**: Limit password reset requests per email/IP
2. **Email Templates**: HTML email templates with branding
3. **Token Refresh**: Option to resend reset email
4. **Password Strength**: Enforce password complexity rules
5. **Multi-Factor Authentication**: Add 2FA for password reset

## Support

For issues or questions, contact the development team or refer to the main project documentation.
