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
package io.github.carlos_emr.carlos.contactRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.ProfessionalSpecialistsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Authorization and JSON contract of the professional specialist lookups.
 *
 * <p>The Rich Text Letter's addressee box searches this directory, and the consultation request
 * form reads a chosen specialist's details from it. Both are clinical workflows: the doctor role
 * holds {@code _con} but not {@code _admin}, so requiring {@code _admin} here refused every
 * clinician who was not also an administrator. {@link ProfessionalSpecialistsManager} already
 * guards the same reads with {@code _con} read.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("security")
@DisplayName("ProfessionalSpecialist2Action")
class ProfessionalSpecialist2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private ProfessionalSpecialistsManager professionalSpecialistsManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/carlos/searchProfessionalSpecialist");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        securityInfoManager = mock(SecurityInfoManager.class);
        professionalSpecialistsManager = mock(ProfessionalSpecialistsManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProfessionalSpecialistsManager.class, professionalSpecialistsManager);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void grant(String objectName, boolean granted) {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq(objectName), eq("r"), isNull()))
                .thenReturn(granted);
    }

    private static ProfessionalSpecialist specialist(String lastName) {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setFirstName("FAKE-Alice");
        specialist.setLastName(lastName);
        specialist.setStreetAddressFromForm("123 Example St, Faketown");
        specialist.setFaxNumber("555-555-8001");
        return specialist;
    }

    @Test
    @DisplayName("should return the specialist search as JSON to a clinician who is not an administrator")
    void shouldReturnSearchJson_whenClinicianHasConsultationReadOnly() throws Exception {
        grant("_con", true);
        grant("_admin", false);
        request.setParameter("keyword", "FAKE-Spec");
        when(professionalSpecialistsManager.searchProfessionalSpecialist(loggedInInfo, "FAKE-Spec"))
                .thenReturn(List.of(specialist("FAKE-Specialist-01")));

        new ProfessionalSpecialist2Action().search();

        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"lastName\":\"FAKE-Specialist-01\"")
                .contains("\"streetAddress\":\"123 Example St, Faketown\"")
                .contains("\"fax\":\"555-555-8001\"");
    }

    @Test
    @DisplayName("should refuse the specialist search without consultation read, even to an administrator")
    void shouldRefuseSearch_whenConsultationReadMissing() {
        grant("_con", false);
        grant("_admin", true);
        request.setParameter("keyword", "FAKE-Spec");

        assertThatThrownBy(() -> new ProfessionalSpecialist2Action().search())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
        verify(professionalSpecialistsManager, never()).searchProfessionalSpecialist(any(), anyString());
    }

    @Test
    @DisplayName("should return one specialist as JSON to a clinician who is not an administrator")
    void shouldReturnSpecialistJson_whenClinicianHasConsultationReadOnly() throws Exception {
        grant("_con", true);
        grant("_admin", false);
        request.setParameter("id", "9002");
        when(professionalSpecialistsManager.getProfessionalSpecialist(loggedInInfo, 9002))
                .thenReturn(specialist("FAKE-Specialist-02"));

        new ProfessionalSpecialist2Action().get();

        assertThat(response.getContentAsString()).contains("\"lastName\":\"FAKE-Specialist-02\"");
    }

    @Test
    @DisplayName("should refuse one specialist without consultation read")
    void shouldRefuseGet_whenConsultationReadMissing() {
        grant("_con", false);
        grant("_admin", true);
        request.setParameter("id", "9002");

        assertThatThrownBy(() -> new ProfessionalSpecialist2Action().get())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
        verify(professionalSpecialistsManager, never()).getProfessionalSpecialist(any(), anyInt());
    }

    @Test
    @DisplayName("should refuse the legacy dispatch without consultation read")
    void shouldRefuseExecute_whenConsultationReadMissing() {
        grant("_con", false);
        grant("_admin", true);

        ProfessionalSpecialist2Action action = new ProfessionalSpecialist2Action();
        action.setActionType("/searchProfessionalSpecialist");

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
    }
}
