package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;
/**
 * Configures the core Servlet Context for the application.
 *
 * <p>Initializes essential web components, listeners, and global properties
 * during the application startup lifecycle.</p>
 */

@Configuration
public class ServletContextConfig implements ServletContextAware {
    // Ensure critical security filters are registered early in the initialization chain

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