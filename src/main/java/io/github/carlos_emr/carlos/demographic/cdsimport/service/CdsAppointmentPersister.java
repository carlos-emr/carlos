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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsAppointmentSourceContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentSourceDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigInteger;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Native single-day slots require exact representable timing; every source-only decision is
 * explicit and retains complete values.
 *
 * <p>A CARLOS sender also writes one {@code CARLOS.Appointment.Source.v1} NewCategory record per
 * appointment ({@link CdsAppointmentResidualReader}). It is used only after it proves itself
 * against the element it names: the same position, the same local date and start, and a stored
 * end that gives the element's Duration by the shared rule ({@link
 * CdsAppointmentSourceContract#minutes}). Then the exact stored end (a slot past midnight, with
 * no length or not on a minute becomes a native appointment instead of a source-only record),
 * the complete notes (when the element's text is exactly the shared cut of them) and the
 * scheduler context (style, billing, location, resources, urgency, type, booking source) are
 * restored. A record that does not prove itself is reported ({@code
 * CDS_APPOINTMENT_SOURCE_RECORD_NOT_APPLIED}) and nothing is taken from it. The status code, the
 * booking name, the remarks, the program and the provider number are not restored: they depend
 * on the receiving clinic's own tables or already travel in the standard elements.
 */
@Service
public class CdsAppointmentPersister {
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService normalization;
    private final CdsImportConfigurationService configuration;
    private final CdsProviderResolver providers;
    private final CdsNativePreparationService preparation;
    private final CdsAppointmentResidualReader sources;

    private final OscarAppointmentDao appointments;
    private final DemographicDao demographics;
    // Unit hashes can interleave patients. Bound the working set independently
    // of import size and isolate names by run, owner epoch and local patient.
    private record PatientNameKey(String run, long epoch, int patient) { }
    private final Map<PatientNameKey, String> patientNames = Collections.synchronizedMap(
            new LinkedHashMap<PatientNameKey, String>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PatientNameKey, String> eldest) {
                    return size() > 256;
                }
            });

    public CdsAppointmentPersister(
            CdsFieldParser fields,
            CdsClinicalNormalizationService normalization,
            CdsImportConfigurationService configuration,
            CdsProviderResolver providers,
            CdsNativePreparationService preparation,
            OscarAppointmentDao appointments,
            DemographicDao demographics) {
        this.fields = fields;
        this.normalization = normalization;
        this.configuration = configuration;
        this.providers = providers;
        this.preparation = preparation;
        this.sources = fields == null ? null : new CdsAppointmentResidualReader(fields);
        this.appointments = Objects.requireNonNull(appointments);
        this.demographics = Objects.requireNonNull(demographics);
    }

    public void validate(CdsRecordDto record, CdsImportCommand command) throws IOException {
        if (fields.firstToken(record, "AppointmentTime", 64) == null)
            throw new CdsImportException("APPOINTMENT_TIME_MISSING");
        normalization.date(record, "AppointmentDate", command);
    }

    public void prepare(
            String run, long epoch, String unit, CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        Map<String, CdsClinicalDiagnosticDto> diagnostics = new LinkedHashMap<>();
        Consumer<CdsClinicalDiagnosticDto> notice =
                n -> diagnostics.putIfAbsent(n.code() + "/" + n.field(), n);
        CdsTemporalDto sourceDate = normalization.date(record, "AppointmentDate", command);
        LocalDate date = sourceDate.earliest();
        boolean representable = sourceDate.exactCalendarDate();
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            representable = false;
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_DATE_RANGE_SOURCE_ONLY", "AppointmentDate"));
        }
        if (!representable)
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_PARTIAL_DATE_SOURCE_ONLY", "AppointmentDate"));
        if (sourceDate.appliedShiftDays() != 0)
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_SYNTHETIC_DATE_SHIFT_APPLIED", "AppointmentDate"));
        String duration = bounded(record, "Duration", 32, notice),
                sourceTime = fields.firstToken(record, "AppointmentTime", 64);
        long minutes = 0;
        if (duration == null) {
            representable = false;
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_DURATION_SOURCE_ONLY", "Duration"));
        } else {
            BigInteger integer = new BigInteger(duration);
            if (integer.signum() < 1) throw new CdsImportException("APPOINTMENT_DURATION_INVALID");
            if (integer.compareTo(BigInteger.valueOf(1440)) > 0) {
                representable = false;
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_APPOINTMENT_MULTIDAY_SOURCE_ONLY", "Duration"));
            } else minutes = integer.longValueExact();
        }
        if (configuration.value("cds.import.clinicalZone", "").isEmpty()) {
            representable = false;
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_TIME_ZONE_SOURCE_ONLY", "AppointmentTime"));
        }
        LocalTime start = null, end = null;
        try {
            try {
                OffsetTime offset = OffsetTime.parse(sourceTime);
                start = offset.toLocalTime();
                if (representable) {
                    String zone = configuration.value("cds.import.clinicalZone", "");
                    if (zone.isEmpty()) {
                        representable = false;
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_OFFSET_SOURCE_ONLY", "AppointmentTime"));
                    } else {
                        var local =
                                date.atTime(start)
                                        .atOffset(offset.getOffset())
                                        .atZoneSameInstant(ZoneId.of(zone));
                        date = local.toLocalDate();
                        start = local.toLocalTime();
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_OFFSET_NORMALIZED", "AppointmentTime"));
                    }
                }
            } catch (DateTimeParseException noOffset) {
                start = LocalTime.parse(sourceTime);
            }
            if (start.getNano() != 0) {
                representable = false;
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_APPOINTMENT_SUBSECOND_SOURCE_ONLY", "AppointmentTime"));
            }
            if (representable) {
                String zone = configuration.value("cds.import.clinicalZone", "");
                if (!zone.isEmpty()
                        && ZoneId.of(zone).getRules().getValidOffsets(date.atTime(start)).size()
                                != 1) {
                    representable = false;
                    notice.accept(
                            new CdsClinicalDiagnosticDto(
                                    "CDS_APPOINTMENT_LOCAL_TIME_SOURCE_ONLY", "AppointmentTime"));
                }
                end = start.plusMinutes(minutes);
                if (!end.isAfter(start)) {
                    representable = false;
                    notice.accept(
                            new CdsClinicalDiagnosticDto(
                                    "CDS_APPOINTMENT_CROSS_MIDNIGHT_SOURCE_ONLY", "Duration"));
                }
                if (representable && !zone.isEmpty()) {
                    var rules = ZoneId.of(zone).getRules();
                    var before = rules.getValidOffsets(date.atTime(start));
                    var after = rules.getValidOffsets(date.atTime(end));
                    if (before.size() != 1
                            || after.size() != 1
                            || !before.get(0).equals(after.get(0))) {
                        representable = false;
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_DST_DURATION_SOURCE_ONLY", "Duration"));
                    }
                }
            }
        } catch (DateTimeParseException validSchemaButNotNative) {
            representable = false;
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_TIME_SOURCE_ONLY", "AppointmentTime"));
        }
        var provider =
                providers.resolve(
                        fields.firstToken(record, "Provider/OHIPPhysicianId", 20),
                        null,
                        fields.firstText(record, "Provider/Name/FirstName", 250),
                        fields.firstText(record, "Provider/Name/LastName", 250),
                        command.matchProviderNames());
        if (!provider.matched())
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_PROVIDER_" + provider.outcome(), "Provider"));
        String purpose = fields.firstText(record, "AppointmentPurpose", 250);
        if (purpose != null && purpose.codePointCount(0, purpose.length()) > 80) {
            purpose = "CDS: complete appointment purpose is available in CDS source fields.";
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_APPOINTMENT_PURPOSE_SOURCE_VIEW", "AppointmentPurpose"));
        }
        String notes = fields.firstText(record, "AppointmentNotes", 32768);
        // The sender's CARLOS.Appointment.Source.v1 record, used only once it proves itself.
        CdsAppointmentSourceDto restored = null;
        var found = sources == null ? null : sources.find(record);
        if (found != null) {
            var proven =
                    found.source() == null
                            ? null
                            : proven(found.source(), sourceDate, sourceTime, duration);
            if (proven == null)
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_APPOINTMENT_SOURCE_RECORD_NOT_APPLIED", "NewCategory"));
            else {
                var source = found.source();
                if (proven.nativeEnd()) {
                    // The stored wall-clock slot is known exactly: a native appointment, as the
                    // sender's schedule shows it, replaces every Duration-derived decision.
                    if (!proven.date().equals(date) || !proven.start().equals(start))
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_OFFSET_NORMALIZED", "AppointmentTime"));
                    date = proven.date();
                    start = proven.start();
                    end = proven.end();
                    representable = true;
                    diagnostics
                            .keySet()
                            .removeIf(
                                    key ->
                                            SLOT_DECISIONS.contains(
                                                    key.substring(0, key.indexOf('/'))));
                }
                boolean completeNotes = false;
                if (source.completeNotes() != null) {
                    if (lines(CdsAppointmentSourceContract.notesElement(source.completeNotes()))
                                    .equals(lines(notes))
                            && source.completeNotes()
                                            .codePointCount(0, source.completeNotes().length())
                                    <= 32768) {
                        notes = source.completeNotes();
                        completeNotes = true;
                    } else
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_SOURCE_NOTES_NOT_RESTORED",
                                        "AppointmentNotes"));
                }
                restored =
                        new CdsAppointmentSourceDto(
                                proven.nativeEnd(),
                                completeNotes,
                                column(source.style(), CdsAppointmentSourceDto.STYLE, notice),
                                column(source.billing(), CdsAppointmentSourceDto.BILLING, notice),
                                column(source.location(), CdsAppointmentSourceDto.LOCATION, notice),
                                column(
                                        source.resources(),
                                        CdsAppointmentSourceDto.RESOURCES,
                                        notice),
                                column(source.urgency(), CdsAppointmentSourceDto.URGENCY, notice),
                                column(source.type(), CdsAppointmentSourceDto.TYPE, notice),
                                bookingSource(source.bookingSource(), notice));
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_APPOINTMENT_SOURCE_RECORD_APPLIED", "NewCategory"));
            }
        }
        if (representable) {
            try { io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.dateStorageAnchor(date); }
            catch (io.github.carlos_emr.carlos.commn.model.CdsClinicalException unavailable) {
                representable = false;
                notice.accept(new CdsClinicalDiagnosticDto("CDS_APPOINTMENT_NATIVE_CALENDAR_SOURCE_ONLY", "AppointmentDate"));
            }
        }
        preparation.store(
                run,
                epoch,
                unit,
                record.effectiveSha256(),
                command,
                new CdsAppointmentNativeDto(
                        date == null ? null : date.toString(),
                        start == null ? null : start.toString(),
                        end == null ? null : end.toString(),
                        fields.firstText(record, "AppointmentStatus", 250),
                        purpose,
                        notes,
                        provider.localProviderNo(),
                        representable,
                        List.copyOf(diagnostics.values()),
                        restored));
    }

    /** Decisions the element's Duration and offset forced that an exact stored slot replaces. */
    private static final java.util.Set<String> SLOT_DECISIONS =
            java.util.Set.of(
                    "CDS_APPOINTMENT_DURATION_SOURCE_ONLY",
                    "CDS_APPOINTMENT_CROSS_MIDNIGHT_SOURCE_ONLY",
                    "CDS_APPOINTMENT_DST_DURATION_SOURCE_ONLY",
                    "CDS_APPOINTMENT_LOCAL_TIME_SOURCE_ONLY");

    /**
     * @param nativeEnd the stored end is the scheduler's inclusive end and gives the element's
     *     Duration; false when the record agrees with the element but adds nothing to its timing
     */
    private record Proven(LocalDate date, LocalTime start, LocalTime end, boolean nativeEnd) {}

    /**
     * Proves the record against its element: the element's date and start, read in the receiving
     * clinic's zone, are the stored date and start, and the stored end gives the element's
     * Duration by the shared rule (inclusive, as the CARLOS scheduler stores it, or exclusive, as
     * an earlier CDS import left it).
     *
     * @return null when the record does not prove itself
     */
    private Proven proven(
            CdsAppointmentResidualReader.Source source,
            CdsTemporalDto sourceDate,
            String sourceTime,
            String duration) {
        String zone = configuration.value("cds.import.clinicalZone", "");
        if (zone.isEmpty()
                || !sourceDate.exactCalendarDate()
                || sourceDate.earliest() == null
                || source.date() == null
                || source.start() == null
                || !source.date().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !source.start().matches(CLOCK)) return null;
        LocalDate date = sourceDate.earliest();
        LocalTime start;
        try {
            try {
                OffsetTime offset = OffsetTime.parse(sourceTime);
                var local =
                        date.atTime(offset.toLocalTime())
                                .atOffset(offset.getOffset())
                                .atZoneSameInstant(ZoneId.of(zone));
                date = local.toLocalDate();
                start = local.toLocalTime();
            } catch (DateTimeParseException noOffset) {
                start = LocalTime.parse(sourceTime);
            }
            if (start.getNano() != 0
                    || date.getYear() < 1
                    || date.getYear() > 9999
                    || !date.equals(LocalDate.parse(source.date()))
                    || !start.equals(LocalTime.parse(source.start()))) return null;
        } catch (java.time.DateTimeException invalid) {
            return null;
        }
        LocalTime end =
                source.end() != null && source.end().matches(CLOCK)
                        ? LocalTime.parse(source.end())
                        : null;
        // A Duration the element could not state, or stated beyond a day, is in neither rule.
        long minutes = duration == null ? 0 : new BigInteger(duration).min(BigInteger.valueOf(1441)).longValue();
        if (end == null) return minutes == 0 ? new Proven(date, start, null, false) : null;
        if (CdsAppointmentSourceContract.minutes(start, end, true) == minutes)
            return new Proven(date, start, end, true);
        return minutes > 0 && CdsAppointmentSourceContract.minutes(start, end, false) == minutes
                ? new Proven(date, start, end, false)
                : null;
    }

    private static final String CLOCK = "(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]";

    /** Line endings as an XML parser delivers them, so a CR the writer kept is not a difference. */
    private static String lines(String text) {
        return text == null ? null : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** A stored scheduler value the receiving column can hold; a longer one is reported. */
    private static String column(
            String value, int capacity, Consumer<CdsClinicalDiagnosticDto> notice) {
        if (value == null || value.codePointCount(0, value.length()) <= capacity) return value;
        notice.accept(
                new CdsClinicalDiagnosticDto(
                        "CDS_APPOINTMENT_SOURCE_COLUMN_CAPACITY_NOT_RESTORED", "NewCategory"));
        return null;
    }

    /** Only a booking source this build knows is restored; any other is reported. */
    private static String bookingSource(String value, Consumer<CdsClinicalDiagnosticDto> notice) {
        if (value == null || value.isEmpty()) return null;
        for (var known : Appointment.BookingSource.values())
            if (known.name().equals(value)) return value;
        notice.accept(
                new CdsClinicalDiagnosticDto(
                        "CDS_APPOINTMENT_SOURCE_BOOKING_SOURCE_NOT_RESTORED", "NewCategory"));
        return null;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(
            String run,
            long epoch,
            String unit,
            int patient,
            CdsRecordDto record,
            CdsImportCommand command,
            Consumer<CdsClinicalDiagnosticDto> notices)
            throws IOException {
        var dto =
                preparation.load(
                        run,
                        epoch,
                        unit,
                        record.effectiveSha256(),
                        command,
                        CdsAppointmentNativeDto.class);
        dto.diagnostics().forEach(notices);
        if (!dto.representable()) return Map.of("demographic", Integer.toString(patient));
        Appointment appointment = new Appointment();
        appointment.setDemographicNo(patient);
        appointment.setAppointmentDate(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.dateStorageAnchor(LocalDate.parse(dto.date())));
        appointment.setStartTime(Time.valueOf(LocalTime.parse(dto.start())));
        appointment.setEndTime(Time.valueOf(LocalTime.parse(dto.end())));
        appointment.setProviderNo(dto.providerNo() == null ? "" : dto.providerNo());
        appointment.setName(appointmentName(run, epoch, patient));
        appointment.setProgramId(command.programId() == null ? 0 : command.programId());
        appointment.setReason(dto.purpose());
        appointment.setNotes(dto.notes());
        appointment.setImportedStatus(dto.status());
        appointment.setStatus("");
        appointment.setCreator(command.providerNo());
        appointment.setLastUpdateUser(command.providerNo());
        if (dto.source() != null) {
            // Scheduler context the sender's proven source record carried.
            var source = dto.source();
            if (source.style() != null) appointment.setStyle(source.style());
            if (source.billing() != null) appointment.setBilling(source.billing());
            if (source.location() != null) appointment.setLocation(source.location());
            if (source.resources() != null) appointment.setResources(source.resources());
            if (source.urgency() != null) appointment.setUrgency(source.urgency());
            if (source.type() != null) appointment.setType(source.type());
            if (source.bookingSource() != null)
                appointment.setBookingSource(
                        Appointment.BookingSource.valueOf(source.bookingSource()));
        }
        appointments.persist(appointment);
        appointments.flush();
        if (appointment.getId() == null || appointment.getId() < 1)
            throw new CdsImportException("APPOINTMENT_NATIVE_ID_MISSING");
        return Map.of("appointment", appointment.getId().toString());
    }

    /**
     * Receipts and appointment cards print {@code appointment.name} as stored, so imported
     * bookings carry the patient's name in the same "Last,First" form the scheduler writes
     * (AppointmentAddRecord2Action).
     */
    private String appointmentName(String run, long epoch, int patient) {
        PatientNameKey key = new PatientNameKey(run, epoch, patient);
        String cached = patientNames.get(key);
        if (cached != null) return cached;
        Demographic demographic = demographics.getDemographicById(patient);
        String name = demographic == null ? "" : demographic.getLastName() + "," + demographic.getFirstName();
        patientNames.put(key, name);
        return name;
    }

    private String bounded(
            CdsRecordDto record,
            String path,
            int capacity,
            Consumer<CdsClinicalDiagnosticDto> notice)
            throws IOException {
        try {
            return fields.firstToken(record, path, capacity);
        } catch (CdsInputException overflow) {
            if (!overflow.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw overflow;
            notice.accept(
                    new CdsClinicalDiagnosticDto("CDS_APPOINTMENT_DURATION_SOURCE_ONLY", path));
            return null;
        }
    }
}
