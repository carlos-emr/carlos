/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

/**
 * Uses the shipped configuration and real CSRF validator/filter before the action boundary.
 *
 * @since 2026-10-17
 */
@Tag("integration") @Tag("security")
class Alpha20MutationCsrfIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings={"/report/ViewReportonbilledvisitprovider","/admin/UpdateDemographicProvider","/CaseManagementEntry",
            "/billing/CA/BC/SaveBilling", "/billing/CA/BC/UpdateBilling"})
    void shouldRejectMissingAndWrongTokensButAllowValidPost(String path) throws Exception {
        Properties properties=new Properties();
        try(var input=Files.newInputStream(Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties"))) {properties.load(input);}
        properties.setProperty("org.owasp.csrfguard.configuration.provider.factory","org.owasp.csrfguard.config.PropertiesConfigurationProviderFactory");
        CsrfGuard.load(properties);
        var guard=CsrfGuard.getInstance();var filter=new CarlosCsrfGuardFilter();
        var request=new MockHttpServletRequest("GET",path);request.setServletPath(path);request.getSession();
        var reached=new AtomicBoolean();
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->reached.set(true));
        assertThat(reached).isTrue();
        var session=guard.getLogicalSessionExtractor().extract(request);
        String token=guard.getTokenService().getTokenValue(session.getKey(),path);
        assertThat(token).isNotBlank();request.setMethod("POST");
        for(String invalid:new String[]{"", "invalid-token"}) {
            reached.set(false);request.removeParameter(guard.getTokenName());
            if(!invalid.isEmpty()) request.setParameter(guard.getTokenName(),invalid);
            var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->reached.set(true));
            assertThat(reached).isFalse();assertThat(response.getStatus()).isEqualTo(403);
        }
        reached.set(false);request.setParameter(guard.getTokenName(),token);
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->reached.set(true));
        assertThat(reached).isTrue();assertThat(response.getStatus()).isEqualTo(200);
    }
}
