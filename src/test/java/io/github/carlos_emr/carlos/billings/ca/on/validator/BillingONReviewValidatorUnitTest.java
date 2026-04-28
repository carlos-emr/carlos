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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingONReviewValidator}.
 *
 * <p>Locks down the three validation surfaces the validator owns:
 * service-code validity, diagnostic-code validity, and the A003A
 * annual-physical warning. Every assertion targets the {@link
 * BillingONReviewValidator.Result} record so a future refactor that
 * silently changes the validation contract (e.g., swallows an error
 * into a warning, or vice versa) fails loudly.</p>
 *
 * @since 2026-04-26
 */
@DisplayName("BillingONReviewValidator")
@Tag("unit")
@Tag("billing")
@Tag("validator")
class BillingONReviewValidatorUnitTest {

    private BillingONCHeader1Dao bCh1Dao;
    private BillingServiceDao billingServiceDao;
    private DiagnosticCodeDao diagnosticCodeDao;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        bCh1Dao = Mockito.mock(BillingONCHeader1Dao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);
        diagnosticCodeDao = Mockito.mock(DiagnosticCodeDao.class);
        request = new MockHttpServletRequest();
    }

    private BillingONReviewValidator newValidator() {
        return new BillingONReviewValidator(bCh1Dao, billingServiceDao, diagnosticCodeDao);
    }

    @Test
    @DisplayName("returns codeValid=true and no messages on empty request")
    void shouldReturnCodeValid_whenNoCodesSubmitted() {
        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.codeValid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("emits ERROR when a service code is unknown")
    void shouldEmitError_whenServiceCodeNotFound() {
        request.setParameter("serviceCode0", "BOGUS");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(Collections.emptyList());

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.codeValid()).isFalse();
        assertThat(result.messages())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.severity())
                            .isEqualTo(BillingONReviewValidator.Message.Severity.ERROR);
                    assertThat(m.text()).contains("BOGUS");
                });
    }

    @Test
    @DisplayName("does NOT emit error when every service code resolves")
    void shouldStayValid_whenEveryServiceCodeResolves() {
        request.setParameter("serviceCode0", "A007A");
        // findBillingCodesByCodeAndTerminationDate returns a non-empty list:
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.codeValid()).isTrue();
        assertThat(result.messages()).isEmpty();
    }

    @Test
    @DisplayName("escapes underscores in service code so SQL LIKE doesn't wildcard them")
    void shouldEscapeUnderscores_inServiceCodeLookup() {
        request.setParameter("serviceCode0", "A_07A");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));

        newValidator().validate(request, "1", "2026-04-26");

        // The DAO call must receive the escaped form so SQL LIKE treats `_`
        // as a literal — preserves the legacy scriptlet's intent.
        Mockito.verify(billingServiceDao)
                .findBillingCodesByCodeAndTerminationDate(eq("A\\_07A"), any(Date.class));
    }

    @Test
    @DisplayName("uses non-null fallback date when bill reference date is invalid")
    void shouldUseNonNullFallbackDate_whenBillReferenceDateInvalid() {
        request.setParameter("serviceCode0", "A007A");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "not-a-date");

        assertThat(result.codeValid()).isTrue();
        verify(billingServiceDao)
                .findBillingCodesByCodeAndTerminationDate(eq("A007A"), any(Date.class));
    }

    @Test
    @DisplayName("emits ERROR when a primary dx code is unknown")
    void shouldEmitError_whenPrimaryDxCodeNotFound() {
        request.setParameter("dxCode", "Z999");
        when(diagnosticCodeDao.findByDiagnosticCode("Z999"))
                .thenReturn(Collections.emptyList());

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.codeValid()).isFalse();
        assertThat(result.messages())
                .anySatisfy(m -> {
                    assertThat(m.severity())
                            .isEqualTo(BillingONReviewValidator.Message.Severity.ERROR);
                    assertThat(m.text()).contains("Z999");
                });
    }

    @Test
    @DisplayName("checks dxCode + dxCode1 + dxCode2 (3 dx slots)")
    void shouldCheckAllThreeDxSlots() {
        request.setParameter("dxCode", "401");
        request.setParameter("dxCode1", "402");
        request.setParameter("dxCode2", "BOGUS");
        when(diagnosticCodeDao.findByDiagnosticCode("401")).thenReturn(List.of(new DiagnosticCode()));
        when(diagnosticCodeDao.findByDiagnosticCode("402")).thenReturn(List.of(new DiagnosticCode()));
        when(diagnosticCodeDao.findByDiagnosticCode("BOGUS")).thenReturn(Collections.emptyList());

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.codeValid()).isFalse();
        // Only the bogus one becomes a message.
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).text()).contains("BOGUS");
    }

    @Test
    @DisplayName("emits WARNING (not ERROR) when A003A was billed within the past year")
    void shouldEmitWarning_whenA003ABilledWithinPastYear() {
        request.setParameter("serviceCode0", "A003A");
        request.setParameter("xml_billtype", "ODP");
        request.setParameter("service_date", "2026-04-26");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));
        // Last A003A bill was 6 months ago — within the 1-year guard.
        BillingONCHeader1 lastBill = new BillingONCHeader1();
        lastBill.setHeaderId(42);
        Calendar c = Calendar.getInstance();
        c.set(2025, Calendar.OCTOBER, 26);
        lastBill.setBillingDate(c.getTime());
        when(bCh1Dao.getLastOHIPBillingDateForServiceCode(eq(1), eq("A003A")))
                .thenReturn(lastBill);

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        // codeValid stays TRUE — the legacy scriptlet emitted a warning but
        // did not block save; the validator preserves that.
        assertThat(result.codeValid()).isTrue();
        assertThat(result.messages())
                .anySatisfy(m -> {
                    assertThat(m.severity())
                            .isEqualTo(BillingONReviewValidator.Message.Severity.WARNING);
                    assertThat(m.text())
                            .contains("A003A")
                            .contains("past year");
                });
    }

    @Test
    @DisplayName("does NOT emit A003A warning when last bill was over a year ago")
    void shouldNotEmitWarning_whenA003AOlderThanYear() {
        request.setParameter("serviceCode0", "A003A");
        request.setParameter("xml_billtype", "ODP");
        request.setParameter("service_date", "2026-04-26");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));
        // Last A003A bill 14 months ago — outside the guard window.
        BillingONCHeader1 lastBill = new BillingONCHeader1();
        Calendar c = Calendar.getInstance();
        c.set(2025, Calendar.FEBRUARY, 1);
        lastBill.setBillingDate(c.getTime());
        when(bCh1Dao.getLastOHIPBillingDateForServiceCode(anyInt(), eq("A003A")))
                .thenReturn(lastBill);

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        assertThat(result.messages())
                .noneMatch(m -> m.severity()
                        == BillingONReviewValidator.Message.Severity.WARNING);
    }

    @Test
    @DisplayName("does NOT run A003A guard when bill type is not ODP*")
    void shouldSkipA003AGuard_whenBillTypeIsNotODP() {
        request.setParameter("serviceCode0", "A003A");
        request.setParameter("xml_billtype", "WCB");  // not ODP*
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));

        newValidator().validate(request, "1", "2026-04-26");

        // The DAO is never consulted because the bill type filter rejects.
        Mockito.verify(bCh1Dao, Mockito.never())
                .getLastOHIPBillingDateForServiceCode(anyInt(), anyString());
    }

    @Test
    @DisplayName("does NOT run A003A guard when demoNo is non-numeric")
    void shouldSkipA003AGuard_whenDemoNoIsNonNumeric() {
        request.setParameter("serviceCode0", "A003A");
        request.setParameter("xml_billtype", "ODP");
        when(billingServiceDao.findBillingCodesByCodeAndTerminationDate(anyString(), any(Date.class)))
                .thenReturn(List.of(new Object()));

        newValidator().validate(request, "not-a-number", "2026-04-26");

        Mockito.verify(bCh1Dao, Mockito.never())
                .getLastOHIPBillingDateForServiceCode(anyInt(), anyString());
    }

    @Test
    @DisplayName("messages list is immutable (defensive copy)")
    void shouldReturnImmutableMessageList() {
        request.setParameter("dxCode", "BOGUS");
        when(diagnosticCodeDao.findByDiagnosticCode("BOGUS"))
                .thenReturn(Collections.emptyList());

        BillingONReviewValidator.Result result =
                newValidator().validate(request, "1", "2026-04-26");

        // Result.messages() must not be mutable to callers.
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.messages().add(new BillingONReviewValidator.Message(
                        BillingONReviewValidator.Message.Severity.ERROR, "extra")));
    }
}
