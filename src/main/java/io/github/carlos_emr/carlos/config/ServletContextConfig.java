package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;


/**
 * Spring configuration class that registers essential Servlets, Filters, and Listeners for the web application context.
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
        return this.servletContext;
    }
}