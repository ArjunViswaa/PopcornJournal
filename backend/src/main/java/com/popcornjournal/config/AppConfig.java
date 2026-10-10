package com.popcornjournal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** "Today" for dashboard statistics; replaced with a fixed clock in tests. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    WebMvcConfigurer corsConfigurer(@Value("${popcorn.cors.allowed-origins}") String[] allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                        .allowedHeaders("Content-Type", "Accept")
                        .maxAge(3600);
            }
        };
    }

    @Bean
    OpenAPI popcornOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Popcorn Journal API")
                .version("v1")
                .description("Personal movie journal: movies, viewing sessions, statistics and spreadsheet import."));
    }
}
