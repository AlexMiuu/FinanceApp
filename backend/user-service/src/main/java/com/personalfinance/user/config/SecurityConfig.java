package com.personalfinance.user.config;

import com.personalfinance.user.entity.RoleEnum;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Google login is optional: this bean (and with it the whole oauth2Login
     * machinery) only exists when GOOGLE_CLIENT_ID is configured.
     */
    @Bean
    @ConditionalOnExpression("!'${auth.google.client-id:}'.isEmpty()")
    ClientRegistrationRepository clientRegistrationRepository(AuthProperties authProperties) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(authProperties.google().clientId())
                .clientSecret(authProperties.google().clientSecret())
                .redirectUri("{baseUrl}/api/v1/auth/oauth/callback/{registrationId}")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<OAuthLoginSuccessHandler> successHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/**", "/actuator/**").permitAll()
                        // Offered without an account as a way in for people who have
                        // never signed up. SalaryController takes no principal — the
                        // reply is arithmetic over the posted amount alone, reading no
                        // record and storing nothing — so there is no per-user data to
                        // leak here. The gateway throttles it per caller address.
                        .requestMatchers(HttpMethod.POST, "/api/v1/salary-calculator").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(e -> e.baseUri("/api/v1/auth/oauth"))
                    .redirectionEndpoint(e -> e.baseUri("/api/v1/auth/oauth/callback/*"))
                    .successHandler(successHandler.getObject())
                    .failureUrl("/?login=error"));
        }

        return http.build();
    }
}
