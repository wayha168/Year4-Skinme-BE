package com.project.skin_me.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyPhoneRequest {

    @NotBlank(message = "Phone number is required")
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^[+]?[0-9\\s-]+$", message = "Invalid phone format")
    private String phone;

    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 8)
    @Pattern(regexp = "^[0-9]+$", message = "OTP must be digits only")
    private String otp;
}
