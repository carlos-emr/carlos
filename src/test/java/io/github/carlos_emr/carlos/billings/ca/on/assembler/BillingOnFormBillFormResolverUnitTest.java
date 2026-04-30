/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the 5-step billing-form priority chain in
 * {@link BillingOnFormBillFormResolver}. The agent flagged this as
 * clinical-billing severity: a regression returning the wrong form on
 * edge inputs sends claims with the wrong service-type, which OHIP
 * rejects (bill bounces back into the operator's correction queue,
 * delaying revenue) or accepts incorrectly (compliance issue).
 *
 * <p>Priority order under test:</p>
 * <ol>
 *   <li>{@code curBillForm} request param wins outright</li>
 *   <li>roster-status billing service (if configured)</li>
 *   <li>provider preference (if non-empty and not "no")</li>
 *   <li>group default billing form</li>
 *   <li>{@code carlos.properties default_view}</li>
 * </ol>
 *
 * <p>Plus post-selection MIP/PRI overrides driven by visit type + roster.</p>
 */
@DisplayName("BillingOnFormBillFormResolver")
@Tag("unit")
@Tag("billing")
class BillingOnFormBillFormResolverUnitTest {

    private CtlBillingServiceDao ctlBillingServiceDao;
    private ProviderPreferenceDao providerPreferenceDao;
    private MyGroupDao myGroupDao;
    private BillingOnFormBillFormResolver resolver;
    private BillingOnFormViewModel.Builder builder;

    @BeforeEach
    void setUp() {
        ctlBillingServiceDao = mock(CtlBillingServiceDao.class);
        providerPreferenceDao = mock(ProviderPreferenceDao.class);
        myGroupDao = mock(MyGroupDao.class);
        resolver = new BillingOnFormBillFormResolver(
                ctlBillingServiceDao, providerPreferenceDao, myGroupDao);
        builder = BillingOnFormViewModel.builder();

        // Sensible no-result defaults — individual tests override per branch.
        when(ctlBillingServiceDao.findByServiceTypeId(anyString())).thenReturn(Collections.emptyList());
        when(providerPreferenceDao.find(anyString())).thenReturn(null);
        when(myGroupDao.getProviderGroups(anyString())).thenReturn(Collections.emptyList());
    }

    private MockHttpServletRequest emptyRequest() {
        return new MockHttpServletRequest();
    }

    @Test
    void shouldReturnCurBillForm_whenRequestParamIsPresent_priority1() {
        MockHttpServletRequest request = emptyRequest();
        request.setParameter("curBillForm", "USER-PICKED-FORM");

        BillingOnFormBillFormResolver.ResolvedBillForm result =
                resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                        "999998", "999998", "999998");

