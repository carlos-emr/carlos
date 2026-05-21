package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.WebappShutdownResources;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@Tag("unit")
class StartupUnitTest extends CarlosUnitTestBase {

    @Test
    @Tag("delete")
    void shouldUseThreadContextClassLoader_whenDestroyedWithNullEvent() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = new ClassLoader(original) {
        };

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(fallback))
                    .thenReturn(shutdownReport());
            Thread.currentThread().setContextClassLoader(fallback);

            new Startup().contextDestroyed(null);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(fallback));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    @Tag("delete")
    void shouldUseThreadContextClassLoader_whenServletContextIsNull() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = new ClassLoader(original) {
        };
        ServletContextEvent event = mock(ServletContextEvent.class);

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(fallback))
                    .thenReturn(shutdownReport());
            Thread.currentThread().setContextClassLoader(fallback);

            new Startup().contextDestroyed(event);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(fallback));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    @Tag("delete")
    void shouldUseServletContextClassLoader_whenAvailable() throws Exception {
        ClassLoader webappClassLoader = new ClassLoader(getClass().getClassLoader()) {
        };
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        org.mockito.Mockito.when(event.getServletContext()).thenReturn(servletContext);
        org.mockito.Mockito.when(servletContext.getClassLoader()).thenReturn(webappClassLoader);

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(webappClassLoader))
                    .thenReturn(shutdownReport());

            new Startup().contextDestroyed(event);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(webappClassLoader));
        }

        verify(event).getServletContext();
    }

    private WebappShutdownResources.ShutdownReport shutdownReport() throws Exception {
        Constructor<WebappShutdownResources.ShutdownReport> constructor =
                WebappShutdownResources.ShutdownReport.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of());
    }
}
