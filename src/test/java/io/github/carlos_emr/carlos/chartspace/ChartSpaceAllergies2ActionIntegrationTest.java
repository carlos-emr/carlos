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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Privilege and method contract of {@link ChartSpaceAllergies2Action} through
 * {@link CarlosWebTestBase}, with a deny-all default.
 *
 * <p>The request validator and the loader are real, wired to the base class's
 * mock {@code SecurityInfoManager}. {@code AllergyManager} is a mock: the real
 * bean receives its own {@code SecurityInfoManager} through a field that
 * {@code CarlosWebTestBase} does not re-inject, and H2 has no seeded
 * allergies, so a real manager would prove nothing here.</p>
 *
 * @since 2026-10-09
 */
@DisplayName("ChartSpaceAllergies2Action tests")
@Tag("integration")
@Tag("chartspace")
class ChartSpaceAllergies2ActionIntegrationTest extends CarlosWebTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AllergyManager allergyManager;
    private ChartSpaceAllergies2Action action;

    @BeforeEach
    void setUpAction() {
        // Deny-all default so each test must grant the specific privileges it relies on.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("GET");
        addRequestParameter("demographicNo", "2");
        allergyManager = mock(AllergyManager.class);
        action = new ChartSpaceAllergies2Action(
                new ChartSpaceRequestValidator(mockSecurityInfoManager),
                new AllergyBlockLoader(mockSecurityInfoManager, allergyManager));
    }

    @Test
    @DisplayName("should return NO_ACCESS json when _eChart is granted but _allergy is denied")
    void shouldReturnNoAccessJson_whenEChartGrantedButAllergyDenied() throws Exception {
        allowPrivilege("_eChart", "r");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

        assertThat(status()).isEqualTo("NO_ACCESS");
        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should return EMPTY json when both privileges are granted and there is no data")
    void shouldReturnEmptyJson_whenBothGrantedAndNoData() throws Exception {
        allowPrivilege("_eChart", "r");
        allowPrivilege("_allergy", "r");
        when(allergyManager.getActiveAllergies(any(LoggedInInfo.class), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of());

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

        assertThat(status()).isEqualTo("EMPTY");
        assertThat(mockResponse.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("should return OK json with rows when both privileges are granted")
    void shouldReturnOkJson_whenBothGrantedAndData() throws Exception {
        allowPrivilege("_eChart", "r");
        allowPrivilege("_allergy", "r");
        Allergy latex = new Allergy();
        latex.setDescription("Latex");
        when(allergyManager.getActiveAllergies(any(LoggedInInfo.class), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(List.of(latex));

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

        JsonNode body = JSON.readTree(mockResponse.getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("OK");
        assertThat(body.get("items").get(0).get("description").asText()).isEqualTo("Latex");
    }

    @Test
    @DisplayName("should deny when _eChart read is missing, even with _allergy granted")
    void shouldThrowSecurityException_whenEChartReadMissing() {
        allowPrivilege("_allergy", "r");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should return 405 on POST")
    void shouldReturn405_onPost() throws Exception {
        allowPrivilege("_eChart", "r");
        allowPrivilege("_allergy", "r");
        mockRequest.setMethod("POST");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(405);
        verifyNoInteractions(allergyManager);
    }

    private String status() throws Exception {
        return JSON.readTree(mockResponse.getContentAsString()).get("status").asText();
    }
}
