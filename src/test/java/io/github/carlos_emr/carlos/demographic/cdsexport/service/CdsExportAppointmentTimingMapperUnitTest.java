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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportAppointmentTimingMapperUnitTest {
    private final CdsExportAppointmentTimingMapper mapper = new CdsExportAppointmentTimingMapper();
    private final ZoneId zone = ZoneId.of("America/Toronto");

    @Test
    void shouldKeepTheExactLocalDateAndSeconds_andDeriveWholeMinutes() throws Exception {
        var result = mapper.map("2024-02-29", "09:00:30", "10:15:30", zone);
        assertThat(result.date().lexical()).isEqualTo("2024-02-29");
        assertThat(result.time()).isEqualTo("09:00:30");
        assertThat(result.durationMinutes()).isEqualTo(76);
    }

    @Test
    void shouldVerifyWireTimeCarriesTheExactSourceOffset_withoutChangingItsLocalClock()
            throws Exception {
        var winter = mapper.map("2024-02-29", "09:00:30", "10:15:30", zone);
        assertThat(winter.time()).isEqualTo("09:00:30");
        assertThat(winter.wireTime()).isEqualTo("09:00:30-05:00");
        var summer = mapper.map("2024-07-01", "09:00:30", "10:15:30", zone);
        assertThat(summer.wireTime()).isEqualTo("09:00:30-04:00");
        var utc = mapper.map("2024-02-29", "09:00:30", "10:15:30", ZoneId.of("UTC"));
        assertThat(utc.wireTime()).isEqualTo("09:00:30Z");
    }

    @Test
    void shouldWriteTheScheduleClockWithoutAnOffset_forSecondOffsetsAndOffsetsOutsideTheWireRange()
            throws Exception {
        // Local mean time before standard time (-05:17:32) and an offset xs:time cannot hold:
        // the wall-clock time is written with no offset; the booking is never held.
        var historical = mapper.map("1800-01-01", "09:00:00", "10:00:00", zone);
        assertThat(historical.offset()).isNull();
        assertThat(historical.wireTime()).isEqualTo("09:00:00");
        assertThat(historical.durationMinutes()).isEqualTo(61);
        var wide =
                mapper.map("2024-02-29", "09:00:00", "10:00:00", java.time.ZoneOffset.ofHours(15));
        assertThat(wide.offset()).isNull();
        assertThat(wide.wireTime()).isEqualTo("09:00:00");
    }

    @Test
    void shouldDistinguishNativeInclusiveAndImportedExclusiveEnds_usingTheProvenOccurrenceOrigin()
            throws Exception {
        assertThat(mapper.map("2024-02-29", "09:00:00", "09:14:00", zone).durationMinutes())
                .isEqualTo(15);
        assertThat(mapper.map("2024-02-29", "09:00:00", "09:00:00", zone).durationMinutes())
                .isEqualTo(1);
        assertThat(
                        mapper.map(
                                        "2024-02-29",
                                        "09:00:00",
                                        "09:15:00",
                                        zone,
                                        CdsExportAppointmentTimingMapper.EndConvention
                                                .IMPORTED_EXCLUSIVE)
                                .durationMinutes())
                .isEqualTo(15);
    }

    @Test
    void shouldExportOvernightNoLengthAndPartialMinuteSlots_byTheirDocumentedRule()
            throws Exception {
        var native_ = CdsExportAppointmentTimingMapper.EndConvention.NATIVE_INCLUSIVE;
        var imported = CdsExportAppointmentTimingMapper.EndConvention.IMPORTED_EXCLUSIVE;
        // The add form stores 23:30 for 60 minutes as an end of 00:29: the slot ends tomorrow.
        var overnight = mapper.plan("2024-02-29", "23:30:00", "00:29:00", zone, native_);
        assertThat(overnight.length()).isEqualTo(CdsExportAppointmentTimingMapper.Length.NEXT_DAY);
        assertThat(overnight.value().date().lexical()).isEqualTo("2024-02-29");
        assertThat(overnight.value().time()).isEqualTo("23:30:00");
        assertThat(overnight.value().durationMinutes()).isEqualTo(60);
        // The form's duration 0 stores an end one minute before the start: no positive length.
        var none = mapper.plan("2024-02-29", "09:00:00", "08:59:00", zone, native_);
        assertThat(none.length()).isEqualTo(CdsExportAppointmentTimingMapper.Length.NOT_STATED);
        assertThat(none.value().durationMinutes()).isZero();
        assertThat(none.value().wireTime()).isEqualTo("09:00:00-05:00");
        assertThat(mapper.plan("2024-02-29", "09:00:00", "09:00:00", zone, imported).length())
                .isEqualTo(CdsExportAppointmentTimingMapper.Length.NOT_STATED);
        // An end that is not on a minute counts its started minute.
        var partial = mapper.plan("2024-02-29", "09:00:00", "09:01:01", zone, native_);
        assertThat(partial.length())
                .isEqualTo(CdsExportAppointmentTimingMapper.Length.PARTIAL_MINUTE);
        assertThat(partial.value().durationMinutes()).isEqualTo(2);
        assertThat(mapper.plan("2024-02-29", "09:00:00", "09:01:01", zone, imported)
                        .value()
                        .durationMinutes())
                .isEqualTo(2);
        var seconds = mapper.plan("2024-02-29", "23:59:30", "00:00:30", zone, native_);
        assertThat(seconds.length()).isEqualTo(CdsExportAppointmentTimingMapper.Length.NEXT_DAY);
        assertThat(seconds.value().durationMinutes()).isEqualTo(2);
        // An end that is not a time of day states no length; the start is still exported.
        var noEnd = mapper.plan("2024-02-29", "09:00:00", "838:59:59", zone, native_);
        assertThat(noEnd.length()).isEqualTo(CdsExportAppointmentTimingMapper.Length.NOT_STATED);
        assertThat(noEnd.value().time()).isEqualTo("09:00:00");
        // The longest slot a stored end can state: 24 hours less one second, ending tomorrow.
        assertThat(mapper.plan("2024-02-29", "09:00:00", "08:58:00", zone, native_)
                        .value()
                        .durationMinutes())
                .isEqualTo(1439);
    }

    @Test
    void
            shouldWriteTheScheduleClockWithoutAnOffset_forAmbiguousMissingAndOffsetChangingDaylightSavingSlots()
                    throws Exception {
        // The add-appointment screen accepts these slots (an overlap, the spring-forward gap and a
        // slot spanning the change). They are exported as the schedule shows them: the exact date,
        // wall-clock time and minutes, with no offset rather than an invented one.
        for (var slot :
                List.of(
                        List.of("2026-11-01", "01:15:00", "01:45:00", "31"),
                        List.of("2026-03-08", "02:15:00", "02:45:00", "31"),
                        List.of("2026-03-08", "01:30:00", "03:30:00", "121"))) {
            var timing = mapper.map(slot.get(0), slot.get(1), slot.get(2), zone);
            assertThat(timing.date().lexical()).isEqualTo(slot.get(0));
            assertThat(timing.offset()).isNull();
            assertThat(timing.wireTime()).isEqualTo(slot.get(1));
            assertThat(timing.durationMinutes()).isEqualTo(Integer.parseInt(slot.get(3)));
        }
    }

    @Test
    void shouldHoldOnlyADateOrStartThatIsNotACalendarTime_underItsOwnName() {
        for (String date :
                List.of("0000-00-00", "1900-02-29", "2024-2-29", "2024-02-30", " 2024-02-29")) {
            assertThatThrownBy(() -> mapper.map(date, "09:00:00", "10:00:00", zone))
                    .isInstanceOf(
                            io.github.carlos_emr.carlos.demographic.cdsexport.support
                                    .CdsExportMappingReviewException.class)
                    .hasMessage(CdsExportAppointmentTimingMapper.NOT_A_CALENDAR_TIME);
        }
        for (String time : List.of("24:00:00", "-01:00:00", "09:00", "09:00:00.1")) {
            assertThatThrownBy(() -> mapper.map("2024-02-29", time, "10:00:00", zone))
                    .hasMessage("CDS_EXPORT_APPOINTMENT_START_NOT_A_CALENDAR_TIME");
        }
        assertThatThrownBy(() -> mapper.map("2024-02-29", "09:00:00", "10:00:00", null))
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
    }
}
