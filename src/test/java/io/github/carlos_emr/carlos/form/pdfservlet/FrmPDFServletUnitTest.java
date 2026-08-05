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
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.ByteArrayOutputStream;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization contract for the standard-form PDF servlet.
 *
 * <p>This servlet is mapped at {@code /form/createpdf} and is not exempt from {@code LoginFilter},
 * so authentication was enforced — but authentication is not authorization. The patient whose form
 * is rendered comes straight from the {@code demographic_no} request parameter and is handed to
 * {@code FrmRecord.getFormRecord}, which issues raw SQL with no gate of its own. Any authenticated
 * user, including a role holding no {@code _form} privilege at all, could therefore stream any
 * patient's stored form as a PDF — and the audit line is written only after the bytes are sent.</p>
 */
@DisplayName("FrmPDFServlet unit tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
class FrmPDFServletUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void registerSecurity() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
    }

    /** A request carrying an authenticated session, which is all the pre-existing gate required. */
    private MockHttpServletRequest authenticatedRequest(String demographicNo) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/createpdf");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        request.getSession().setAttribute(loggedInInfo.LOGGED_IN_INFO_KEY, loggedInInfo);
        request.setParameter("demographic_no", demographicNo);
        return request;
    }

    @Test
    @DisplayName("should refuse to render a form for a demographic the caller may not read")
    void shouldRefuseRender_whenDemographicNotReadable() {
        FrmPDFServlet servlet = new HeaderOnlyFrmPDFServlet();
        MockHttpServletRequest request = authenticatedRequest("999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> servlet.doPost(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_form");
        // No bytes may reach the client: the point is that PHI is withheld, not merely that an
        // exception is raised somewhere after the stream has started.
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should pass the gate when the caller may read the requested demographic")
    void shouldPassGate_whenDemographicReadable() {
        // The positive control. Without it, a check that denied unconditionally would satisfy the
        // test above while breaking every legitimate form print.
        //
        // Asserts the GATE, not the rendered bytes. Generation continues past the gate into
        // LogAction.addLogSynchronous, which needs Spring beans this unit JVM has none of; doPost
        // catches that and sendError()s, resetting the buffer. Asserting on the body would therefore
        // be asserting on an unrelated environmental failure, and would go red for reasons that have
        // nothing to do with authorization.
        FrmPDFServlet servlet = new HeaderOnlyFrmPDFServlet();
        MockHttpServletRequest request = authenticatedRequest("123");
        when(securityInfoManager.hasPrivilege(
                nullable(LoggedInInfo.class), eq("_form"), eq("r"), eq("123"))).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> servlet.doPost(request, response))
                .doesNotThrowAnyException();
        verify(securityInfoManager).hasPrivilege(
                nullable(LoggedInInfo.class), eq("_form"), eq("r"), eq("123"));
    }

    @Test
    @DisplayName("should scope the privilege check to the requested patient rather than any patient")
    void shouldScopeCheck_toRequestedDemographic() {
        // Granting read on one patient must not release another's form. An unscoped check — passing
        // null as the demographic, which is the easy mistake here — would pass this test's setup and
        // then serve patient 999, so this pins the scoping specifically.
        FrmPDFServlet servlet = new HeaderOnlyFrmPDFServlet();
        when(securityInfoManager.hasPrivilege(
                nullable(LoggedInInfo.class), eq("_form"), eq("r"), eq("123"))).thenReturn(true);
        MockHttpServletRequest request = authenticatedRequest("999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> servlet.doPost(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_form");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should reject an unauthenticated request before any privilege lookup")
    void shouldReject_whenNoAuthenticatedSession() throws Exception {
        FrmPDFServlet servlet = new HeaderOnlyFrmPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/createpdf");
        request.setParameter("demographic_no", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doPost(request, response);

        assertThat(response.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    /**
     * Replaces the OpenPDF generation so these tests exercise the gate rather than form rendering,
     * mirroring {@code EFormPDFServletUnitTest}'s HeaderOnlyEFormPDFServlet.
     */
    private static final class HeaderOnlyFrmPDFServlet extends FrmPDFServlet {
        @Override
        protected ByteArrayOutputStream generatePDFDocumentBytes(
                HttpServletRequest req, ServletContext ctx, ByteArrayOutputStream baosPDF, int multiple) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(new byte[] {1, 2, 3, 4}, 0, 4);
            return output;
        }
    }
}
