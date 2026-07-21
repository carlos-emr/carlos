package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

@Configuration
/**
 * Configuration class for servlet context initialization.
 * Responsible for registering and configuring servlets, filters, and listeners
 * programmatically during the application startup phase.
 */
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}