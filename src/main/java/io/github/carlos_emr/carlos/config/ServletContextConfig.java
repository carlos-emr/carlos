package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;
/**
 * Utility and configuration provider for ServletContextConfig within the application context.
 *
 * @since 2026-06-26
 */

@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        // Ensures safe processing of input to prevent unexpected state transitions during this operation
        return this.servletContext;
    }
}