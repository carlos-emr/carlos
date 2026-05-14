/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewLoader;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused tests for OHIP MRI view-model assembly guardrails.
 *
 * <p>The year parameter feeds date-range queries. Invalid years must fail before DAO access so
 * operators see a load error instead of an apparently empty OHIP history table.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BillingOnMriViewModelAssembler")
class BillingOnMriViewModelAssemblerTest {

    @Test
    @DisplayName("should reject malformed year before billing queries")
    void shouldRejectMalformedYear_beforeBillingQueries() {
        ProviderDao providerDao = mock(ProviderDao.class);
        BillActivityDao billActivityDao = mock(BillActivityDao.class);
        ProviderDataDao providerDataDao = mock(ProviderDataDao.class);
        ProviderBillCenterDao providerBillCenterDao = mock(ProviderBillCenterDao.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        BillingReviewLoader reviewLoader = mock(BillingReviewLoader.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("year", "20xx");

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), isNull()))
                .thenReturn(false);
        BillingOnMriViewModelAssembler assembler = new BillingOnMriViewModelAssembler(
                providerDao,
                billActivityDao,
                providerDataDao,
                providerBillCenterDao,
                securityInfoManager,
                reviewLoader,
                lookupService);

        assertThatThrownBy(() -> assembler.assemble(request, loggedInInfo))
                .isInstanceOfSatisfying(BillingDataLoadException.class, exception -> {
                    assertThat(exception.phase()).isEqualTo(BillingDataLoadException.Phase.DATE_PARSE);
                    assertThat(exception.context()).containsEntry("year", "20xx");
                });
        verifyNoInteractions(providerDao, billActivityDao, providerDataDao, providerBillCenterDao,
                reviewLoader, lookupService);
    }
}
