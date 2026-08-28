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
package io.github.carlos_emr.carlos.prescript.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link RxPharmacyData#getPharmacy(String)}.
 *
 * <p>Every caller hands the method a request-sourced string, so a value that
 * is not a plain numeric id must answer null instead of letting
 * {@code Integer.parseInt} throw a NumberFormatException that 500s the Rx
 * preview/view pages (found via the deb-install Playwright validation).</p>
 */
@Tag("unit")
@Tag("prescription")
class RxPharmacyDataUnitTest extends CarlosUnitTestBase {

    private PharmacyInfoDao pharmacyInfoDao;
    private RxPharmacyData rxPharmacyData;

    @BeforeEach
    void setUp() {
        pharmacyInfoDao = createAndRegisterMock(PharmacyInfoDao.class);
        registerMock(DemographicPharmacyDao.class, Mockito.mock(DemographicPharmacyDao.class));
        rxPharmacyData = new RxPharmacyData();
    }

    @Test
    @DisplayName("should return pharmacy when the id is numeric")
    void shouldReturnPharmacy_whenIdIsNumeric() {
        PharmacyInfo pharmacy = new PharmacyInfo();
        when(pharmacyInfoDao.getPharmacy(6)).thenReturn(pharmacy);

        assertThat(rxPharmacyData.getPharmacy("6")).isSameAs(pharmacy);
        verify(pharmacyInfoDao).getPharmacy(6);
    }

    @Test
    @DisplayName("should return null without touching the DAO for non-numeric ids")
    void shouldReturnNull_forNonNumericId() {
        assertThat(rxPharmacyData.getPharmacy("abc")).isNull();
        assertThat(rxPharmacyData.getPharmacy("6; DROP TABLE x")).isNull();
        assertThat(rxPharmacyData.getPharmacy("-1")).isNull();
        verifyNoInteractions(pharmacyInfoDao);
    }

    @Test
    @DisplayName("should return null for null, blank, and the literal string null")
    void shouldReturnNull_forNullBlankAndLiteralNull() {
        assertThat(rxPharmacyData.getPharmacy(null)).isNull();
        assertThat(rxPharmacyData.getPharmacy("")).isNull();
        assertThat(rxPharmacyData.getPharmacy("null")).isNull();
        verifyNoInteractions(pharmacyInfoDao);
    }

    @Test
    @DisplayName("should return null when the id exceeds the nine-digit cap")
    void shouldReturnNull_whenIdExceedsDigitCap() {
        assertThat(rxPharmacyData.getPharmacy("1234567890")).isNull();
        verifyNoInteractions(pharmacyInfoDao);
    }
}
