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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Native single-day slots require exact representable timing; every source-only decision is explicit and retains complete values. */
@Service
public class CdsAppointmentPersister {
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService normalization;
    private final CdsImportConfigurationService configuration;
    private final CdsProviderResolver providers;
    private final CdsNativePreparationService preparation;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public CdsAppointmentPersister(
            CdsFieldParser fields,
            CdsClinicalNormalizationService normalization,
            CdsImportConfigurationService configuration,
            CdsProviderResolver providers,
            CdsNativePreparationService preparation) {
        this.fields = fields;
        this.normalization = normalization;
        this.configuration = configuration;
        this.providers = providers;
        this.preparation = preparation;
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
        if (representable) {
            try { io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.dateStorageAnchor(date); }
            catch (io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException unavailable) {
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
                        fields.firstText(record, "AppointmentNotes", 32768),
                        provider.localProviderNo(),
                        representable,
                        List.copyOf(diagnostics.values())));
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
        appointment.setName("");
        appointment.setProgramId(command.programId() == null ? 0 : command.programId());
        appointment.setReason(dto.purpose());
        appointment.setNotes(dto.notes());
        appointment.setImportedStatus(dto.status());
        appointment.setStatus("");
        appointment.setCreator(command.providerNo());
        appointment.setLastUpdateUser(command.providerNo());
        entityManager.persist(appointment);
        entityManager.flush();
        return Map.of("appointment", appointment.getId().toString());
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
