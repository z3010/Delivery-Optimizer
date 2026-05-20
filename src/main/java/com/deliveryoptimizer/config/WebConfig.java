package com.deliveryoptimizer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Content-Type", "Cache-Control")
            .maxAge(3600);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // SSE connections can live for up to 5 minutes
        configurer.setDefaultTimeout(300_000L);
    }
}
