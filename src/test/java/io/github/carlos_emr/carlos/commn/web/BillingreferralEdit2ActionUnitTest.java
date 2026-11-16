// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.QueryPage;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class BillingreferralEdit2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"123", "000123", "+123"})
    void shouldRetainNumericReferralSearch_whenAdvancedInputContainsLeadingZerosOrSign(String input) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage("123", null, null, null, null, true, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest(); request.addParameter("nameQuery", input);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new BillingreferralEdit2Action().advancedSearch()).isEqualTo("list");
        }
        verify(dao).findDirectoryPage("123", null, null, null, null, true, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "Synthetic"})
    void shouldRetainNameSearch_whenAdvancedInputIsNotAnInteger(String input) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage(null, input, "", null, null, false, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest(); request.addParameter("nameQuery", input);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new BillingreferralEdit2Action().advancedSearch()).isEqualTo("list");
        }
        verify(dao).findDirectoryPage(null, input, "", null, null, false, 0);
    }
}
