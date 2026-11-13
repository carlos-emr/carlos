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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.security.CdsNativeBaselineKeys;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Complete frozen metadata classification after both authenticated EOFs, protected configuration
 * and final live authority. No clinical serialization or coverage claim. With Immunizations or
 * PastHealth selected, a row the classification cannot place is an immunization when an authenticated
 * import receipt among the captured {@code cds_import_unit} rows proves it was imported from a
 * CDS Immunizations element, exactly as capture-time selection decided
 * ({@link CdsExportImmunizationImportReceipts}).
 */
@Service
public class CdsExportPreventionClassificationService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportPreventionConfigurationService configuration;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsNativeBaselineKeys keys;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPreventionClassificationService(
            CdsExportNativeCaptureService captures,
            CdsExportPreventionConfigurationService configuration,
            @Qualifier("cdsExportProperties") Properties settings) {
        this(
                captures,
                configuration,
                new CdsExportCapturedTextRowsReader(captures),
                CdsNativeBaselineKeys.fromProperties(settings));
    }

    /** Without installation keys: a sealed witness that must be opened refuses the job. */
    CdsExportPreventionClassificationService(
            CdsExportNativeCaptureService captures,
            CdsExportPreventionConfigurationService configuration,
            CdsExportCapturedTextRowsReader rows) {
        this(captures, configuration, rows, CdsNativeBaselineKeys.of(null, Map.of()));
    }

    CdsExportPreventionClassificationService(
            CdsExportNativeCaptureService captures,
            CdsExportPreventionConfigurationService configuration,
            CdsExportCapturedTextRowsReader rows,
            CdsNativeBaselineKeys keys) {
        this.captures = Objects.requireNonNull(captures);
        this.configuration = Objects.requireNonNull(configuration);
        this.rows = Objects.requireNonNull(rows);
        this.keys = Objects.requireNonNull(keys);
    }

    public CdsExportPreventionScopePlanner.Result classify(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto configured,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        return classify(
                lease,
                patient,
                requiredTables,
                source,
                configured,
                maximumFields,
                maximumBytes,
                control,
                false);
    }

    public CdsExportPreventionScopePlanner.Result classifyEmbedded(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        return classify(
                lease,
                patient,
                requiredTables,
                source,
                null,
                maximumFields,
                maximumBytes,
                control,
                true);
    }

    private CdsExportPreventionScopePlanner.Result classify(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto configured,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control,
            boolean embedded)
            throws IOException {
        patient.require(lease);
        if (!requiredTables.contains("preventions_scope")
                || !requiredTables.equals(source.capture().tables().keySet()))
            throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        var classifier =
                embedded
                        ? configuration.readEmbedded(
                                lease,
                                patient,
                                requiredTables,
                                source,
                                maximumFields,
                                maximumBytes,
                                control)
                        : configuration.restore(
                                lease,
                                patient,
                                requiredTables,
                                source,
                                configured,
                                maximumFields,
                                maximumBytes,
                                control);
        if (!source.manifest().plaintextSha256().equals(classifier.sourceManifestSha256()))
            throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        var metadata = new ArrayList<CdsExportCapturedTextRowDto>();
        long[] bytes = {0};
        var actual =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        "preventions_scope",
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        4096,
                        control,
                        row -> {
                            if (metadata.size() >= 10_000)
                                throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_LIMIT");
                            for (String value : row.fields().values())
                                if (value != null) {
                                    bytes[0] += value.getBytes(StandardCharsets.UTF_8).length;
                                    if (bytes[0] > 4_194_304)
                                        throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_LIMIT");
                                }
                            metadata.add(row);
                        });
        if (!actual.equals(source.capture()))
            throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        var result =
                new CdsExportPreventionScopePlanner()
                        .plan(
                                patient.patient(),
                                classifier,
                                metadata,
                                candidates ->
                                        imported(
                                                lease,
                                                patient,
                                                requiredTables,
                                                source,
                                                maximumFields,
                                                maximumBytes,
                                                control,
                                                candidates));
        captures.authorizeSources(lease, patient, requiredTables, control);
        patient.require(lease);
        control.checkpoint();
        return result;
    }

    /**
     * Candidate rows proved by the captured Immunizations import receipts. The capture copies the
     * patient's Immunizations units whenever a prevention category is selected, the same case in
     * which capture-time selection consults them; a proved vaccine is then an immunization, so it
     * never holds a PastHealth-only export.
     */
    private Set<Integer> imported(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control,
            Set<Integer> candidates)
            throws IOException {
        if (Collections.disjoint(
                        lease.document().command().categories(),
                        Set.of(CdsCategory.IMMUNIZATIONS, CdsCategory.PAST_HEALTH))
                || !requiredTables.contains(CdsExportImmunizationImportReceipts.TABLE))
            return Set.of();
        var proof =
                new CdsExportImmunizationImportReceipts.Proof(patient.patient(), candidates, keys);
        var actual =
                rows.forEach(
                        lease,
                        patient,
                        requiredTables,
                        CdsExportImmunizationImportReceipts.TABLE,
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        CdsExportImmunizationImportReceipts.MAX_UNIT_ROW_BYTES,
                        control,
                        row -> {
                            if (!CdsExportImmunizationImportReceipts.TABLE.equals(row.table())
                                    || !row.fields()
                                            .keySet()
                                            .equals(CdsExportImmunizationImportReceipts.COLUMNS))
                                throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
                            control.checkpoint();
                            proof.accept(row.fields());
                        });
        if (!actual.equals(source.capture()))
            throw new IOException("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
        return proof.proven();
    }
}
