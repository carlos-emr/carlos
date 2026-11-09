/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import java.util.Date;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("rest")
class PreventionConverterClinicalStateUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.api.Test
    void shouldRejectUnknownStoredStatus_beforeReportingClinicalFlags() {
        var dto = new io.github.carlos_emr.carlos.webserv.rest.to.model.PreventionTo1();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                dto.applyClinicalState(false, -1, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void shouldPreserveClinicalStateAndOriginalDates_forEveryRefusalStatus(int status) throws Exception {
        Prevention source = mock(Prevention.class);
        Date created = new Date(1000), updated = new Date(2000), given = new Date(3000);
        when(source.getRefusedRawValue()).thenReturn(status);
        when(source.isDeleted()).thenReturn(true);
        when(source.isNever()).thenReturn(true);
        when(source.getCreationDate()).thenReturn(created);
        when(source.getLastUpdateDate()).thenReturn(updated);
        when(source.getPreventionDate()).thenReturn(given);
        PreventionDateService dates = mock(PreventionDateService.class);
        when(dates.getDate(source)).thenReturn(PreventionDateDTO.from(given, null));
        for (boolean precision : new boolean[]{false, true}) {
            var dto = new PreventionConverter(dates, precision).getAsTransferObject(null, source);
            assertThat(dto.isRefused()).isEqualTo(status == 1);
            assertThat(dto.isIneligible()).isEqualTo(status == 2);
            assertThat(dto.isDeleted()).isTrue();
            assertThat(dto.isNever()).isTrue();
            assertThat(dto.getDeletedRawValue()).isEqualTo("1");
            assertThat(dto.getCreationDate()).isEqualTo(created);
            assertThat(dto.getLastUpdateDate()).isEqualTo(updated);
        }
    }
}
