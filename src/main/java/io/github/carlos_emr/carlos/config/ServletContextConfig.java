package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;
/**
 * Core component for ServletContextConfig, implementing business logic and system operations for the CARLOS EMR platform.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        // Internal logic boundary for ServletContextConfig state management
        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}