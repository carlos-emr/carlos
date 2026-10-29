/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.utility.ClinicalStartupCoordinator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportMaintenanceFilterUnitTest {
    @Test void shouldAllowOnlySessionHeartbeatReadsDuringMaintenance() throws Exception {
        var service=mock(CdsImportMaintenanceService.class);
        for(String method:new String[]{"GET","HEAD"}) {
            for(String suffix:new String[]{"",".do",".action"}) {
                var request=new MockHttpServletRequest(method,"/carlos/status/SessionHeartbeat"+suffix);
                request.setContextPath("/carlos");
                var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
                new CdsImportMaintenanceFilter(()->service).doFilter(request,response,chain);
                verify(chain).doFilter(request,response);
            }
        }
        verifyNoInteractions(service);
        when(service.isClinicalAccessBlocked()).thenReturn(true);
        for(String method:new String[]{"POST","PUT","DELETE","PATCH","TRACE","OPTIONS"}) {
            var request=new MockHttpServletRequest(method,"/status/SessionHeartbeat");
            var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            new CdsImportMaintenanceFilter(()->service).doFilter(request,response,chain);
            assertThat(response.getStatus()).isEqualTo(503);verifyNoInteractions(chain);
        }
        for(String path:new String[]{"/status/SessionHeartbeat/extra","/status/SessionHeartbeat;ignored",
                "/status/%53essionHeartbeat","/status/Other"}) {
            var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            new CdsImportMaintenanceFilter(()->service).doFilter(new MockHttpServletRequest("GET",path),response,chain);
            assertThat(response.getStatus()).isEqualTo(503);verifyNoInteractions(chain);
        }
    }
    @Test
    void shouldHoldOrdinaryRequests_whenDeferredStartupIsIncomplete() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        ClinicalStartupCoordinator startup = mock(ClinicalStartupCoordinator.class);
        var request = new MockHttpServletRequest("GET", "/provider/providercontrol");
        request.getServletContext().setAttribute(ClinicalStartupCoordinator.ATTRIBUTE, startup);
        var response = new MockHttpServletResponse(); FilterChain chain = mock(FilterChain.class);
        new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("startup initialization is pending");
        verifyNoInteractions(chain);
        when(startup.isReady()).thenReturn(true);
        response = new MockHttpServletResponse();
        new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldKeepRecoveryAvailable_whenClinicalStartupIsDeferred() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        ClinicalStartupCoordinator startup = mock(ClinicalStartupCoordinator.class);
        for (String path : new String[] {"/login", "/form/importUpload", "/form/cdsImportStatus", "/form/cdsImportControl"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.getServletContext().setAttribute(ClinicalStartupCoordinator.ATTRIBUTE, startup);
            var response = new MockHttpServletResponse(); FilterChain chain = mock(FilterChain.class);
            new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        verifyNoInteractions(service, startup);
    }

    @Test
    void shouldReadTheFiveNativeLoginAssets_whenMaintenanceIsActive() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        for (String method : new String[] {"GET", "HEAD"}) {
            for (String path : new String[] {"/share/css/global.css", "/share/css/searchBox.css",
                    "/share/css/transitions.css", "/share/javascript/Oscar.js", "/share/javascript/carlos-ajax.js"}) {
                var request = new MockHttpServletRequest(method, "/carlos" + path);
                request.setContextPath("/carlos");
                var response = new MockHttpServletResponse();
                FilterChain chain = mock(FilterChain.class);
                new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
                verify(chain).doFilter(request, response);
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepSharedRoutesAndNoncanonicalAssetPathsClosed_whenMaintenanceIsActive() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        when(service.isClinicalAccessBlocked()).thenReturn(true);
        for (String path : new String[] {"/share/css/reporting.css", "/share/javascript/labFiling.js",
                "/share/letterheader.htm", "/share/javascript/Oscar.js.do", "/share/javascript/Oscar.js.action",
                "/share/javascript/Oscar.js/extra", "/share/css/../javascript/Oscar.js",
                "/share/%63ss/global.css", "/share/css/global.css;ignored", "/share//css/global.css"}) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
            assertThat(response.getStatus()).as(path).isEqualTo(503);
            verifyNoInteractions(chain);
        }
    }

    @Test
    void shouldKeepAssetMutationsClosed_whenMaintenanceIsActive() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        when(service.isClinicalAccessBlocked()).thenReturn(true);
        for (String method : new String[] {"POST", "PUT", "DELETE", "PATCH", "TRACE", "OPTIONS"}) {
            for (String path : new String[] {"/share/css/global.css", "/share/css/searchBox.css",
                    "/share/css/transitions.css", "/share/javascript/Oscar.js", "/share/javascript/carlos-ajax.js"}) {
                var request = new MockHttpServletRequest(method, path);
                var response = new MockHttpServletResponse();
                FilterChain chain = mock(FilterChain.class);
                new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
                assertThat(response.getStatus()).as(method + " " + path).isEqualTo(503);
                verifyNoInteractions(chain);
            }
        }
    }

    @Test
    void shouldDenyClinicalReadsAndDownloads_whenMaintenanceOrHoldsExist() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        when(service.isClinicalAccessBlocked()).thenReturn(true);
        for (String context : new String[] {"", "/carlos", "/<img src=x onerror=alert(1)>"}) {
            for (String path : new String[]{"/demographic/demographiccontrol", "/ws/PreventionService", "/Download",
                    "/eformViewForPdfGenerationServlet", "/provider/providercontrol"}) {
                var request = new MockHttpServletRequest("GET", context + path);
                request.setContextPath(context);
                var response = new MockHttpServletResponse();
                FilterChain chain = mock(FilterChain.class);
                new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
                assertThat(response.getStatus()).isEqualTo(503);
                assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getContentAsString()).contains("chart reconciliation", "/form/importUpload",
                        "application base address");
                if (!context.isEmpty()) assertThat(response.getContentAsString()).doesNotContain(context);
                verifyNoInteractions(chain);
            }
        }
    }

    @Test
    void shouldPreserveLoginStaticAndAuthorizedImporterRoutes_duringMaintenance() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        when(service.isClinicalAccessBlocked()).thenReturn(true);
        for (String path : new String[]{"/login", "/css/Roboto.css", "/form/importUpload", "/form/cdsImportStatus", "/form/cdsImportDiagnostic",
                "/WEB-INF/jsp/demographic/demographicImport.jsp"}) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        assertThat(CdsImportMaintenanceFilter.allowed("/css/../demographic/view")).isFalse();
        assertThat(CdsImportMaintenanceFilter.allowed("/css/%2e%2e/demographic/view")).isFalse();
    }

    @Test
    void shouldKeepOrdinaryClinicalRoutesUnchanged_whenNoWindowOrHoldExists() throws Exception {
        CdsImportMaintenanceService service = mock(CdsImportMaintenanceService.class);
        var request = new MockHttpServletRequest("POST", "/demographic/add");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new CdsImportMaintenanceFilter(() -> service).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
