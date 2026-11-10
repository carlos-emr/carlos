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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Maps only the independently verified frozen vaccine partition, with bounded child indexes.
 * Results and callbacks remain private until all source generations, child counts, authenticated
 * EOFs and final live authority pass. No mutable clinical/configuration rows are reread.
 */
@Service
public class CdsExportImmunizationProjectionService {
    @FunctionalInterface
    public interface Mappings {
        void accept(CdsExportImmunizationMappingDto value) throws IOException;
    }

    private final CdsExportNativeCaptureService captures;
    private final CdsExportImmunizationCaptureValidator closure;
    private final CdsExportPreventionConfigurationService configurations;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportClinicalZoneService zones;

    public CdsExportImmunizationProjectionService(
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations) {
        this(captures, closure, configurations, new CdsExportCapturedTextRowsReader(captures));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportImmunizationProjectionService(
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportClinicalZoneService zones) {
        this(
                captures,
                closure,
                configurations,
                new CdsExportCapturedTextRowsReader(captures),
                zones);
    }

    CdsExportImmunizationProjectionService(
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportCapturedTextRowsReader rows) {
        this(captures, closure, configurations, rows, new CdsExportClinicalZoneService(captures));
    }

    CdsExportImmunizationProjectionService(
            CdsExportNativeCaptureService captures,
            CdsExportImmunizationCaptureValidator closure,
            CdsExportPreventionConfigurationService configurations,
            CdsExportCapturedTextRowsReader rows,
            CdsExportClinicalZoneService zones) {
        this.captures = Objects.requireNonNull(captures);
        this.closure = Objects.requireNonNull(closure);
        this.configurations = Objects.requireNonNull(configurations);
        this.rows = Objects.requireNonNull(rows);
        this.zones = Objects.requireNonNull(zones);
    }

    public CdsExportImmunizationProjectionDto project(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto configuration,
            long maximumFields,
            long maximumBytes,
            int maximumAuxiliaryRows,
            long maximumAuxiliaryBytes,
            CdsExportRunControl control,
            Mappings output)
            throws IOException {
        Objects.requireNonNull(output);
        patient.require(lease);
        control.checkpoint();
        if (maximumAuxiliaryRows < 1
                || maximumAuxiliaryRows > 131_072
                || maximumAuxiliaryBytes < 1
                || maximumAuxiliaryBytes > 67_108_864
                || !tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(CdsExportImmunizationCaptureValidator.REQUIRED))
            throw new IllegalArgumentException("CDS_EXPORT_IMMUNIZATION_INPUTS_REQUIRED");
        var selection =
                closure.verify(
                        lease,
                        patient,
                        tables,
                        source,
                        configuration,
                        maximumFields,
                        maximumBytes,
                        control);
        if (selection.patient() != patient.patient()
                || !selection.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !selection.representable()) scope();
        var definitions =
                configurations.restore(
                        lease,
                        patient,
                        tables,
                        source,
                        configuration,
                        maximumFields,
                        maximumBytes,
                        control);
        if (!definitions.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            scope();
        var zone =
                tables.contains("clinical_timezone")
                        ? zones.read(
                                lease,
                                patient,
                                tables,
                                source,
                                maximumFields,
                                maximumBytes,
                                control)
                        : null;
        if (zone != null
                && !zone.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            scope();
        var extensions = new HashMap<Integer, List<CdsExportCapturedTextRowDto>>();
        var precision = new HashMap<Integer, List<CdsExportCapturedTextRowDto>>();
        long[] totals = new long[6];
        var auxiliary =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        Set.of("preventionsExt", "prevention_precision"),
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        67_108_864,
                        control,
                        row -> {
                            if (++totals[0] > maximumAuxiliaryRows) limit();
                            for (String value : row.fields().values())
                                if (value != null) {
                                    totals[1] =
                                            Math.addExact(
                                                    totals[1],
                                                    value.getBytes(StandardCharsets.UTF_8).length);
                                    if (totals[1] > maximumAuxiliaryBytes) limit();
                                }
                            int owner =
                                    integer(
                                            row.fields()
                                                    .get(
                                                            row.table().equals("preventionsExt")
                                                                    ? "prevention_id"
                                                                    : "table_id"));
                            if (!selection.immunizations().contains(owner)) scope();
                            if (row.table().equals("preventionsExt")) {
                                extensions
                                        .computeIfAbsent(owner, ignored -> new ArrayList<>())
                                        .add(row);
                                totals[2]++;
                            } else if (row.table().equals("prevention_precision")) {
                                precision
                                        .computeIfAbsent(owner, ignored -> new ArrayList<>())
                                        .add(row);
                                totals[3]++;
                            } else scope();
                        });
        if (!auxiliary.equals(source.capture())
                || totals[2] != auxiliary.tables().get("preventionsExt").rows()
                || totals[3] != auxiliary.tables().get("prevention_precision").rows()) scope();
        var seen = new HashSet<Integer>();
        long[] assessed = {0};
        var parents =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        "preventions",
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        4096,
                        control,
                        row -> {
                            patient.require(lease);
                            int id = integer(row.fields().get("id"));
                            if (!selection.immunizations().contains(id) || !seen.add(id)) scope();
                            var child = extensions.remove(id);
                            var dates = precision.remove(id);
                            var mapping =
                                    new CdsExportImmunizationMapper(
                                                    zone == null ? null : zone.zone())
                                            .map(
                                                    patient.patient(),
                                                    row,
                                                    child == null ? List.of() : child,
                                                    dates == null ? List.of() : dates,
                                                    definitions);
                            totals[4]++;
                            if (!mapping.nativeRowRepresentable()) totals[5]++;
                            assessed[0] = Math.addExact(assessed[0], mapping.fields().size());
                            output.accept(mapping);
                        });
        if (!parents.equals(source.capture())) scope();
        long expected =
                Math.addExact(
                        Math.multiplyExact(source.capture().tables().get("preventions").rows(), 16),
                        Math.addExact(
                                Math.multiplyExact(totals[2], 4),
                                Math.multiplyExact(totals[3], 5)));
        if (!parents.equals(source.capture())
                || !seen.equals(selection.immunizations())
                || !extensions.isEmpty()
                || !precision.isEmpty()
                || totals[4] != parents.tables().get("preventions").rows()
                || assessed[0] != expected) scope();
        captures.authorizeSources(lease, patient, tables, control);
        patient.require(lease);
        control.checkpoint();
        return new CdsExportImmunizationProjectionDto(
                source.manifest().plaintextSha256(), totals[4], totals[5], assessed[0]);
    }

    private static int integer(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (!Integer.toString(value).equals(text)) scope();
            return value;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
        }
    }

    private static void limit() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_AUXILIARY_RESOURCE_LIMIT");
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_PROJECTION_SCOPE_INVALID");
    }
}
