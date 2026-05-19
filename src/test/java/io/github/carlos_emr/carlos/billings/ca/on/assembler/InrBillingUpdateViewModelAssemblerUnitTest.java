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

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.InrBillingUpdateViewModel;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InrBillingUpdateViewModelAssembler}, the assembler
 * that pulls demographic-display fields out of {@link DemographicDao} for
 * {@code inr/updateINRbilling.jsp}.
 *
 * @since 2026-04-26
 */
@DisplayName("InrBillingUpdateViewModelAssembler")
@Tag("unit")
@Tag("billing")
class InrBillingUpdateViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    private AutoCloseable mockitoCloseable;

    @Mock
    private DemographicDao mockDemographicDao;

    private MockHttpServletRequest request;
    private InrBillingUpdateViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        assembler = new InrBillingUpdateViewModelAssembler(mockDemographicDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private static Demographic makeDemo() {
        Demographic d = new Demographic();
        d.setHin("9876543225");
        d.setVer("ab"); // lower-case to verify uppercase translation
        d.setYearOfBirth("1985");
        d.setMonthOfBirth("06");
        d.setDateOfBirth("15");
        return d;
    }

    @Test
    void shouldFormatHinAndDob_fromDemographicLookup() {
        when(mockDemographicDao.getDemographicById(101)).thenReturn(makeDemo());
        request.setParameter("demono", "101");
        request.setParameter("billinginr_no", "7");
        request.setParameter("demo_name", "Doe, Jane");
        request.setParameter("servicecode", "A007");
        request.setParameter("dxcode", "401");

        InrBillingUpdateViewModel m = assembler.assemble(request);

        assertThat(m.getDemoNo()).isEqualTo("101");
        assertThat(m.getBillingInrNo()).isEqualTo("7");
        assertThat(m.getDemoName()).isEqualTo("Doe, Jane");
        assertThat(m.getDemoHin()).isEqualTo("9876543225AB"); // ver uppercased
        assertThat(m.getDemoDob()).isNotEmpty();
        assertThat(m.getServiceCode()).isEqualTo("A007");
        assertThat(m.getDxCode()).isEqualTo("401");
    }

    @Test
    void shouldReturnEmptyHinAndDob_whenDemographicNotFound() {
        when(mockDemographicDao.getDemographicById(999)).thenReturn(null);
        request.setParameter("demono", "999");

        InrBillingUpdateViewModel m = assembler.assemble(request);

        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
    }

    @Test
    void shouldReturnEmptyHinAndDob_whenDemoNoMissing() {
        InrBillingUpdateViewModel m = assembler.assemble(request);

        assertThat(m.getDemoNo()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
    }

    @Test
    void shouldNotThrow_whenDemoNoIsNonNumeric() {
        request.setParameter("demono", "abc");

        InrBillingUpdateViewModel m = assembler.assemble(request);

        assertThat(m.getDemoNo()).isEqualTo("abc");
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
    }
}
