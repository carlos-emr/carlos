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
package io.github.carlos_emr.carlos.admin.gate;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailFooterAuditPage;
import io.github.carlos_emr.carlos.integration.patientportal.PortalEmailFooterAuditService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ViewPortalEmailFooterAuditUnitTest {
    private final SecurityInfoManager security=mock(SecurityInfoManager.class);
    private final EmailFooterLogoService logo=mock(EmailFooterLogoService.class);
    private final ClinicEmailFooterService clinic=mock(ClinicEmailFooterService.class);
    private final PortalEmailFooterAuditService audit=mock(PortalEmailFooterAuditService.class);
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> context;
    private LoggedInInfo user;
    @BeforeEach void setup() {
        request=new MockHttpServletRequest("GET","/admin/ViewConfigureEmail");user=new LoggedInInfo();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),user);
        context=mockStatic(ServletActionContext.class);context.when(ServletActionContext::getRequest).thenReturn(request);
        when(clinic.clinicFooter()).thenReturn("FAKE Current Clinic");
    }
    @AfterEach void cleanup(){context.close();}
    private ViewConfigureEmail2Action action(){return new ViewConfigureEmail2Action(security,logo,clinic,audit,()->true);}
    private void authorize(){when(security.hasPrivilege(any(),eq("_admin"),eq("r"),isNull())).thenReturn(true);}
    @Test void shouldDenyNonAdmin_beforeConfigurationOrHistoricalLookup() {
        AtomicInteger checks=new AtomicInteger();
        var a=new ViewConfigureEmail2Action(security,logo,clinic,audit,()->{checks.incrementAndGet();return true;});
        assertThatThrownBy(a::execute).isInstanceOf(SecurityException.class);
        assertThat(checks).hasValue(0);verifyNoInteractions(audit,clinic,logo);
    }
    @Test void shouldRenderSavedHistoricalPage_withoutSubstitutingCurrentClinic() throws Exception {
        authorize();request.addParameter("portalFooterDate","2026-10-09");
        var row=new PortalEmailFooterAuditPage.Attempt("01791547200000000000-00000000000000000000000000000001",
                "mfa",java.time.Instant.parse("2026-10-09T01:00:00Z"),"unknown",
                java.time.Instant.parse("2026-10-09T01:00:01Z"),"a".repeat(64),"FAKE Saved Old Clinic",null);
        var history=new PortalEmailFooterAuditPage(LocalDate.of(2026,10,9),List.of(row),null);
        when(audit.read(any(),eq(history.date()),isNull())).thenReturn(history);
        assertThat(action().execute()).isEqualTo("success");
        assertThat(request.getAttribute("portalFooterAudit")).isSameAs(history);
        assertThat(history.attempts().get(0).footerText()).isEqualTo("FAKE Saved Old Clinic");
        assertThat(request.getAttribute("clinicFooter")).isEqualTo("FAKE Current Clinic");
    }
    @Test void shouldRefuseInvalidOrDuplicateDateAndCursor_beforePortal() throws Exception {
        authorize();
        for(String invalid:List.of("2026-02-30","2026-1-01","0000-01-01","2026-10-09&limit=100")){
            request.setParameter("portalFooterDate",invalid);action().execute();
            assertThat(request.getAttribute("portalFooterAuditError")).isEqualTo("invalid");
        }
        request.setParameter("portalFooterDate",new String[]{"2026-10-09","2026-10-08"});action().execute();
        request.setParameter("portalFooterDate","2026-10-09");request.setParameter("portalFooterBefore","../../private");action().execute();
        verifyNoInteractions(audit);
    }
    @Test void shouldShowUnavailable_withoutTurningFailureIntoEmptyHistory() throws Exception {
        authorize();request.addParameter("portalFooterDate","2026-10-09");
        when(audit.read(any(),any(),isNull())).thenThrow(new PatientPortalConfigurationException("missing configuration"));
        assertThat(action().execute()).isEqualTo("success");
        assertThat(request.getAttribute("portalFooterAuditError")).isEqualTo("unavailable");
        assertThat(request.getAttribute("portalFooterAudit")).isNull();
    }
    @Test void shouldOmitHistory_whenPortalIsOff_withoutClientLookup() throws Exception {
        authorize();var a=new ViewConfigureEmail2Action(security,logo,clinic,audit,()->false);a.execute();
        assertThat(request.getAttribute("portalFooterAuditEnabled")).isNull();verifyNoInteractions(audit);
    }
    @Test void shouldUseNativeCollapsedDetails_andEscapedSavedPlaintext() throws Exception {
        String jsp=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/portalFooterAudit.jspf"));
        var doc=org.jsoup.Jsoup.parse(jsp);
        assertThat(doc.select("details")).hasSize(2);
        assertThat(doc.select("details[open]")).isEmpty();
        assertThat(jsp).contains("<c:out value=\"${attempt.footerText}\"/>")
                .doesNotContain("escapeXml=\"false\"", "innerHTML", "${clinicFooter}", "<img");
    }
}
