package com.project.skin_me.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class JwtResponse {

    private Long id;
    private String jwtToken;
    /** Role names from user relationship (e.g. ROLE_ADMIN, ROLE_USER). */
    private Set<String> roles;
}
