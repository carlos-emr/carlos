/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
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
package io.github.carlos_emr.carlos.commn.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.GenerateRaDescriptionViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.RaHeaderTotalsPersister;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaDescriptionViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewGenRaDesc2Action;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Practitioner premium mutations require authorized POST before any DAO access")
@Tag("unit")
@Tag("billing")
@Tag("security")
class ApplyPractitionerPremium2ActionUnitTest extends CarlosUnitTestBase {
    private record State(boolean applied, String provider, String amount) { }
    private static final int RA = 71;
    private static final int APPLIED = 101;
    private static final int UNAPPLIED = 102;
    private static final String PROVIDER = "123456";
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private BillingONPremiumDao premiums;
    private ProviderDao providers;
    private RaHeaderTotalsPersister totals;
    private GenerateRaDescriptionViewModelAssembler assembler;
    private final Map<Integer, State> persisted = new LinkedHashMap<>();
    private final List<Integer> writes = new ArrayList<>();

    @BeforeEach
    void setup() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setParameter("rano", String.valueOf(RA));
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        premiums = mock(BillingONPremiumDao.class);
        providers = mock(ProviderDao.class);
        totals = mock(RaHeaderTotalsPersister.class);
        assembler = mock(GenerateRaDescriptionViewModelAssembler.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(BillingONPremiumDao.class, premiums);
        registerMock(ProviderDao.class, providers);
        when(security.hasPrivilege(login, "_billing", "w", null)).thenReturn(true);
        when(providers.getProvider(PROVIDER)).thenReturn(mock(Provider.class));
        persisted.put(APPLIED, new State(true, PROVIDER, "777.77"));
        persisted.put(UNAPPLIED, new State(false, null, "555.55"));
        when(premiums.getRAPremiumsByRaHeaderNo(RA)).thenAnswer(invocation -> {
            List<BillingONPremium> rows = new ArrayList<>();
            persisted.forEach((id, state) -> {
                BillingONPremium row = new BillingONPremium();
                ReflectionTestUtils.setField(row, "id", id);
                row.setRAHeaderNo(RA);
                row.setStatus(state.applied());
                row.setProviderNo(state.provider());
                row.setAmountPay(state.amount());
                rows.add(row);
            });
            return rows;
        });
        doAnswer(invocation -> {
            BillingONPremium row = invocation.getArgument(0);
            writes.add(row.getId());
            persisted.put(row.getId(), new State(row.getStatus(), row.getProviderNo(), row.getAmountPay()));
            return null;
        }).when(premiums).merge(any(BillingONPremium.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void cleanup() {
        servlet.close();
    }

    private String executeWithActualSuccessChain() throws Exception {
        String result = new ApplyPractitionerPremium2Action().execute();
        if (ActionSupport.SUCCESS.equals(result)) {
            // struts-scheduling.xml chains SUCCESS to this real action; same request method.
            return new ViewGenRaDesc2Action(security, assembler, totals).execute();
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseBeforePremiumReadOrWriteEvenWhenTheSuccessChainWouldReturn405(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("providerNo" + APPLIED, PROVIDER);
        Map<Integer, State> before = new LinkedHashMap<>(persisted);
        String result = executeWithActualSuccessChain();
        // Synthetic map diagnostics prove actual mutation ordering in the old RED action.
        System.out.println("SYNTHETIC_PREMIUM_PROOF method=" + method + " before=" + before
                + " after=" + persisted + " mergeCalls=" + writes.size() + " finalStatus=" + response.getStatus());
        assertThat(persisted).isEqualTo(before);
        assertThat(writes).isEmpty();
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(premiums, providers, assembler, totals);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseReadVerbsWithoutParsingMissingRaOrTouchingDao(String method) throws Exception {
        request.setMethod(method);
        request.removeParameter("rano");
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(premiums, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST"})
    void shouldRetainAuthorizationBeforeRefusalOrMutation(String method) {
        request.setMethod(method);
        when(security.hasPrivilege(login, "_billing", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> new ApplyPractitionerPremium2Action().execute())
                .isInstanceOf(SecurityException.class).hasMessageContaining("_billing");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Allow")).isNull();
        verifyNoInteractions(premiums, providers, assembler, totals);
    }

    @Test
    void shouldKeepAuthorizationFirstEvenWithoutAnRaParameter() {
        request.setMethod("GET");request.removeParameter("rano");
        when(security.hasPrivilege(login, "_billing", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> new ApplyPractitionerPremium2Action().execute())
                .isInstanceOf(SecurityException.class).hasMessageContaining("_billing");
        verifyNoInteractions(premiums, providers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "12x", "99999999999"})
    void shouldAnswerBadRequest_whenPostedRaIsMalformed(String rano) {
        request.setMethod("POST");
        request.setParameter("rano", rano);
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(premiums, providers);
    }

    @Test
    void shouldAnswerBadRequest_whenPostedRaIsMissing() {
        request.setMethod("POST");
        request.removeParameter("rano");
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(premiums, providers);
    }

    @Test
    void shouldApplySelectedPremiumByAuthorizedPostAndPreserveOtherRows() {
        request.setMethod("POST");request.setParameter("providerNo" + UNAPPLIED, PROVIDER);
        request.setParameter("choosePremium" + UNAPPLIED, "Y");
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(persisted.get(UNAPPLIED)).isEqualTo(new State(true, PROVIDER, "555.55"));
        assertThat(persisted.get(APPLIED)).isEqualTo(new State(true, PROVIDER, "777.77"));
        assertThat(writes).containsExactly(UNAPPLIED);
        assertThat(response.getHeader("Allow")).isNull();
    }

    @Test
    void shouldUnapplyUntickedPremiumByAuthorizedPostAndClearProviderOnlyOnThatRow() {
        request.setMethod("POST");request.setParameter("providerNo" + APPLIED, PROVIDER);
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(persisted.get(APPLIED)).isEqualTo(new State(false, null, "777.77"));
        assertThat(persisted.get(UNAPPLIED)).isEqualTo(new State(false, null, "555.55"));
        assertThat(writes).containsExactly(APPLIED);
    }

    @Test
    void shouldPreserveAuthorizedPostSuccessChainAndActualReportModel() throws Exception {
        request.setMethod("POST");request.setParameter("providerNo" + UNAPPLIED, PROVIDER);
        request.setParameter("choosePremium" + UNAPPLIED, "Y");
        GenerateRaDescriptionViewModel model = mock(GenerateRaDescriptionViewModel.class);
        when(assembler.assemble(request, login)).thenReturn(model);
        assertThat(executeWithActualSuccessChain()).isEqualTo(ActionSupport.SUCCESS);
        verify(totals).refreshDescriptionHeaderAndPremiums(eq(login), eq(RA), eq(request.getLocale()));
        verify(assembler).assemble(request, login);
        assertThat(request.getAttribute("raDescModel")).isSameAs(model);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(persisted.get(UNAPPLIED).applied()).isTrue();
    }

    @Test
    void shouldKeepUnselectedPostRowsUntouched() {
        request.setMethod("POST");Map<Integer, State> before = new LinkedHashMap<>(persisted);
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(persisted).isEqualTo(before);assertThat(writes).isEmpty();
        verify(providers, never()).getProvider(any());
    }

    @Test
    void shouldPreserveInvalidProviderFailureWithoutDaoMerge() {
        request.setMethod("POST");request.setParameter("providerNo" + APPLIED, "654321");
        Map<Integer, State> before = new LinkedHashMap<>(persisted);
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo("failure");
        assertThat(persisted).isEqualTo(before);assertThat(writes).isEmpty();
        verify(premiums, never()).merge(any(BillingONPremium.class));
    }
}
