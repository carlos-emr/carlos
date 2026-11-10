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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.*;

/** Native chart assembly uses the same typed mappers and independent field closure as retained charts. */
public final class CdsExportNativeDemographicAssembler {
    public record Result(
            CdsExportDemographicEnrolmentProjectionDto demographic,
            CdsExportPharmacyProjectionService.Result pharmacy,
            CdsExportPersonalContactProjectionService.Result contacts,
            CdsExportProfessionalContactMapper.Result professional,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        public Result {
            fields = Map.copyOf(fields);
        }

        @Override
        public String toString() {
            return "CdsExportNativeDemographicAssembler.Result[protected]";
        }
    }

    public Result assemble(
            int patient,
            Set<CdsCategory> selected,
            String captureDigest,
            Map<String, List<CdsExportCapturedTextRowDto>> rows,
            int maximumRows,
            long maximumBytes,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(checkpoint);
        checkpoint.run();
        if (patient < 1
                || !selected.contains(CdsCategory.DEMOGRAPHICS)
                || captureDigest == null
                || !captureDigest.matches("[0-9a-f]{64}")
                || maximumRows < 1
                || maximumRows > 8192
                || maximumBytes < 1
                || maximumBytes > (64L << 20)
                || !rows.keySet()
                        .equals(
                                Set.of(
                                        "demographic",
                                        "demographicExt",
                                        "demographiccust",
                                        "demographicArchive",
                                        "provider",
                                        "Contact",
                                        "DemographicContact",
                                        "pharmacyInfo",
                                        "demographicPharmacy"))
                || rows.get("demographic").size() != 1
                || rows.get("demographiccust").size() > 1) invalid();
        var current = rows.get("demographic").getFirst();
        if (!"demographic".equals(current.table())
                || !String.format(Locale.ROOT, "%010d", patient).equals(current.recordKey())
                || !Integer.toString(patient).equals(current.fields().get("demographic_no")))
            invalid();
        var rawFields = new LinkedHashMap<>(current.fields());
        rawFields.remove("demographic_no");
        var raw = new CdsExportDemographicRowDto(patient, rawFields);
        var providers = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : rows.get("provider")) {
            checkpoint.run();
            if (!row.table().equals("provider")
                    || !row.fields()
                            .keySet()
                            .equals(
                                    io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto
                                            .COLUMNS)
                    || row.fields().get("provider_no") == null
                    || !CdsExportPublicProviderSourceReader.key(row.fields().get("provider_no"))
                            .equals(row.recordKey())
                    || providers.putIfAbsent(row.fields().get("provider_no"), row) != null)
                invalid();
        }
        var base =
                new CdsExportDemographicMapper()
                        .map(raw, providers.get(current.fields().get("provider_no")));
        base =
                new CdsExportDemographicPhonesMapper()
                        .map(
                                base,
                                raw,
                                rows.get("demographicExt"),
                                maximumRows,
                                maximumBytes,
                                checkpoint);
        var history = new CdsExportEnrolmentHistory(patient, maximumRows, maximumBytes, checkpoint);
        var archiveFields =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var archive : rows.get("demographicArchive")) {
            history.accept(archive, providers.get(archive.fields().get("roster_enrolled_to")));
            merge(
                    archiveFields,
                    CdsExportDemographicAncillaryReconciler.assessArchive(
                            patient, archive, checkpoint));
        }
        history.accept(current, providers.get(current.fields().get("roster_enrolled_to")));
        var enrolment = history.finish(rows.get("demographicArchive").size());
        var mappedFields = new LinkedHashMap<>(base.fields());
        var ancillary = CdsExportDemographicAncillaryReconciler.assess(current, checkpoint);
        for (var entry : ancillary.entrySet())
            if (!entry.getKey().column().equals("demographic_no"))
                mappedFields.put(entry.getKey().column(), baseAssessment(entry.getValue()));
        for (String column : CdsExportEnrolmentMapper.COLUMNS.keySet()) {
            var field =
                    enrolment
                            .fields()
                            .get(
                                    new CdsExportNativeFieldKeyDto(
                                            current.table(), current.recordKey(), column));
            if (field == null) invalid();
            mappedFields.put(column, baseAssessment(field));
        }
        base =
                new CdsExportDemographicMappingDto(
                        base.patient(),
                        base.names(),
                        base.birthDate(),
                        base.healthCard(),
                        base.chartNumber(),
                        base.administrativeGender(),
                        base.addresses(),
                        base.phones(),
                        base.officialLanguage(),
                        base.spokenLanguage(),
                        base.email(),
                        base.personStatus(),
                        base.enumeratedStatus(),
                        base.personStatusDate(),
                        base.sin(),
                        mappedFields,
                        base.primaryPhysician(),
                        base.relatedFields());
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var entry : base.fields().entrySet()) {
            var value = entry.getValue();
            fields.put(
                    new CdsExportNativeFieldKeyDto(
                            current.table(), current.recordKey(), entry.getKey()),
                    new CdsExportFieldAssessmentDto(
                            CdsExportFieldAssessmentDto.Presence.valueOf(value.presence().name()),
                            CdsExportFieldAssessmentDto.Outcome.valueOf(value.outcome().name()),
                            value.destination(),
                            value.rule()));
        }
        fields.put(
                new CdsExportNativeFieldKeyDto(
                        current.table(), current.recordKey(), "demographic_no"),
                ancillary.get(
                        new CdsExportNativeFieldKeyDto(
                                current.table(), current.recordKey(), "demographic_no")));
        merge(fields, base.relatedFields());
        var relatedEnrolment = new LinkedHashMap<>(enrolment.fields());
        relatedEnrolment.keySet().removeIf(key -> key.table().equals("demographic"));
        merge(fields, relatedEnrolment);
        merge(fields, archiveFields);
        CdsExportPatientNoteMappingDto note = null;
        if (!rows.get("demographiccust").isEmpty()) {
            var custom = rows.get("demographiccust").getFirst();
            note = new CdsExportPatientNoteMapper().map(patient, selected, custom);
            merge(fields, note.fields());
            for (var key : note.delegatedFields()) {
                String value = custom.fields().get(key.column());
                fields.put(
                        key,
                        new CdsExportFieldAssessmentDto(
                                value == null
                                        ? CdsExportFieldAssessmentDto.Presence.SQL_NULL
                                        : value.isEmpty()
                                                ? CdsExportFieldAssessmentDto.Presence.EMPTY
                                                : CdsExportFieldAssessmentDto.Presence.VALUE,
                                value == null || value.isEmpty()
                                        ? CdsExportFieldAssessmentDto.Outcome.ABSENT
                                        : CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW,
                                "",
                                "NATIVE_LEGACY_ALERT_CARRIER_REQUIRES_REVIEW_UNLESS_ABSENT"));
            }
        }
        var pharmacy =
                new CdsExportPharmacyMapper()
                        .map(
                                patient,
                                rows.get("demographicPharmacy"),
                                rows.get("pharmacyInfo"),
                                maximumRows,
                                maximumBytes,
                                checkpoint);
        var professionalLinks =
                rows.get("DemographicContact").stream()
                        .filter(row -> "professional".equals(row.fields().get("category")))
                        .toList();
        var professionalIds = new HashSet<String>();
        for (var link : professionalLinks)
            if ("2".equals(link.fields().get("type")))
                professionalIds.add(link.fields().get("contactId"));
        var personalLinks =
                rows.get("DemographicContact").stream()
                        .filter(row -> !"professional".equals(row.fields().get("category")))
                        .toList();
        var personalIds = new HashSet<String>();
        for (var link : personalLinks) personalIds.add(link.fields().get("contactId"));
        var professionalContacts =
                rows.get("Contact").stream()
                        .filter(row -> professionalIds.contains(row.fields().get("id")))
                        .toList();
        var personalContacts =
                rows.get("Contact").stream()
                        .filter(
                                row ->
                                        !professionalIds.contains(row.fields().get("id"))
                                                || personalIds.contains(row.fields().get("id")))
                        .toList();
        var professional =
                new CdsExportProfessionalContactMapper()
                        .map(
                                patient,
                                professionalLinks,
                                professionalContacts,
                                maximumRows,
                                maximumBytes,
                                checkpoint);
        var contacts =
                new CdsExportPersonalContactMapper()
                        .map(
                                patient,
                                personalLinks,
                                personalContacts,
                                maximumRows,
                                maximumBytes,
                                checkpoint);
        merge(fields, pharmacy.fields());
        merge(fields, contacts.fields());
        merge(fields, professional.fields());
        var projection =
                new CdsExportDemographicEnrolmentProjectionDto(
                        captureDigest,
                        base,
                        enrolment.sourceRows(),
                        note == null ? 0 : 1,
                        note,
                        enrolment.events(),
                        enrolment.fields());
        if (!projection.representable()
                || !pharmacy.nativeRepresentable()
                || !contacts.nativeRepresentable()
                || !professional.representable()) review();
        var expected = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.values().forEach(expected::addAll);
        var closure =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(expected, fields, 1_000_000, checkpoint);
        if (!closure.representable()) review();
        checkpoint.run();
        return new Result(
                projection,
                new CdsExportPharmacyProjectionService.Result(captureDigest, pharmacy),
                new CdsExportPersonalContactProjectionService.Result(captureDigest, contacts),
                professional,
                fields);
    }

    private static CdsExportDemographicMappingDto.Assessment baseAssessment(
            CdsExportFieldAssessmentDto value) {
        return new CdsExportDemographicMappingDto.Assessment(
                CdsExportDemographicMappingDto.Presence.valueOf(value.presence().name()),
                CdsExportDemographicMappingDto.Outcome.valueOf(value.outcome().name()),
                value.destination(),
                value.rule());
    }

    private static void merge(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> into,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> values)
            throws IOException {
        for (var entry : values.entrySet()) {
            var prior = into.putIfAbsent(entry.getKey(), entry.getValue());
            if (prior == null || prior.equals(entry.getValue())) continue;
            var value = entry.getValue();
            if (!entry.getKey().table().equals("provider") || prior.presence() != value.presence())
                invalid();
            boolean mapped =
                    prior.outcome() == CdsExportFieldAssessmentDto.Outcome.MAPPED
                            || value.outcome() == CdsExportFieldAssessmentDto.Outcome.MAPPED;
            boolean review =
                    prior.outcome() == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW
                            || value.outcome()
                                    == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW;
            into.put(
                    entry.getKey(),
                    new CdsExportFieldAssessmentDto(
                            prior.presence(),
                            review
                                    ? CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW
                                    : mapped
                                            ? CdsExportFieldAssessmentDto.Outcome.MAPPED
                                            : prior.outcome(),
                            prior.destination()
                                    + (value.destination().equals(prior.destination())
                                            ? ""
                                            : "," + value.destination()),
                            "EXACT_SHARED_NATIVE_PUBLIC_PROVIDER_USES"));
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_NATIVE_DEMOGRAPHIC_SCOPE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }
}
