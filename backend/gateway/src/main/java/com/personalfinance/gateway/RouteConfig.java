package com.personalfinance.gateway;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Static routing table (see DESIGN.md section 7). Service URLs are overridable
 * via environment (SERVICES_USER_URL etc.) so the same image works in
 * docker-compose and local runs. Routes are defined in Java rather than YAML to
 * stay independent of Spring Cloud property-name changes.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
            @Value("${services.user.url:http://localhost:8081}") String userUrl,
            @Value("${services.expense.url:http://localhost:8082}") String expenseUrl,
            @Value("${services.report.url:http://localhost:8083}") String reportUrl,
            @Value("${services.quest.url:http://localhost:8084}") String questUrl,
            @Value("${services.notification.url:http://localhost:8085}") String notificationUrl) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/me/**", "/api/v1/salary-calculator/**")
                        .uri(userUrl))
                .route("expense-service", r -> r
                        .path("/api/v1/expenses/**", "/api/v1/categories/**")
                        .uri(expenseUrl))
                .route("report-service", r -> r
                        .path("/api/v1/reports/**", "/api/v1/dashboard/**")
                        .uri(reportUrl))
                .route("quest-service", r -> r
                        .path("/api/v1/quests/**", "/api/v1/goals/**", "/api/v1/calendar/**")
                        .uri(questUrl))
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**", "/ws/**")
                        .uri(notificationUrl))
                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
