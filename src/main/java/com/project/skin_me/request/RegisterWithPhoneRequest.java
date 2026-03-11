package com.project.skin_me.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterWithPhoneRequest {

    @NotBlank(message = "Phone verification token is required")
    private String phoneVerificationToken;

    @NotBlank(message = "Phone number is required")
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^[+]?[0-9\\s-]+$", message = "Invalid phone format")
    private String phone;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
