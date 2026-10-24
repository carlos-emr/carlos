/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EncounterUtil} encounter type enum mapping.
 *
 * @since 2026-03-31
 */
@DisplayName("EncounterUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility") @Tag("clinical")
class EncounterUtilUnitTest {

    @Test
    @DisplayName("should map old db value to FACE_TO_FACE_WITH_CLIENT")
    void shouldMap_faceToFace() {
        EncounterUtil.EncounterType type = EncounterUtil.getEncounterTypeFromOldDbValue("face to face encounter with client");
        assertThat(type).isEqualTo(EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT);
    }

    @Test
    @DisplayName("should map old db value to TELEPHONE")
    void shouldMap_telephone() {
        EncounterUtil.EncounterType type = EncounterUtil.getEncounterTypeFromOldDbValue("telephone encounter with client");
        assertThat(type).isEqualTo(EncounterUtil.EncounterType.TELEPHONE_WITH_CLIENT);
    }

    @Test
    @DisplayName("should return null for unknown old db value")
    void shouldReturnNull_forUnknown() {
        EncounterUtil.EncounterType type = EncounterUtil.getEncounterTypeFromOldDbValue("unknown_type");
        assertThat(type).isNull();
    }

    @Test
    @DisplayName("should return null for null input")
    void shouldReturnNull_forNull() {
        EncounterUtil.EncounterType type = EncounterUtil.getEncounterTypeFromOldDbValue(null);
        assertThat(type).isNull();
    }

    @Test
    @DisplayName("should have old db value on each enum constant")
    void shouldHaveOldDbValue_onEachConstant() {
        for (EncounterUtil.EncounterType type : EncounterUtil.EncounterType.values()) {
            assertThat(type.getOldDbValue()).isNotNull().isNotEmpty();
        }
    }
}
