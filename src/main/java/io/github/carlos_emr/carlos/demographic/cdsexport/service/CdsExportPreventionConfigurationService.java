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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Freezes the trusted prevention configuration into owned encrypted storage, then independently
 * reopens it with the complete authenticated CVC capture. Recovery never rereads today's XML
 * configuration or mutable vocabulary. Category/selection/authentication/EOF failures cannot
 * return a classifier. This does not capture, authorize or reconcile patient prevention facts.
 */
@Service
public class CdsExportPreventionConfigurationService {
    private static final byte[] MAGIC = "CDSPCFG1".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> TABLES = Set.of("CVCImmunization", "CVCMapping");
    private static final int MAX_XML = 1_048_576,
            MAX_ROWS = 10_000,
            MAX_CATALOGUE_BYTES = 4_194_304;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;
    private final CdsExportCapturedTextRowsReader rows;
    private final String location;

    @Autowired
    public CdsExportPreventionConfigurationService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            @Value("${PREVENTION_ITEMS:classpath:oscar/prevention/PreventionItems.xml}")
                    String location) {
        this(captures, stores, new CdsExportCapturedTextRowsReader(captures), location);
    }

    CdsExportPreventionConfigurationService(
            CdsExportNativeCaptureService captures,
            CdsExportStagingFactory stores,
            CdsExportCapturedTextRowsReader rows,
            String location) {
        this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores);
        this.rows = Objects.requireNonNull(rows);
        this.location = Objects.requireNonNull(location);
    }

    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public CdsExportPreventionConfigurationDto freeze(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        scope(lease, patient, requiredTables, source);
        captures.authorizeSources(lease, patient, requiredTables, control);
        control.checkpoint();
        byte[] xml =
                requiredTables.contains("prevention_configuration")
                        ? capturedConfiguration(
                                lease,
                                patient,
                                requiredTables,
                                source,
                                maximumFields,
                                maximumBytes,
                                control)
                        : configuration();
        control.checkpoint();
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        CdsExportStagedFileDto file = null;
        boolean complete = false;
        Throwable primary = null;
        try {
            file =
                    store.stage(
                            "SOURCE_PREVENTION_CONFIG",
                            output -> {
                                output.write(MAGIC);
                                output.write(
                                        source.manifest()
                                                .plaintextSha256()
                                                .getBytes(StandardCharsets.US_ASCII));
                                var binary = new DataOutputStream(output);
                                binary.writeInt(xml.length);
                                binary.write(xml);
                            });
            var receipt =
                    new CdsExportPreventionConfigurationDto(
                            source.manifest().plaintextSha256(), sha256(xml), xml.length, file);
            restore(
                    lease,
                    patient,
                    requiredTables,
                    source,
                    receipt,
                    maximumFields,
                    maximumBytes,
                    control);
            complete = true;
            return receipt;
        } catch (IOException | RuntimeException | Error failed) {
            primary = failed;
            throw failed;
        } finally {
            Arrays.fill(xml, (byte) 0);
            if (!complete && file != null)
                try {
                    store.discardUnpublished(file);
                } catch (IOException | RuntimeException | Error cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
        }
    }

    public CdsExportPreventionClassifier restore(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto receipt,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        scope(lease, patient, requiredTables, source);
        if (receipt == null
                || !source.manifest().plaintextSha256().equals(receipt.sourceManifestSha256()))
            invalid();
        captures.authorizeSources(lease, patient, requiredTables, control);
        control.checkpoint();
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        byte[] xml;
        try (var input = new DataInputStream(store.open(receipt.member()))) {
            if (!Arrays.equals(input.readNBytes(8), MAGIC)
                    || !Arrays.equals(
                            input.readNBytes(64),
                            source.manifest()
                                    .plaintextSha256()
                                    .getBytes(StandardCharsets.US_ASCII))) invalid();
            int length = input.readInt();
            if (length != receipt.xmlBytes() || length < 1 || length > MAX_XML) invalid();
            xml = input.readNBytes(length);
            if (xml.length != length
                    || input.read() != -1
                    || !sha256(xml).equals(receipt.configurationSha256())) invalid();
        }
        return classifyCaptured(
                lease, patient, requiredTables, source, xml, maximumFields, maximumBytes, control);
    }

    /** Exact same-snapshot configuration; no auxiliary file or current-setting fallback is needed. */
    public CdsExportPreventionClassifier readEmbedded(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        scope(lease, patient, requiredTables, source);
        if (!requiredTables.contains("prevention_configuration")) invalid();
        captures.authorizeSources(lease, patient, requiredTables, control);
        control.checkpoint();
        byte[] xml =
                capturedConfiguration(
                        lease,
                        patient,
                        requiredTables,
                        source,
                        maximumFields,
                        maximumBytes,
                        control);
        return classifyCaptured(
                lease, patient, requiredTables, source, xml, maximumFields, maximumBytes, control);
    }

    private CdsExportPreventionClassifier classifyCaptured(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            byte[] xml,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        try {
            var catalogue = new ArrayList<CdsExportCapturedTextRowDto>();
            long[] bytes = {0};
            var actual =
                    rows.forEachTable(
                            lease,
                            patient,
                            requiredTables,
                            TABLES,
                            source.manifest(),
                            maximumFields,
                            maximumBytes,
                            16_384,
                            control,
                            row -> {
                                if (catalogue.size() >= MAX_ROWS) invalid();
                                for (String value : row.fields().values())
                                    if (value != null) {
                                        bytes[0] += value.getBytes(StandardCharsets.UTF_8).length;
                                        if (bytes[0] > MAX_CATALOGUE_BYTES) invalid();
                                    }
                                catalogue.add(row);
                            });
            if (!actual.equals(source.capture())) invalid();
            var classifier =
                    CdsExportPreventionClassifier.fromCaptured(
                            source.manifest().plaintextSha256(), xml, catalogue);
            captures.authorizeSources(lease, patient, requiredTables, control);
            patient.require(lease);
            control.checkpoint();
            return classifier;
        } finally {
            Arrays.fill(xml, (byte) 0);
        }
    }

    private static void scope(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source)
            throws IOException {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(patient);
        patient.require(lease);
        if (source == null
                || requiredTables == null
                || !requiredTables.containsAll(TABLES)
                || !requiredTables.equals(source.capture().tables().keySet())
                || !lease.document().command().categories().contains(CdsCategory.IMMUNIZATIONS)
                || !"SOURCE_MANIFEST".equals(source.manifest().purpose())) invalid();
    }

    /** Embedded bytes drove clinical selection; a later file or setting must never replace them. */
    private byte[] capturedConfiguration(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        var table = source.capture().tables().get("prevention_configuration");
        if (table == null
                || table.rows() != 1
                || !table.columns().equals(Set.of("sha256", "xml"))
                || !table.receiptColumns().isEmpty()) invalid();
        var values = new java.util.HashMap<String, ByteArrayOutputStream>();
        var hashes = new java.util.HashMap<String, String>();
        var actual =
                captures.scan(
                        lease,
                        patient,
                        requiredTables,
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        control,
                        new CdsExportNativeCaptureReader.Fields() {
                            @Override
                            public OutputStream output(CdsExportCapturedFieldDto field)
                                    throws IOException {
                                if (!field.table().equals("prevention_configuration"))
                                    return OutputStream.nullOutputStream();
                                if (!"configuration".equals(field.recordKey())
                                        || !Set.of("sha256", "xml").contains(field.column())
                                        || field.exactNumeric() != null
                                        || field.column().equals("sha256")
                                                && (field.kind()
                                                                != CdsExportCapturedFieldDto.Kind
                                                                        .UTF8
                                                        || field.bytes() != 64)
                                        || field.column().equals("xml")
                                                && (field.kind()
                                                                != CdsExportCapturedFieldDto.Kind
                                                                        .BINARY
                                                        || field.bytes() < 1
                                                        || field.bytes() > MAX_XML)
                                        || values.containsKey(field.column())) invalid();
                                var output = new ByteArrayOutputStream((int) field.bytes());
                                values.put(field.column(), output);
                                return output;
                            }

                            @Override
                            public void verified(CdsExportCapturedFieldDto field)
                                    throws IOException {
                                if (field.table().equals("prevention_configuration")) {
                                    if (hashes.putIfAbsent(field.column(), field.sha256()) != null)
                                        invalid();
                                }
                            }
                        });
        if (!actual.equals(source.capture())
                || !values.keySet().equals(Set.of("sha256", "xml"))
                || !hashes.keySet().equals(values.keySet())) invalid();
        byte[] xml = values.get("xml").toByteArray();
        String declared = values.get("sha256").toString(StandardCharsets.US_ASCII);
        if (!declared.matches("[0-9a-f]{64}")
                || !declared.equals(sha256(xml))
                || !declared.equals(hashes.get("xml"))) invalid();
        return xml;
    }

    private byte[] configuration() throws IOException {
        return new CdsExportPreventionConfigurationLoader(location).load();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_PREVENTION_CONFIG_INVALID");
    }
}
