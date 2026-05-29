package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

/**
 * Configuration setup and bean initialization for ServletContext components.
 */
@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Bean

    // Servletcontext is exposed here to satisfy the external component interface contract without exposing internal state.
    public ServletContext servletContext() {
        return this.servletContext;
    }
}