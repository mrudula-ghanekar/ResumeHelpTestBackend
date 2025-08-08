package com.resumehelp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**") // Allow all API routes
                        .allowedOrigins("https://ai-resume-frontend-mg.vercel.app") // Your Vercel frontend
                        .allowedMethods("GET", "POST", "OPTIONS") // Allow POST & OPTIONS
                        .allowedHeaders("*") // All headers
                        .allowCredentials(true); // Allow cookies if needed
            }
        };
    }
}
