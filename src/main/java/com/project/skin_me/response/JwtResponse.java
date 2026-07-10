package com.project.skin_me.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class JwtResponse {

    private Long id;
    private String jwtToken;
    private Set<String> roles;
}
