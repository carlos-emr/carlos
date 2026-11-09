/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every mapped BC endpoint refuses ON before invoking its action. @since 2026-11-09 */
@Tag("unit")
class BcBillingProvinceInterceptorUnitTest {
    static Stream<String> bcRoutes() throws Exception {
        String xml=Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml"));
        var matcher=java.util.regex.Pattern.compile("<action\\s+name=\"(billing/CA/BC/[^\"*]+)\"").matcher(xml);
        var names=new java.util.LinkedHashSet<String>(); while(matcher.find()) names.add(matcher.group(1));
        names.add("quickBillingBC"); names.add("saveQuickBillingBC");
        return names.stream();
    }

    @ParameterizedTest @MethodSource("bcRoutes")
    void shouldRefuseEveryMappedBcRoute_beforeInvokingItsActionOnOntario(String route) throws Exception {
        var invocation=mock(ActionInvocation.class); var proxy=mock(ActionProxy.class);
        when(invocation.getProxy()).thenReturn(proxy); when(proxy.getActionName()).thenReturn(route);
        var response=new MockHttpServletResponse(); var properties=mock(CarlosProperties.class);
        when(properties.getProperty("billregion","")).thenReturn("ON");
        try(var config=mockStatic(CarlosProperties.class);var servlet=mockStatic(ServletActionContext.class)) {
            config.when(CarlosProperties::getInstance).thenReturn(properties); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new BcBillingProvinceInterceptor().intercept(invocation)).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(404); verify(invocation,never()).invoke();
        }
    }

    @Test
    void shouldRetainTheGenericProvinceRouter_onOntario() throws Exception {
        var invocation=mock(ActionInvocation.class); var proxy=mock(ActionProxy.class);
        when(invocation.getProxy()).thenReturn(proxy); when(proxy.getActionName()).thenReturn("billing");
        when(invocation.invoke()).thenReturn("ON");
        assertThat(new BcBillingProvinceInterceptor().intercept(invocation)).isEqualTo("ON");
    }
}
