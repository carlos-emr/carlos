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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReceiptDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;

/** Preserves separately addressable source values in bounded, independently checkpointed field pages. */
@Service
public class CdsSourceFieldService {
    /** At most 1MiB of original inline text plus 32 bounded logical/corrected values per transaction. */
    public static final int PAGE_SIZE = 32;
    private final CdsFieldParser fields;
    private final CdsImportFieldPersister preservation;
    private final CdsImportFileService files;
    private final CdsTargetReceiptService receipts;
    private final CdsImportLedgerService ledger;

    public CdsSourceFieldService(CdsFieldParser fields, CdsImportFieldPersister preservation,
            CdsImportFileService files, CdsTargetReceiptService receipts, CdsImportLedgerService ledger) {
        this.fields = fields; this.preservation = preservation; this.files = files; this.receipts = receipts;
        this.ledger = ledger;
    }

    public CdsImportReceiptDto write(String runId, long epoch, String unitKey, CdsRecordDto page) throws IOException {
        if (page.fieldCount() > PAGE_SIZE) throw new CdsImportException("SOURCE_FIELD_UNIT_TOO_LARGE");
        // The complete immutable values spool is verified before entering a unit.
        // Large binary/text fields remain offset references instead of being materialized in a transaction.
        var targets = new LinkedHashMap<String, String>();
        String patientKey = ledger.unit(runId, unitKey).getPatientKey();
        files.referenceLocation(runId, epoch, unitKey, page.valuesFile().toString());
        fields.forEachCorrectionFile(page, true,
                (path, digest, length) -> files.referenceLocation(runId, epoch, unitKey, path.toString()));
        try {
            fields.forEachField(page, field -> {
                try {
                    String original = null, normalized = null, payload = null, digest = null;
                    long offset = 0, length = 0;
                    if (!field.kind().equals("BINARY") && field.length() <= 32768) {
                        original = fields.readText(page, field, 32768);
                        normalized = fields.readLogicalText(page, field, 65536);
                    } else {
                        payload = page.valuesFile().toString(); digest = field.sha256();
                        offset = field.offset(); length = field.length();
                    }
                    if (!field.kind().equals("BINARY")) {
                        var corrected = fields.correctedLogicalText(page, field, 65536);
                        if (corrected.isPresent()) normalized = corrected.get();
                    }
                    String path = page.sourceXPath() + (field.path().isEmpty() ? "" : "/" + field.path());
                    String type = field.schemaType().isBlank() ? field.kind() : field.schemaType();
                    String precision = precision(field);
                    String id = preservation.preserve(runId, epoch, unitKey, path, page.ordinal(), field.namespace(), type,
                            precision, original, normalized, payload, digest, offset, length);
                    targets.put("cds_import_field#" + targets.size(), id);
                    if (normalized != null && !java.util.Objects.equals(original, normalized)) {
                        ledger.diagnostic(runId, epoch, patientKey, unitKey, "INFO",
                                "CDS_FIELD_LOGICAL_NORMALIZATION_RETAINED", "NORMALIZATION", path,
                                "The XML logical value or audited correction is retained beside the original source value", null, false);
                    }
                } catch (IOException failed) { throw new UncheckedIOException(failed); }
            });
        } catch (UncheckedIOException failed) { throw failed.getCause(); }
        return receipts.capture(targets, runId, unitKey);
    }

    private static String precision(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto field) {
        if (field.path().endsWith("YearOnly[1]")) return "YEAR";
        if (field.path().endsWith("YearMonth[1]")) return "MONTH";
        if (field.path().endsWith("FullDate[1]")) return "FULL_DATE";
        if (field.path().endsWith("FullDateTime[1]")) return "DATE_TIME";
        String type = field.schemaType();
        type = type.substring(type.lastIndexOf('}') + 1);
        return switch (type) {
            case "dateYYYYMMDD", "date" -> "FULL_DATE";
            case "dateTimeYYYYMMDDTHHMMSS", "dateTime" -> "DATE_TIME";
            case "gYear" -> "YEAR";
            case "gYearMonth" -> "MONTH";
            default -> null;
        };
    }
}
