/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.signature.action;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.UUID;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SaveSignatureUpload2Action facility gate")
@Tag("unit")
@Tag("signature")
class SaveSignatureUpload2ActionFacilityGateUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldReturn403_whenFacilityDigitalSignaturesAreDisabled() throws Exception {
        Facility facility = mock(Facility.class);
        when(facility.isEnableDigitalSignatures()).thenReturn(false);

        assertUploadResult(facility, true, HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldReturn403_whenCurrentFacilityIsMissing() throws Exception {
        assertUploadResult(null, true, HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void shouldReturn200_whenPersistenceIsNotRequestedForDisabledFacility() throws Exception {
        Facility facility = mock(Facility.class);
        when(facility.isEnableDigitalSignatures()).thenReturn(false);

        assertUploadResult(facility, false, HttpServletResponse.SC_OK);
    }

    private void assertUploadResult(Facility facility, boolean saveToDB, int expectedStatus)
            throws Exception {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        DigitalSignatureManager digitalSignatureManager =
                createAndRegisterMock(DigitalSignatureManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String signatureKey = "facilitygate" + UUID.randomUUID().toString().replace("-", "");
        File tempFile = new File(DigitalSignatureUtils.getTempFilePath(signatureKey));

        request.setMethod("POST");
        request.setParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureKey);
        request.setParameter("source", "IPAD");
        request.setParameter(
                "signatureImage",
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABAQAAAAA3bvkkAAAAC0lEQVR42mNgAAIAAAUAAeImBZsAAAAASUVORK5CYII=");
        request.setParameter("saveToDB", Boolean.toString(saveToDB));

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("w"), isNull()))
                .thenReturn(true);
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
                MockedStatic<LoggedInInfo> loggedInInfoContext = mockStatic(LoggedInInfo.class)) {
            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            loggedInInfoContext
                    .when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);

            String result = new SaveSignatureUpload2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(expectedStatus);
            if (expectedStatus == HttpServletResponse.SC_OK) {
                assertThat(response.getContentAsString())
                        .contains("name=\"signatureId\"")
                        .contains("value=\"\"");
                assertThat(tempFile).exists();
                assertThat(tempFile.length()).isPositive();
            } else {
                assertThat(tempFile).doesNotExist();
            }
            verifyNoInteractions(digitalSignatureManager);
        } finally {
            tempFile.delete();
        }
    }
}
