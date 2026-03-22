package io.github.carlos_emr.carlos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

/**
 * Spring configuration class that exposes the {@link ServletContext} as a Spring-managed bean.
 *
 * <p>Implements {@link ServletContextAware} to receive the servlet context during
 * application startup, then publishes it as a {@code @Bean} so that other components
 * can inject the {@link ServletContext} directly via Spring dependency injection.</p>
 *
 * @since 2026-03-17
 */
@Configuration
public class ServletContextConfig implements ServletContextAware {

    private ServletContext servletContext;

    /**
     * Receives the {@link ServletContext} from the Spring container during initialization.
     *
     * @param servletContext ServletContext the web application's servlet context
     */
    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     * Exposes the {@link ServletContext} as a Spring bean for dependency injection.
     *
     * @return ServletContext the current web application's servlet context
     */
    @Bean
    public ServletContext servletContext() {
        return this.servletContext;
    }
}