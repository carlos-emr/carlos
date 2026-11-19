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

import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsNativeIssuesDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;

/** Reads one restorable marker; source-only copies never restore rows. */
final class CdsNativeIssuesReader {
    private static final Set<String> PATIENT_COLUMNS =
            Set.of("acute", "certain", "major", "resolved", "type", "program_id", "update_date");
    private static final Set<String> CATALOGUE_COLUMNS =
            Set.of("code", "description", "priority", "role", "sortOrderId", "type", "update_date");
    private final CdsFieldParser fields;
    private final CdsPatientSourceRecordReader records;

    CdsNativeIssuesReader(CdsFieldParser fields) {
        this.fields = java.util.Objects.requireNonNull(fields);
        records = new CdsPatientSourceRecordReader(fields);
    }

    CdsNativeIssuesDto read(CdsRecordDto record) throws IOException {
        if (!records.named(record, CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY))
            return new CdsNativeIssuesDto(null);
        var content = records.content(record, CdsNativeExtensionContract.NATIVE_ISSUES);
        if (content == null) throw refused(record);
        var paths = new java.util.HashSet<String>();
        PATIENT_COLUMNS.forEach(column -> paths.add("casemgmt_issue/" + column));
        CATALOGUE_COLUMNS.forEach(column -> paths.add("issue/" + column));
        try (var input = fields.openToken(record, content)) {
            return new CdsNativeIssuesDto(marker(CdsSourceContextParser.read(input, paths)));
        } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
            throw refused(record);
        }
    }

    private static CdsNativeIssuesDto.Marker marker(JsonNode element) {
        var patient = CdsSourceContextParser.row(element, "casemgmt_issue",
                "acute", "certain", "major", "resolved", "type", "update_date");
        var definition = CdsSourceContextParser.row(element, "issue",
                "code", "description", "role", "sortOrderId", "update_date");
        String code = text(definition, "code", 20, false);
        String definitionType = text(definition, "type", 32, true);
        if (!CdsNativeExtensionContract.HISTORY_SECTION_TAGS.contains(code)
                && !"system".equals(definitionType)) invalid();
        var catalogue = new LinkedHashMap<String, String>();
        catalogue.put("code", code);
        catalogue.put("description", text(definition, "description", 255, false));
        catalogue.put("role", text(definition, "role", 100, false));
        catalogue.put("priority", text(definition, "priority", 10, true));
        catalogue.put("type", definitionType);
        catalogue.put("sortOrderId", text(definition, "sortOrderId", 11, false));
        catalogue.put("update_date", text(definition, "update_date", 26, false));
        if (!Integer.toString(Integer.parseInt(catalogue.get("sortOrderId")))
                .equals(catalogue.get("sortOrderId"))) invalid();
        String program = text(patient, "program_id", 11, true);
        String time = text(patient, "update_date", 26, false);
        calendar(time);
        return new CdsNativeIssuesDto.Marker(code, catalogue, 0,
                flag(patient, "acute"), flag(patient, "certain"), flag(patient, "major"),
                flag(patient, "resolved"), text(patient, "type", 100, false),
                program == null ? null : Integer.valueOf(program), time);
    }

    static java.time.LocalDateTime calendar(String value) {
        var time = java.time.LocalDateTime.parse(value.replace(' ', 'T'));
        if (time.getYear() < 1 || time.getYear() > 9999 || time.getNano() != 0) invalid();
        return time;
    }

    private static boolean flag(JsonNode row, String column) {
        String value = text(row, column, 1, false);
        if (!"0".equals(value) && !"1".equals(value)) invalid();
        return "1".equals(value);
    }

    private static String text(JsonNode row, String column, int capacity, boolean nullable) {
        var value = row.get(column);
        if (value == null || value.isNull()) {
            if (!nullable) invalid();
            return null;
        }
        if (!value.isTextual()) invalid();
        String result = value.textValue();
        if (result.codePointCount(0, result.length()) > capacity) invalid();
        return result;
    }

    private static void invalid() { throw new IllegalArgumentException("CDS_NATIVE_ISSUES_MALFORMED"); }

    private static CdsInputException refused(CdsRecordDto record) {
        return new CdsInputException("CDS_NATIVE_ISSUES_MALFORMED", record.sourceXPath(),
                "The CARLOS system-marker record cannot be restored exactly.");
    }
}
