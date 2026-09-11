package com.minthanttun.usermanagementsystem.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name= "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI userManagementSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Management System API")
                        .version("1.0.0")
                        .description("REST API for user registration, authentication, and account management.")
                        .contact(new Contact()
                                .name("Min Thant Tun")));
    }
}
