package com.personalfinance.expense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The gateway already rejects unauthenticated requests, but each service
 * verifies the JWT itself (zero-trust between containers). Validation uses the
 * user-service JWKS — no shared secrets.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${auth.jwks-uri:http://localhost:8081/api/v1/auth/jwks}") String jwksUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }
}
