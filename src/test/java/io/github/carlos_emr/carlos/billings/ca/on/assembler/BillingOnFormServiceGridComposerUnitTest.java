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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BillingOnFormServiceGridComposer")
@Tag("unit")
@Tag("billing")
class BillingOnFormServiceGridComposerUnitTest {

    @Test
    void shouldAcceptSimpleInlineStyleDeclarations() {
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:red;"))
                .isTrue();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("font-weight: bold; background-color:#fff"))
                .isTrue();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:red;  "))
                .isTrue();
    }

    @Test
    void shouldRejectMalformedOrDangerousInlineStylesWithoutRegexBacktracking() {
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:\"red\";"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("color:\\red;"))
                .isFalse();
        assertThat(BillingOnFormServiceGridComposer.isSafeInlineStyle("-:!-:!-:!-:!-:!-:!-:!-:!-:!"))
                .isFalse();
    }

    // -- compose() integration: this file previously only exercised the
    // -- isSafeInlineStyle() regex helper. These tests cover the empty-input
    // -- happy path of compose() itself; deeper grid-content tests can extend
    // -- from here.

    private CtlBillingServiceDao ctlBillingServiceDao;
    private BillingServiceDao billingServiceDao;
    private CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private CSSStylesDAO cssStylesDAO;
    private CodeFilterManager codeFilterManager;
    private CtlBillingTypeDao ctlBillingTypeDao;
    private DiagnosticCodeDao diagnosticCodeDao;
    private BillingOnFormServiceGridComposer composer;

    @BeforeEach
    void setUp() {
        ctlBillingServiceDao = mock(CtlBillingServiceDao.class);
        billingServiceDao = mock(BillingServiceDao.class);
        ctlBillingServicePremiumDao = mock(CtlBillingServicePremiumDao.class);
        cssStylesDAO = mock(CSSStylesDAO.class);
        codeFilterManager = mock(CodeFilterManager.class);
        ctlBillingTypeDao = mock(CtlBillingTypeDao.class);
        diagnosticCodeDao = mock(DiagnosticCodeDao.class);
        composer = new BillingOnFormServiceGridComposer(
                ctlBillingServiceDao,
                billingServiceDao,
                ctlBillingServicePremiumDao,
                cssStylesDAO,
                codeFilterManager,
                ctlBillingTypeDao,
                diagnosticCodeDao);
    }

    @Test
    void shouldComposeEmptyGrid_whenNoServiceTypesPresent() {
        when(ctlBillingServiceDao.findServiceTypesByStatus("A"))
                .thenReturn(Collections.emptyList());

        BillingOnFormViewModel.Builder b = BillingOnFormViewModel.builder();

        composer.compose(b, "billform-A", new Date(), new Date(), new Demographic());

        BillingOnFormViewModel model = b.build();
        assertThat(model.getServiceGrid().serviceTypes()).isEmpty();
        assertThat(model.getServiceGrid().codesByServiceType()).isEmpty();
    }

    @Test
    void shouldSkipServiceTypeRow_whenCodeColumnIsNull() {
        // Defensive guard: a ctl_billservice row with a null code column
        // would render id="null" / billForm=null in the DOM. The composer
        // logs a warning and skips it.
        Object[] rowWithNullCode = new Object[] { "Some Group", null };
        when(ctlBillingServiceDao.findServiceTypesByStatus("A"))
                .thenReturn(Collections.singletonList(rowWithNullCode));

        BillingOnFormViewModel.Builder b = BillingOnFormViewModel.builder();
        composer.compose(b, "any-billform", new Date(), new Date(), new Demographic());

        BillingOnFormViewModel model = b.build();
        assertThat(model.getServiceGrid().serviceTypes()).isEmpty();
        assertThat(model.getServiceGrid().codesByServiceType()).isEmpty();
    }

    @Test
    void shouldEmitOneEntryPerServiceType_whenRowsHaveValidCodes() {
        // Given two service-type rows with no service-code rows in any group,
        // the composer must still emit each service-type code into the grid
        // (used by the menu, even when groups are empty).
        Object[] type1 = new Object[] { "Office", "OFC" };
        Object[] type2 = new Object[] { "Inpatient", "HOS" };
        when(ctlBillingServiceDao.findServiceTypesByStatus("A"))
                .thenReturn(java.util.Arrays.asList(type1, type2));
        when(billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(
                anyString(), anyString(), any(Date.class)))
                .thenReturn(Collections.emptyList());

        BillingOnFormViewModel.Builder b = BillingOnFormViewModel.builder();
        composer.compose(b, "OFC", new Date(), new Date(), new Demographic());

        BillingOnFormViewModel model = b.build();
        assertThat(model.getServiceGrid().serviceTypes()).containsExactly("OFC", "HOS");
    }
}
