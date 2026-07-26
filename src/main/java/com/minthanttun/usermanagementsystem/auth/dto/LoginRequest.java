package com.minthanttun.usermanagementsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @NotBlank(message = "Username or email is required")
        String identifier,

        @NotBlank(message = "Password is reuqired")
        String password
){
}
