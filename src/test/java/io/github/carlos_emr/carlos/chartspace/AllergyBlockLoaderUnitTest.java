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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllergyBlockLoader}: the three block states, the
 * patient-scoped {@code _allergy} check, and which failures become a state
 * versus propagate.
 */
@Tag("unit")
class AllergyBlockLoaderUnitTest {

    private SecurityInfoManager securityInfoManager;
    private AllergyManager allergyManager;
    private LoggedInInfo info;
    private AllergyBlockLoader loader;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        allergyManager = mock(AllergyManager.class);
        info = mock(LoggedInInfo.class);
        loader = new AllergyBlockLoader(securityInfoManager, allergyManager);
    }

    private void allowAllergyRead() {
        when(securityInfoManager.hasPrivilege(info, "_allergy", "r", "2")).thenReturn(true);
    }

    private static Allergy allergy(String description, String severity, String reaction) throws Exception {
        Allergy a = new Allergy();
        a.setDescription(description);
        a.setSeverityOfReaction(severity);
        a.setReaction(reaction);
        return a;
    }

    @Test
    void shouldReturnNoAccess_whenAllergyReadMissing() {
        when(securityInfoManager.hasPrivilege(info, "_allergy", "r", "2")).thenReturn(false);

        AllergyBlockDto dto = loader.load(info, 2);

        assertThat(dto.status()).isEqualTo(BlockStatus.NO_ACCESS);
        assertThat(dto.items()).isEmpty();
        verifyNoInteractions(allergyManager);
    }

    @Test
    void shouldCheckPrivilege_scopedToPatient() {
        loader.load(info, 2);

        // Fourth argument is the patient id, not null (unlike EctDisplayAllergy2Action).
        verify(securityInfoManager).hasPrivilege(info, "_allergy", "r", "2");
    }

    @Test
    void shouldReturnEmpty_whenNoActiveAllergies() {
        allowAllergyRead();
        when(allergyManager.getActiveAllergies(info, 2)).thenReturn(List.of());

        AllergyBlockDto dto = loader.load(info, 2);

        assertThat(dto.status()).isEqualTo(BlockStatus.EMPTY);
        assertThat(dto.items()).isEmpty();
    }

    @Test
    void shouldMapItems_inManagerOrder() throws Exception {
        allowAllergyRead();
        Allergy first = allergy("Penicillin", "3", "Hives");
        first.setStartDate(new SimpleDateFormat("yyyy-MM-dd").parse("2020-03-15"));
        Allergy second = allergy("Latex", null, null);
        when(allergyManager.getActiveAllergies(info, 2)).thenReturn(List.of(first, second));

        AllergyBlockDto dto = loader.load(info, 2);

        assertThat(dto.status()).isEqualTo(BlockStatus.OK);
        assertThat(dto.items()).extracting(AllergyBlockDto.Item::description)
                .containsExactly("Penicillin", "Latex");
        assertThat(dto.items()).extracting(AllergyBlockDto.Item::severityCode)
                .containsExactly("3", "");
        assertThat(dto.items().get(0).reaction()).isEqualTo("Hives");
        assertThat(dto.items().get(1).reaction()).isEmpty();
        assertThat(dto.items().get(0).startDate()).isEqualTo(first.getStartDateFormatted());
        assertThat(dto.items().get(1).startDate()).isEmpty();
    }

    @Test
    void shouldReturnImmutableItems_forOkResult() throws Exception {
        allowAllergyRead();
        when(allergyManager.getActiveAllergies(info, 2)).thenReturn(List.of(allergy("Latex", "1", "")));

        AllergyBlockDto dto = loader.load(info, 2);

        assertThatThrownBy(() -> dto.items().add(new AllergyBlockDto.Item("", "", "", "")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnNoAccess_whenManagerRejects() {
        allowAllergyRead();
        when(allergyManager.getActiveAllergies(info, 2))
                .thenThrow(new SecurityException("missing required sec object (_allergy)"));

        AllergyBlockDto dto = loader.load(info, 2);

        assertThat(dto.status()).isEqualTo(BlockStatus.NO_ACCESS);
        assertThat(dto.items()).isEmpty();
    }

    @Test
    void shouldPropagate_whenManagerFailsUnexpectedly() {
        allowAllergyRead();
        when(allergyManager.getActiveAllergies(info, 2)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> loader.load(info, 2)).isInstanceOf(IllegalStateException.class);
    }
}
