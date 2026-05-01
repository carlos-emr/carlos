/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnPaymentViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Smoke tests for {@link BillingOnPaymentViewModelAssembler}, the 500+
 * LOC payment-report assembler. Pins the no-provider-selected guard,
 * the SecurityException path for {@code isThisProviderOnly} with no
 * OHIP, and the report-rendered=false short-circuit so a future refactor
 * of the entry point fails this suite instead of silently shipping an
 * empty report or DAO-storming the page when no filter was selected.
 */
@DisplayName("BillingOnPaymentViewModelAssembler smoke")
@Tag("unit")
@Tag("billing")
class BillingOnPaymentViewModelAssemblerUnitTest {

    private ProviderDao providerDao;
    private RaDetailDao raDetailDao;
    private BillingONCHeader1Dao bCh1Dao;
    private BillingONPremiumDao bPremiumDao;
    private BillingONPaymentDao bPaymentDao;
    private BillingOnItemPaymentDao bItemPaymentDao;
    private DemographicDao demographicDao;
    private LoggedInInfo loggedInInfo;
    private BillingOnPaymentViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        providerDao = mock(ProviderDao.class);
        raDetailDao = mock(RaDetailDao.class);
        bCh1Dao = mock(BillingONCHeader1Dao.class);
        bPremiumDao = mock(BillingONPremiumDao.class);
        bPaymentDao = mock(BillingONPaymentDao.class);
        bItemPaymentDao = mock(BillingOnItemPaymentDao.class);
        demographicDao = mock(DemographicDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999");
        when(providerDao.getBillableProviders()).thenReturn(Collections.emptyList());

        assembler = new BillingOnPaymentViewModelAssembler(
                providerDao, raDetailDao, bCh1Dao, bPremiumDao,
                bPaymentDao, bItemPaymentDao, demographicDao);
    }

    @Test
    void shouldShortCircuit_whenNoProviderSelected() {
        // No providerList param: the form hasn't been submitted yet.
        // reportRendered must be false so the JSP doesn't render empty
        // RA / premium / 3rd-party tables and the DAOs don't get called
        // for those queries.
        MockHttpServletRequest req = new MockHttpServletRequest();

        BillingOnPaymentViewModel vm = assembler.assemble(req, loggedInInfo, false, false);

        assertThat(vm).isNotNull();
        assertThat(vm.isReportRendered())
                .as("no providerList → reportRendered=false")
                .isFalse();
    }

    @Test
    void shouldShortCircuit_whenStartDateUnparseable() {
        // Bad date string lands in the catch-and-error branch; the form
        // still renders but the report doesn't run.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("startDateText", "not-a-date");
        req.setParameter("providerList", "");

        BillingOnPaymentViewModel vm = assembler.assemble(req, loggedInInfo, false, false);

        assertThat(vm).isNotNull();
        assertThat(vm.isReportRendered()).isFalse();
        assertThat(vm.getErrorMsg()).isNotEmpty();
    }

    @Test
    void shouldThrowSecurityException_whenIsThisProviderOnlyWithNoOhipNumber() {
        // _admin.invoices grant routes through the dropdown-restriction
        // path; provider with no OHIP number must throw so the action
        // layer can translate to /noRights.html (legacy contract).
        MockHttpServletRequest req = new MockHttpServletRequest();
        Provider p = new Provider();
        p.setProviderNo("999");
        p.setOhipNo("");
        when(providerDao.getProvider(anyString())).thenReturn(p);

        assertThatThrownBy(() -> assembler.assemble(req, loggedInInfo, true, false))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("OHIP");
    }

    @Test
    void shouldExposeProviderDropdown_whenBillableProvidersResolved() {
        // The dropdown must always populate, even when the report itself
        // doesn't render — operators see who's selectable before they
        // pick.
        MockHttpServletRequest req = new MockHttpServletRequest();
        Provider p1 = new Provider();
        p1.setProviderNo("100");
        p1.setLastName("Smith");
        p1.setFirstName("John");
        when(providerDao.getBillableProviders()).thenReturn(List.of(p1));

        BillingOnPaymentViewModel vm = assembler.assemble(req, loggedInInfo, false, false);

        assertThat(vm.getProviderOptions()).hasSize(1);
        assertThat(vm.getProviderOptions().get(0).displayName()).contains("Smith");
    }
}
