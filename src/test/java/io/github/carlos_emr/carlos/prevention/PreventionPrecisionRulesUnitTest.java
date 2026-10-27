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
package io.github.carlos_emr.carlos.prevention;

import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.sql.Date;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class PreventionPrecisionRulesUnitTest {
    @Test
    void shouldRequireReview_whenHistoricalIntervalsOverlap() throws Exception {
        var history = new Prevention("F", Date.valueOf("2000-01-01"));
        history.addPreventionItem(item("2019-01-01", "YYYY"));
        history.addPreventionItem(item("2019-05-01", "YYYY-MM"));
        assertThat(history.getPreventionData("Td")).hasSize(2);
        assertThat(history.isDateReviewRequired()).isTrue();
        assertThat(history.getLastPreventionDate("Td")).isNull();
        assertThat(history.getHowManyDaysSinceLast("Td")).isEqualTo(-1);
        assertThat(history.getHowManyMonthsSinceLast("Td")).isEqualTo(-1);
        assertThat(history.getAgeInMonthsLastPreventionTypeGiven("Td")).isEqualTo(-1);
        assertThatThrownBy(() -> history.isLastPreventionWithinRange("Td", "2019-01-01", "2019-06-01"))
                .isInstanceOf(PreventionDatePrecisionException.class);
        assertThatThrownBy(() -> history.isPassedNextDate("Td"))
                .isInstanceOf(PreventionDatePrecisionException.class);
        assertThatThrownBy(() -> history.isPreventionNever("Td"))
                .isInstanceOf(PreventionDatePrecisionException.class);
        new PreventionDSImpl().getMessages(history);
        assertThat(history.getWarningMsgs()).containsKey("Td");
        assertThat(history.getWarnings()).singleElement().asString().contains("review history");
    }

    @Test
    void shouldRetainHistory_whenTimingIsUnknown() throws Exception {
        var history = new Prevention("F", Date.valueOf("2000-01-01"));
        history.addPreventionItem(item(null, null));
        new PreventionDSImpl().getMessages(history);
        assertThat(history.getPreventionData("Td")).hasSize(1);
        assertThat(history.getLastPreventionDate("Td")).isNull();
        assertThat(history.getWarningMsgs()).containsKey("Td");
    }

    @Test
    void shouldPreserveExactRules_whenJanuaryFirstHasNoPartialMetadata() {
        var history = new Prevention("F", Date.valueOf("2000-01-01"));
        history.addPreventionItem(item("2019-01-01", null));
        assertThat(history.isDateReviewRequired()).isFalse();
        assertThat(history.getLastPreventionDate("Td")).isEqualTo(Date.valueOf("2019-01-01"));
    }

    private static PreventionItem item(String day, String format) {
        var record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        record.setPreventionType("Td");
        record.setPreventionDate(day == null ? null : Date.valueOf(day));
        return new PreventionItem(record, PreventionDateDTO.from(record.getPreventionDate(), format));
    }
}
