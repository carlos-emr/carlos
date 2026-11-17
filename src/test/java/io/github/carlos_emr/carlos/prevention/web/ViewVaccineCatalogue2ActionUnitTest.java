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
package io.github.carlos_emr.carlos.prevention.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("fast")
@Tag("prevention")
@DisplayName("ViewVaccineCatalogue2Action")
class ViewVaccineCatalogue2ActionUnitTest extends CarlosUnitTestBase {

    private final CanadianVaccineCatalogueManager catalogue = mock(CanadianVaccineCatalogueManager.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo admin = mock(LoggedInInfo.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/prevention/ViewVaccineCatalogue");
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    private ViewVaccineCatalogue2Action action;

    @BeforeEach
    void setUp() {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(admin);
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.READ, null)).thenReturn(true);
        action = new ViewVaccineCatalogue2Action(security, catalogue);
    }

    @AfterEach
    void tearDown() {
        session.close();
        servlet.close();
    }

    @Test
    void shouldExposeInstalledCatalogueSummary_forAdminReader() {
        when(catalogue.getImmunizationList()).thenReturn(List.of(vaccine(true), vaccine(true), vaccine(false)));
        when(catalogue.getLastUpdated()).thenReturn("2026-09-24 10:15");
        when(catalogue.getInstalledVersion()).thenReturn("1.130");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("catalogueGenericCount")).isEqualTo(2L);
        assertThat(request.getAttribute("catalogueTradenameCount")).isEqualTo(1L);
        assertThat(request.getAttribute("catalogueLastUpdated")).isEqualTo("2026-09-24 10:15");
        assertThat(request.getAttribute("catalogueVersion")).isEqualTo("1.130");
        assertThat(request.getAttribute("catalogueSourceUrl")).isEqualTo(CanadianVaccineCatalogueManager.getCVCURL());
        assertThat(request.getAttribute("canUpdate")).isEqualTo(false);
    }

    @Test
    void shouldOfferUpdate_whenAdminHasWrite() {
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);

        action.execute();

        assertThat(request.getAttribute("canUpdate")).isEqualTo(true);
    }

    @Test
    void shouldPassKnownResult_fromUpdateRedirect() {
        request.setParameter("result", "unavailable");

        action.execute();

        assertThat(request.getAttribute("updateResult")).isEqualTo("unavailable");
    }

    @Test
    void shouldIgnoreUnknownResult_fromQueryString() {
        request.setParameter("result", "<script>alert(1)</script>");

        action.execute();

        assertThat(request.getAttribute("updateResult")).isNull();
    }

    @Test
    void shouldThrowSecurityException_whenMissingAdminRead() {
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.READ, null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(catalogue);
    }

    private static CVCImmunization vaccine(boolean generic) {
        CVCImmunization immunization = new CVCImmunization();
        immunization.setGeneric(generic);
        return immunization;
    }
}
