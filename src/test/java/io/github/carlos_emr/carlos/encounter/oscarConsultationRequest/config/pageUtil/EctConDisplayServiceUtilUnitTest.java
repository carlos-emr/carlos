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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.consultation.dto.SpecialistListRow;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Protects entity-getter formatting when rendering raw specialist projections. */
@Tag("unit")
@Tag("consultation")
@DisplayName("Specialist directory projection rendering")
class EctConDisplayServiceUtilUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  123 Main Street \t", "123 Main Street"})
    @DisplayName("should preserve trimmed and empty addresses without loading specialist entities")
    void shouldNormalizeAddress_whenRenderingProjectedDirectory(String address) {
        ProfessionalSpecialistDao specialists = mock(ProfessionalSpecialistDao.class);
        registerMock(ProfessionalSpecialistDao.class, specialists);
        registerMock(ConsultationServiceDao.class, mock(ConsultationServiceDao.class));
        when(specialists.findListRows()).thenReturn(List.of(new SpecialistListRow(
                7, "Ada", "Lovelace", "MD", address, "123", "456", "https://example.test", "a@example.test", "General")));
        EctConDisplayServiceUtil util = new EctConDisplayServiceUtil();

        util.estSpecialistVector();

        String expected = address == null || address.isBlank() ? "" : "123 Main Street";
        assertThat(util.addressVec).containsExactly(expected);
        assertThat(util.specIdVec).containsExactly("7");
        verify(specialists).findListRows();
        verifyNoMoreInteractions(specialists);
    }
}
