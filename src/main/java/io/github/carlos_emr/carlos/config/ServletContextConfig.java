package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

@Configuration
/**
 * Configures application-wide ServletContext attributes upon context initialization.
 * Often used to bind system-wide resources or configuration paths early in the
 * application lifecycle.
 */
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        // Process standard operational requirements ensuring context-specific compliance

        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}