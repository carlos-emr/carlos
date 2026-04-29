/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReportControlViewModel;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BillingReportControlViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingReportControlViewModelAssemblerUnitTest {

    @Test
    void shouldTreatNullProviderDaoResultAsEmptyOptions() {
        ReportProviderDao reportProviderDao = mock(ReportProviderDao.class);
        when(reportProviderDao.search_reportprovider("billingreport")).thenReturn(null);

        BillingReportControlViewModel model =
                new BillingReportControlViewModelAssembler(reportProviderDao)
                        .assemble(new MockHttpServletRequest(), null);

        assertThat(model.getProviderOptions()).isEmpty();
    }
}
