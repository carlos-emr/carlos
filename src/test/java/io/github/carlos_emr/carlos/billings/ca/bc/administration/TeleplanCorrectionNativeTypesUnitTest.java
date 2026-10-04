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
package io.github.carlos_emr.carlos.billings.ca.bc.administration;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TeleplanCorrectionNativeTypesUnitTest {
    @ParameterizedTest
    @CsvSource({"false,1", "true,2", "true,9"})
    void shouldPreserveWcbDatesFlagsAndDuration_whenNativeOrJdbcValuesReturned(boolean modern, int duration) {
        Object[] row = new Object[61];
        Arrays.fill(row, "");
        Object date = modern ? LocalDate.of(2026, 3, 4) : java.sql.Date.valueOf("2026-03-04");
        row[24] = date;
        row[37] = date;
        row[44] = date;
        row[51] = date;
        row[34] = duration;
        row[46] = modern ? 'Y' : "Y";
        row[48] = modern ? 'N' : "N";
        row[49] = modern ? 'Y' : "Y";
        row[50] = modern ? 'N' : "N";
        row[52] = modern ? 'Y' : "Y";
        row[53] = modern ? 'N' : "N";
        row[56] = modern ? 'O' : "O";
        var form = new TeleplanCorrectionFormWCB(Collections.singletonList(row));
        assertThat(form.getW_doi()).isEqualTo("2026-03-04");
        assertThat(form.getW_servicedate()).isEqualTo("2026-03-04");
        assertThat(form.getW_workdate()).isEqualTo("2026-03-04");
        assertThat(form.getW_estimatedate()).isEqualTo("2026-03-04");
        assertThat(form.getW_duration()).isEqualTo(String.valueOf(duration));
        assertThat(form.getW_capability()).isEqualTo("Y");
        assertThat(form.getW_estimate()).isEqualTo("N");
        assertThat(form.getW_rehab()).isEqualTo("Y");
        assertThat(form.getW_rehabtype()).isEqualTo("N");
        assertThat(form.getW_tofollow()).isEqualTo("Y");
        assertThat(form.getW_wcbadvisor()).isEqualTo("N");
        assertThat(form.getStatus()).isEqualTo("O");
    }
}
