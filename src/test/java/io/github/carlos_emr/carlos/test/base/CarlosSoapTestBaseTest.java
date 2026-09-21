/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.test.base;

import java.util.UUID;
import jakarta.jws.WebService;
import jakarta.xml.ws.soap.SOAPFaultException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.AbstractWs;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Pins SOAP marshalling, static-mock visibility, fault propagation and bus isolation. */
class CarlosSoapTestBaseTest extends CarlosSoapTestBase {
    @Mock private ProbeDependency dependency;
    private final String identity = UUID.randomUUID().toString();

    @Override protected Object getServiceBean() {
        registerMock(ProbeDependency.class, dependency);
        return new ProbeService(identity);
    }
    @Override protected Class<?> getServiceInterface() { return ProbeService.class; }

    @Test
    void shouldInvokeStaticMockOnCallerThread_throughSoap() {
        Thread caller = Thread.currentThread();
        when(dependency.value()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread()).isSameAs(caller);
            return "soap-wire-marker";
        });
        assertThat(createClient(ProbeService.class).dependency()).isEqualTo("soap-wire-marker");
    }

    @Test
    void shouldInjectAuthenticatedRequest_throughSoap() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        assertThat(createClient(ProbeService.class).auth()).isEqualTo("999998");
    }

    @Test
    void shouldPropagateServiceFailure_asSoapFault() {
        when(dependency.value()).thenThrow(new IllegalStateException("deliberate fixture failure"));
        ProbeService proxy = createClient(ProbeService.class);
        assertThatThrownBy(proxy::dependency).isInstanceOf(SOAPFaultException.class)
                .hasMessageContaining("deliberate fixture failure");
    }

    @Test
    void shouldKeepTwoInstancesIsolated_andReleaseOnlyTheirOwnResources() throws Exception {
        CarlosSoapTestBaseTest other = new CarlosSoapTestBaseTest();
        try {
            other.setUpSoapEndpoint();
            assertThat(other.getServiceAddress()).isNotEqualTo(getServiceAddress());
            assertThat(createClient(ProbeService.class).instance()).isEqualTo(identity);
            assertThat(other.createClient(ProbeService.class).instance()).isEqualTo(other.identity);
        } finally {
            other.tearDownSoapEndpoint();
        }
        assertThat(createClient(ProbeService.class).instance()).isEqualTo(identity);
    }

    public interface ProbeDependency { String value(); }

    @WebService
    public static class ProbeService extends AbstractWs {
        private final String identity;
        public ProbeService() { this(""); }
        public ProbeService(String identity) { this.identity = identity; }
        public String dependency() { return SpringUtils.getBean(ProbeDependency.class).value(); }
        public String auth() { return getLoggedInInfo().getLoggedInProviderNo(); }
        public String instance() { return identity; }
    }
}
