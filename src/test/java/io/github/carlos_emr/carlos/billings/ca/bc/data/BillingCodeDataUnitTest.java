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
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingCodeData#deleteBillingCode(String)}.
 *
 * @since 2026-04-20
 */
@DisplayName("BillingCodeData deleteBillingCode")
@Tag("unit")
@Tag("billing")
class BillingCodeDataUnitTest extends CarlosUnitTestBase {

    @Mock
    private BillingServiceDao mockBillingServiceDao;

    private BillingCodeData billingCodeData;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registerMock(BillingServiceDao.class, mockBillingServiceDao);
        billingCodeData = new BillingCodeData(mockBillingServiceDao);
    }

    @Test
    void shouldReturnFalse_whenCodeIdIsBlank() {
        assertThat(billingCodeData.deleteBillingCode("   ")).isFalse();
        verify(mockBillingServiceDao, never()).find(anyInt());
    }

    @Test
    void shouldReturnFalse_whenCodeIdIsNonNumeric() {
        assertThat(billingCodeData.deleteBillingCode("abc")).isFalse();
        verify(mockBillingServiceDao, never()).find(anyInt());
    }

    @Test
    void shouldReturnFalse_whenBillingCodeDoesNotExist() {
        when(mockBillingServiceDao.find(123)).thenReturn(null);

        assertThat(billingCodeData.deleteBillingCode("123")).isFalse();
        verify(mockBillingServiceDao).find(123);
        verify(mockBillingServiceDao, never()).remove(anyInt());
    }

    @Test
    void shouldDeleteBillingCode_whenBillingCodeExists() {
        BillingService billingService = new BillingService();
        billingService.setBillingserviceNo(123);
        when(mockBillingServiceDao.find(123)).thenReturn(billingService);

        assertThat(billingCodeData.deleteBillingCode("123")).isTrue();
        verify(mockBillingServiceDao).find(123);
        verify(mockBillingServiceDao).remove(123);
    }
}
