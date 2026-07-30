package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

/**
 * Configuration class for ServletContext settings.
 *
 * This class is part of the CARLOS EMR system.
 */


@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        // Initialize logic for setServletContext operation in CARLOS EMR

        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}