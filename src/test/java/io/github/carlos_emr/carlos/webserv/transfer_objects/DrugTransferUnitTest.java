/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Drug;

/**
 * Unit tests for {@link DrugTransfer}.
 *
 * <p>Tests conversion from Drug domain model to DrugTransfer transfer object, with focus on the
 * nullable {@code Drug.pastMed} Boolean to primitive {@code DrugTransfer.pastMed} boolean copy
 * (see issue #2960).
 */
@Tag("unit")
@DisplayName("DrugTransfer unit tests")
class DrugTransferUnitTest {

    @Test
    @DisplayName("should not fault and default pastMed to false when Drug.pastMed is null")
    void shouldConvertDrug_withNullPastMedToFalse() {
        Drug drug = new Drug();
        drug.setPastMed(null);

        DrugTransfer transfer = DrugTransfer.toTransfer(drug);

        assertThat(transfer).isNotNull();
        assertThat(transfer.isPastMed()).isFalse();
    }

    @Test
    @DisplayName("should copy pastMed true when Drug.pastMed is TRUE")
    void shouldConvertDrug_withTruePastMed() {
        Drug drug = new Drug();
        drug.setPastMed(Boolean.TRUE);

        DrugTransfer transfer = DrugTransfer.toTransfer(drug);

        assertThat(transfer.isPastMed()).isTrue();
    }

    @Test
    @DisplayName("should copy pastMed false when Drug.pastMed is FALSE")
    void shouldConvertDrug_withFalsePastMed() {
        Drug drug = new Drug();
        drug.setPastMed(Boolean.FALSE);

        DrugTransfer transfer = DrugTransfer.toTransfer(drug);

        assertThat(transfer.isPastMed()).isFalse();
    }

    @Test
    @DisplayName("should copy remaining scalar fields alongside the pastMed special-case")
    void shouldConvertDrug_withOtherFieldsCopied() {
        Drug drug = new Drug();
        drug.setId(98765);
        drug.setBrandName("testBrand");
        drug.setPastMed(null);

        DrugTransfer transfer = DrugTransfer.toTransfer(drug);

        assertThat(transfer.getId()).isEqualTo(98765);
        assertThat(transfer.getBrandName()).isEqualTo("testBrand");
        assertThat(transfer.isPastMed()).isFalse();
    }

    @Test
    @DisplayName("should return null when the source Drug is null")
    void shouldConvertDrug_withNullSourceReturnsNull() {
        assertThat(DrugTransfer.toTransfer(null)).isNull();
    }
}
