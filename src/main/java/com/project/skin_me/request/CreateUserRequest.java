package com.project.skin_me.request;

import com.project.skin_me.dto.RoleDto;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String confirmPassword;
    private RoleDto role;
}
