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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.NoSuchElementException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The detach gate: a caller without global {@code _con} read is refused before the
 * consultation is looked up, so the 403/404 difference cannot reveal which request ids exist.
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("consultation")
@Tag("security")
@DisplayName("ConsultationHrmDetach2Action access gate")
class ConsultationHrmDetach2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oscarEncounter/detachConsultationHrm");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private SecurityInfoManager security;
    private ConsultationHrmAttachmentPersister persister;
    private MockedStatic<ServletActionContext> servletActionContext;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        persister = createAndRegisterMock(ConsultationHrmAttachmentPersister.class);
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        request.setParameter("requestId", "77");
        request.setParameter("associationId", "5");
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @ParameterizedTest(name = "requestId {0}")
    @ValueSource(strings = {"77", "999999"})
    @DisplayName("should answer 403 without looking the consultation up when the caller lacks global _con read")
    void shouldRefuseBeforeLookup_whenGlobalConsultationReadMissing(String requestId) throws Exception {
        request.setParameter("requestId", requestId);
        when(security.hasPrivilege(any(), eq("_con"), eq(SecurityInfoManager.READ), (String) isNull())).thenReturn(false);

        String result = new ConsultationHrmDetach2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        // The same answer for an existing and a missing id: nothing was looked up.
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(persister);
    }

    @Test
    void shouldRequirePost_beforeCheckingPrivileges() throws Exception {
        request.setMethod("GET");
        new ConsultationHrmDetach2Action().execute();

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(security, persister);
    }

    @Test
    void shouldDelegatePatientScopedWriteCheck_whenCallerHasConsultationRead() throws Exception {
        when(security.hasPrivilege(any(), eq("_con"), eq(SecurityInfoManager.READ), (String) isNull())).thenReturn(true);

        new ConsultationHrmDetach2Action().execute();

        assertThat(response.getStatus()).isEqualTo(204);
        verify(persister).detach(any(), eq(77), eq(5));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldGiveSameResponse_whenConsultationIsMissingOrInaccessible(boolean missing) throws Exception {
        when(security.hasPrivilege(any(), eq("_con"), eq(SecurityInfoManager.READ), (String) isNull())).thenReturn(true);
        doThrow(missing ? new NoSuchElementException() : new SecurityException())
                .when(persister).detach(any(), eq(77), eq(5));

        new ConsultationHrmDetach2Action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2147483648", "missing"})
    void shouldRejectInvalidAssociation_beforeDetaching(String id) throws Exception {
        when(security.hasPrivilege(any(), eq("_con"), eq(SecurityInfoManager.READ), (String) isNull())).thenReturn(true);
        request.setParameter("associationId", id);

        new ConsultationHrmDetach2Action().execute();

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(persister);
    }
}