        assertThat(result.ctlBillForm()).isEqualTo("USER-PICKED-FORM");
    }

    @Test
    void shouldReturnRosterBillForm_whenRosterStatusHasMatch_priority2() {
        CtlBillingService rosterService = new CtlBillingService();
        rosterService.setServiceType("ROSTER-FORM");
        when(ctlBillingServiceDao.findByServiceTypeId("RO")).thenReturn(List.of(rosterService));

        MockHttpServletRequest request = emptyRequest();

        BillingOnFormBillFormResolver.ResolvedBillForm result =
                resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"RO",
                        "999998", "999998", "999998");

        assertThat(result.ctlBillForm()).isEqualTo("ROSTER-FORM");
    }

    @Test
    void shouldReturnProviderPreference_whenNoRosterMatch_priority3() {
        ProviderPreference pref = new ProviderPreference();
        pref.setDefaultServiceType("PREF-FORM");
        when(providerPreferenceDao.find("999998")).thenReturn(pref);

        MockHttpServletRequest request = emptyRequest();

        BillingOnFormBillFormResolver.ResolvedBillForm result =
                resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                        "999998", "999998", "999998");

        assertThat(result.ctlBillForm()).isEqualTo("PREF-FORM");
    }

    @Test
    void shouldFallThroughProviderPreference_whenServiceTypeIsNo() {
        // The string literal "no" is a sentinel meaning "skip provider pref"
        // — falls through to group default.
        ProviderPreference pref = new ProviderPreference();
        pref.setDefaultServiceType("no");
        when(providerPreferenceDao.find("999998")).thenReturn(pref);
        MyGroup group = new MyGroup();
        group.setDefaultBillingForm("GROUP-FORM");
        when(myGroupDao.getProviderGroups("999998")).thenReturn(List.of(group));

        MockHttpServletRequest request = emptyRequest();

        BillingOnFormBillFormResolver.ResolvedBillForm result =
                resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                        "999998", "999998", "999998");

        assertThat(result.ctlBillForm()).isEqualTo("GROUP-FORM");
    }

    @Test
    void shouldReturnGroupDefault_whenProviderPrefMissing_priority4() {
        MyGroup group = new MyGroup();
        group.setDefaultBillingForm("GROUP-FORM");
        when(myGroupDao.getProviderGroups("999998")).thenReturn(List.of(group));

        MockHttpServletRequest request = emptyRequest();

        BillingOnFormBillFormResolver.ResolvedBillForm result =
                resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                        "999998", "999998", "999998");

        assertThat(result.ctlBillForm()).isEqualTo("GROUP-FORM");
    }

    @Test
    void shouldFallBackToCarlosPropertyDefaultView_whenNoOtherSourceProvides_priority5() {
        // No roster, no provider pref, no group default → reads CarlosProperties.
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("default_view")).thenReturn("CONFIG-DEFAULT");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = emptyRequest();

            BillingOnFormBillFormResolver.ResolvedBillForm result =
                    resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                            "999998", "999998", "999998");

            assertThat(result.ctlBillForm()).isEqualTo("CONFIG-DEFAULT");
        }
    }

    @Test
    void shouldOverrideToMip_whenVisitTypeStartsWith02() {
        // MIP override fires for visit types 02xx and 04xx (short-stay /
        // emergency variants) regardless of upstream resolution. The
        // override is gated on defaultServiceType != "RN".
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("default_view")).thenReturn("CONFIG-DEFAULT");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = emptyRequest();

            BillingOnFormBillFormResolver.ResolvedBillForm result =
                    resolver.resolve(builder, request, /*visitType=*/"02", /*rosterStatus=*/"",
                            "999998", "999998", "999998");

            assertThat(result.ctlBillForm()).isEqualTo("MIP");
        }
    }

    @Test
    void shouldOverrideToPri_whenRosterIsQuebec() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("default_view")).thenReturn("CONFIG-DEFAULT");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = emptyRequest();

            BillingOnFormBillFormResolver.ResolvedBillForm result =
                    resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"QU - Quebec",
                            "999998", "999998", "999998");

            assertThat(result.ctlBillForm()).isEqualTo("PRI");
            assertThat(result.defaultServiceType()).isEqualTo("PRI");
        }
    }

    @Test
    void shouldUseUserNo_whenApptProviderIsNoneSentinel() {
        // The "none" sentinel means the appointment isn't tied to a provider —
        // resolver falls back to userNo for the provider preference lookup.
        ProviderPreference pref = new ProviderPreference();
        pref.setDefaultServiceType("USER-PREF");
        when(providerPreferenceDao.find("USERNUM")).thenReturn(pref);

        MockHttpServletRequest request = emptyRequest();

        BillingOnFormBillFormResolver.ResolvedBillForm result = resolver.resolve(builder, request,
                /*visitType=*/"01", /*rosterStatus=*/"",
                /*providerNo=*/"PROVIDER",
                /*userNo=*/"USERNUM",
                /*apptProviderNo=*/"none");

        assertThat(result.ctlBillForm()).isEqualTo("USER-PREF");
    }

    @Test
    void shouldNeverReturnNull_whenNoSourceProvides() {
        // With no DAO returns and no CarlosProperties stub, the resolver
        // must still return a non-null ctlBillForm (it coalesces to "").
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("default_view")).thenReturn(null);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = emptyRequest();

            BillingOnFormBillFormResolver.ResolvedBillForm result =
                    resolver.resolve(builder, request, /*visitType=*/"01", /*rosterStatus=*/"",
                            "999998", "999998", "999998");

            assertThat(result.ctlBillForm()).isNotNull().isEmpty();
        }
    }
}
