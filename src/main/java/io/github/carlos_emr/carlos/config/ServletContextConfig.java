package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;


/**
 * Configures the ServletContext for the CARLOS EMR application.
 * <p>
 * Initializes required servlet parameters, context listeners, and filters
 * necessary for application startup and request processing.
 * </p>
 */
@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    @Override
    public void setServletContext(ServletContext servletContext) {
        // Bind servlet context parameters required for initialization of the Spring container and application components.
        this.servletContext = servletContext;
    }

    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}