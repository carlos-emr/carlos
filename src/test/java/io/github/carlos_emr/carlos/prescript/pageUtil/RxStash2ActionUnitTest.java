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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Focused GET-rejection and stash-key coverage for {@link RxStash2Action} (issue #3871). This is
 * the conditional-mutator test the GET-rejection contract manifest requires: removing a staged
 * card is POST-only, while the stash-cursor dispatches stay verb-open.
 *
 * @since 2026-09-24
 */
@DisplayName("RxStash2Action stash removal")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxStash2ActionUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxSessionBean bean;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo("999998");
        bean.getStashList().add(staged(111111L, 55));
        bean.getStashList().add(staged(222222L, 0));
        bean.setStashIndex(1);
        putBeanInSession(bean);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /** Puts the bean where the action looks it up. */
    private void putBeanInSession(RxSessionBean rxBean) {
        RxSessionBeanResolver.register(request.getSession(), rxBean);
    }

    private static RxPrescriptionData.Prescription staged(long randomId, int drugReferenceId) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "999998", DEMOGRAPHIC_NO);
        rx.setRandomId(randomId);
        rx.setDrugReferenceId(drugReferenceId);
        return rx;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should reject a non-POST deletePrescribe without touching the stash")
    void shouldRejectDeletePrescribe_whenMethodIsNotPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.setParameter("parameterValue", "deletePrescribe");
        request.setParameter("randomId", "111111");

        String result = new RxStash2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("should reject a GET legacy action=delete without touching the stash")
    void shouldRejectLegacyDelete_whenMethodIsGet() throws Exception {
        request.setMethod("GET");
        request.setParameter("action", "delete");
        request.setParameter("stashId", "0");

        String result = new RxStash2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("should remove exactly the card whose random id was posted")
    void shouldRemoveCard_byStashRandomId() throws Exception {
        request.setParameter("parameterValue", "deletePrescribe");
        request.setParameter("randomId", "111111");

        new RxStash2Action().execute();

        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashItem(0).getRandomId()).isEqualTo(222222L);
    }

    @Test
    @DisplayName("should leave the stash alone when a drug id is posted instead of a random id")
    void shouldKeepStash_whenDrugIdPostedInsteadOfRandomId() throws Exception {
        // The pre-#3871 X button sent the ReRx source drug id (55) here. It never matches a stash
        // key, which is why the "closed" card was still saved; the fix sends the random id.
        request.setParameter("parameterValue", "deletePrescribe");
        request.setParameter("randomId", "55");

        new RxStash2Action().execute();

        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("should answer 400 when randomId is not a number")
    void shouldRejectRequest_whenRandomIdMalformed() throws Exception {
        request.setParameter("parameterValue", "deletePrescribe");
        request.setParameter("randomId", "not-a-number");

        String result = new RxStash2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("should still allow GET for the setStashIndex cursor move")
    void shouldAllowGet_forSetStashIndex() throws Exception {
        request.setMethod("GET");
        request.setParameter("parameterValue", "setStashIndex");
        request.setParameter("randomId", "111111");

        String result = new RxStash2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(bean.getStashIndex()).isZero();
    }
}
