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
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GenerateRaDescriptionViewModel")
@Tag("unit")
@Tag("billing")
class GenerateRaDescriptionViewModelUnitTest {

    @Test
    void shouldDefensivelyCopyTransactionRows() {
        ArrayList<GenerateRaDescriptionViewModel.TransactionRow> rows = new ArrayList<>();
        rows.add(new GenerateRaDescriptionViewModel.TransactionRow(
                "Accounting adjustment", "20260428", "Computer Cheque issued", "000123.450", "message"));

        GenerateRaDescriptionViewModel model = GenerateRaDescriptionViewModel.builder()
                .transactionRows(rows)
                .build();

        rows.add(new GenerateRaDescriptionViewModel.TransactionRow(
                "Advance", "20260429", "No Cheque issued", "000001.000", "late mutation"));

        assertThat(model.getTransactionRows()).hasSize(1);
        assertThatThrownBy(() -> model.getTransactionRows().add(
                new GenerateRaDescriptionViewModel.TransactionRow("", "", "", "", "")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldDefaultNullStructuredRowsToSafeValues() {
        GenerateRaDescriptionViewModel model = GenerateRaDescriptionViewModel.builder()
                .balanceForwardRow(null)
                .transactionRows(null)
                .build();

        assertThat(model.getBalanceForwardRow()).isNotNull();
        assertThat(model.getBalanceForwardRow().claimsAdjustment()).isEqualTo("0.000");
        assertThat(model.getBalanceForwardRow().advances()).isEqualTo("0.000");
        assertThat(model.getBalanceForwardRow().reductions()).isEqualTo("0.000");
        assertThat(model.getBalanceForwardRow().deductions()).isEqualTo("0.000");
        assertThat(model.getTransactionRows()).isEmpty();
    }

    @Test
    void shouldNormalizeNullCellValues_inStructuredRows() {
        GenerateRaDescriptionViewModel.BalanceForwardRow balance =
                new GenerateRaDescriptionViewModel.BalanceForwardRow(null, null, null, null);
        GenerateRaDescriptionViewModel.TransactionRow transaction =
                new GenerateRaDescriptionViewModel.TransactionRow(null, null, null, null, null);

        assertThat(List.of(
                balance.claimsAdjustment(),
                balance.advances(),
                balance.reductions(),
                balance.deductions(),
                transaction.transaction(),
                transaction.transactionDate(),
                transaction.chequeIssued(),
                transaction.amount(),
                transaction.message()))
                .containsOnly("");
    }
}
