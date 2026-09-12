/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.billing.web;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFormConfigurationService;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@DisplayName("Generic billing form mutation method boundaries")
class BillingFormMutationMethodUnitTest extends CarlosUnitTestBase {
    static Stream<Arguments> rejectedMethods() {
        return Stream.of(DbManageBillingformAdd2Action.class, DbManageBillingformDelete2Action.class,
                DbManageBillingformDx2Action.class, DbManageBillingformPremium2Action.class,
                DbManageBillingformPremiumDelete2Action.class, DbManageBillingformService2Action.class)
                .flatMap(action -> Stream.of("GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE",
                        "post", "PoSt", "POſT").map(method -> Arguments.of(action, method)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("rejectedMethods")
    @DisplayName("should reject every non-POST billing form mutation before service or DAO access")
    void shouldRejectNonPost_beforeMutation(Class<? extends ActionSupport> type, String method) throws Exception {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        var services = createAndRegisterMock(CtlBillingServiceDao.class);
        var diagnoses = createAndRegisterMock(CtlDiagCodeDao.class);
        var premiums = createAndRegisterMock(CtlBillingServicePremiumDao.class);
        var configuration = createAndRegisterMock(BillingFormConfigurationService.class);
        var request = new MockHttpServletRequest(method, "/billing/mutation");
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(type.getConstructor().newInstance().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(security, services, diagnoses, premiums, configuration);
        }
    }
}
