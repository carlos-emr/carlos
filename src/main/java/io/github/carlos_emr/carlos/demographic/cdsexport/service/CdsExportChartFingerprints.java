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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The two digests a finished chart is reused by.
 *
 * <p>{@link #capture} is the fingerprint of one patient's capture: every captured table with its
 * column set, row count, schema digest, receipt recipe and note scopes, and the length and SHA-256
 * of the capture's value and index members. The composer writes those two members from the rows
 * alone, in a fixed order, with nothing of the attempt in them ({@link
 * CdsExportNativeCaptureComposer}), so two captures of unchanged rows have the same fingerprint and
 * any changed row, column, stored document or report file, frozen reference input (drug
 * reference, code tables, prevention configuration, clinical zone) or eForm rendering gives a
 * different one. No run, epoch, time or member identifier enters it.
 *
 * <p>{@link #parameters} is the digest of everything a chart's build depends on that is not
 * captured and can differ between two attempts of one job: the job (run, compatibility digest,
 * sender namespace, profile, categories and captured tables), the application version, the
 * clinical zone, the occurrence bounds and the XML limits. A chain made under other parameters is
 * never adopted from.
 *
 * @since 2026-11-14
 */
final class CdsExportChartFingerprints {
    private CdsExportChartFingerprints() {}

    /** The SHA-256 of one capture's canonical description. */
    static String capture(CdsExportNativeCaptureDto capture) {
        Objects.requireNonNull(capture);
        var json = CdsExportChartCheckpointReader.JSON;
        ObjectNode root = json.createObjectNode();
        root.put("fields", capture.fieldCount());
        root.set("index", member(capture.index()));
        root.put("patient", capture.patient());
        root.put("protocol", 1);
        var tables = root.putObject("tables");
        for (var entry : new TreeMap<>(capture.tables()).entrySet()) {
            var scope = entry.getValue();
            ObjectNode table = tables.putObject(entry.getKey());
            ArrayNode columns = table.putArray("columns");
            for (String column : new TreeSet<>(scope.columns())) columns.add(column);
            ArrayNode notes = table.putArray("noteAccess");
            for (CdsExportNoteAccessDto note : scope.noteAccess()) {
                ObjectNode value = notes.addObject();
                value.put("noteId", note.noteId());
                value.put("patient", note.patient());
                value.put("program", note.program());
                value.put("reporterRole", note.reporterRole());
            }
            table.set("receiptColumns", canonical(json.valueToTree(scope.receiptColumns())));
            table.put("rows", scope.rows());
            table.put("schemaSha256", scope.schemaSha256());
        }
        root.set("values", member(capture.values()));
        root.put("valueBytes", capture.valueBytes());
        return sha256(root);
    }

    private static ObjectNode member(CdsExportStagedFileDto file) {
        ObjectNode value = CdsExportChartCheckpointReader.JSON.createObjectNode();
        value.put("bytes", file.plaintextBytes());
        value.put("sha256", file.plaintextSha256());
        return value;
    }

    /** The SHA-256 of the build parameters of one attempt's charts. */
    static String parameters(
            CdsExportLeaseDto lease,
            String applicationVersion,
            ZoneId clinicalZone,
            Object bounds,
            CdsExportXmlLimitsDto xmlLimits,
            Set<String> tables) {
        var json = CdsExportChartCheckpointReader.JSON;
        var document = lease.document();
        var command = document.command();
        ObjectNode root = json.createObjectNode();
        root.put("applicationVersion", Objects.requireNonNull(applicationVersion));
        root.set("bounds", canonical(json.valueToTree(Objects.requireNonNull(bounds))));
        ArrayNode categories = root.putArray("categories");
        for (var category : new TreeSet<>(command.categories().stream().map(Enum::name).toList()))
            categories.add(category);
        root.put("clinicalZone", clinicalZone.getId());
        root.put("compatibilityDigest", document.compatibilityDigest());
        root.put("profile", command.profile().name());
        root.put("protocol", 1);
        root.put("runId", lease.runId());
        root.put("senderNamespace", document.senderNamespace());
        ArrayNode captured = root.putArray("tables");
        for (String table : new TreeSet<>(tables)) captured.add(table);
        root.set("xmlLimits", canonical(json.valueToTree(Objects.requireNonNull(xmlLimits))));
        return sha256(root);
    }

    /** The same value with every object's members in name order, so equal values encode equally. */
    static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            for (Map.Entry<String, JsonNode> member : value.properties())
                sorted.put(member.getKey(), canonical(member.getValue()));
            ObjectNode result = CdsExportChartCheckpointReader.JSON.createObjectNode();
            sorted.forEach(result::set);
            return result;
        }
        if (value.isArray()) {
            var items = new ArrayList<JsonNode>();
            for (var item : value) items.add(canonical(item));
            ArrayNode result = CdsExportChartCheckpointReader.JSON.createArrayNode();
            items.forEach(result::add);
            return result;
        }
        return value;
    }

    private static String sha256(JsonNode value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            CdsExportChartCheckpointReader.JSON.writeValueAsBytes(
                                                    value)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
