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
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.NativeQueryValues;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("lab")
class LabResultDataTimestampUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 123000000, 999999999})
    void shouldParseReceivedTimestamp_whenHibernateReturnsLocalDateTime(int nanos) {
        LabResultData lab = new LabResultData();
        lab.labType = LabResultData.HL7TEXT;
        lab.dateTime = NativeQueryValues.asString(LocalDateTime.of(2021, 6, 16, 23, 59, 59, nanos));
        assertThat(lab.getDateObj()).isEqualTo(new GregorianCalendar(2021, Calendar.JUNE, 16, 23, 59, 59).getTime());
    }

    @ParameterizedTest
    @CsvSource({
        "2021-06-16,0,0,0", "2021-06-16 12:34,12,34,0",
        "2021-06-16 12:34:56,12,34,56", "2021-06-16 12:34:56.0,12,34,56"
    })
    void shouldPreserveDateAndSecondPrecision_whenParsingSupportedLabTypes(String text, int hour, int minute, int second) {
        for (String type : new String[] {LabResultData.HL7TEXT, LabResultData.Spire}) {
            LabResultData lab = new LabResultData();
            lab.labType = type;
            lab.dateTime = " " + text + " ";
            assertThat(lab.getDateObj()).isEqualTo(new GregorianCalendar(2021, Calendar.JUNE, 16, hour, minute, second).getTime());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "2021-06-16 12:34:56.", "2021-06-16 12:34:56.bad", "2021-06-16 12:34:56+01:00"})
    void shouldReturnNull_whenTimestampIsMissingOrHasUnsupportedSuffix(String text) {
        LabResultData lab = new LabResultData();
        lab.labType = LabResultData.HL7TEXT;
        lab.dateTime = text;
        assertThat(lab.getDateObj()).isNull();
    }
}
