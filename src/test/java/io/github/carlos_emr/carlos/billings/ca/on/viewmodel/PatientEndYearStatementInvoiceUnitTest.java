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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the {@link PatientEndYearStatementInvoice} record's compact
 * constructor + accessor defensive-copy invariants. A regression that
 * removes either copy lets a JSP mutate the persisted aggregation.
 */
@DisplayName("PatientEndYearStatementInvoice record")
@Tag("unit")
@Tag("billing")
class PatientEndYearStatementInvoiceUnitTest {

    @Test
    void shouldDefensivelyCopyInvoiceDate_whenCallerMutatesOriginalDate() {
        Date original = new Date(1_700_000_000_000L);
        PatientEndYearStatementInvoice inv = new PatientEndYearStatementInvoice(
                42, original, "100.00", "50.00", List.of());
        long stored = inv.invoiceDate().getTime();

        // Mutate original AFTER construction.
        original.setTime(0L);

        // Record's invoiceDate is unchanged because the compact ctor
        // defensively copied the input Date.
        assertThat(inv.invoiceDate().getTime()).isEqualTo(stored);
    }

    @Test
    void shouldDefensivelyCopyInvoiceDate_onAccessor() {
        Date original = new Date(1_700_000_000_000L);
        PatientEndYearStatementInvoice inv = new PatientEndYearStatementInvoice(
                42, original, "100.00", "50.00", List.of());

        // Mutate the Date returned by the accessor.
        inv.invoiceDate().setTime(0L);

        // Subsequent accessor call returns an unchanged Date — the accessor
        // also copies, so the JSP can't reach in and corrupt the snapshot.
        assertThat(inv.invoiceDate().getTime()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void shouldReturnEmptyImmutableServices_whenConstructedWithNullList() {
        PatientEndYearStatementInvoice inv = new PatientEndYearStatementInvoice(
                42, new Date(), "100.00", "50.00", null);
        assertThat(inv.services()).isEmpty();
        assertThatThrownBy(() ->
                inv.services().add(new PatientEndYearStatementServiceLine("X", "1.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRetainServicesUnchanged_whenCallerMutatesOriginalListPostConstruction() {
        List<PatientEndYearStatementServiceLine> mutable = new ArrayList<>();
        mutable.add(new PatientEndYearStatementServiceLine("A007A", "75.00"));
        PatientEndYearStatementInvoice inv = new PatientEndYearStatementInvoice(
                42, new Date(), "100.00", "50.00", mutable);

        mutable.add(new PatientEndYearStatementServiceLine("X", "1.00"));
        mutable.clear();

        // Record snapshot is the post-construction state.
        assertThat(inv.services()).hasSize(1);
        assertThat(inv.services().get(0).code()).isEqualTo("A007A");
    }

    @Test
    void shouldExposeBothLegacyAndRecordAccessors_forJspEl() {
        // The legacy getInvoiceNo() accessor exists alongside the record
        // accessor invoiceNo() so JSP EL ${row.invoiceNo} resolves either way.
        Date d = new Date();
        PatientEndYearStatementInvoice inv = new PatientEndYearStatementInvoice(
                42, d, "100.00", "50.00", List.of());
        assertThat(inv.getInvoiceNo()).isEqualTo(42);
        assertThat(inv.getInvoiced()).isEqualTo("100.00");
        assertThat(inv.getPaid()).isEqualTo("50.00");
        assertThat(inv.getServices()).isEmpty();
    }
}
