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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.util.Base64;
import java.util.Map;

import cds.ReportsDocument.Reports;
import cdsDt.ReportClass;
import cdsDt.ReportFormat;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * Maps an HL7 lab OBX that carries an embedded document (value type {@code ED}) onto an
 * OntarioMD CDS {@code Reports} element.
 *
 * <p>The CDS schema's {@code LaboratoryResults/Result/Value} is a 120-character result (or an
 * inline base64 blob that no receiving EMR presents as a document), so a lab PDF written there is
 * either truncated or lost to the clinician. {@code Reports} is where the schema carries
 * documents: {@code Format=Binary} with the decoded bytes in {@code Content/Media}, classed as
 * {@code Lab Report}. The import side of CARLOS already turns such a report back into a patient
 * document, so the PDF survives a CARLOS-to-CARLOS migration.</p>
 *
 * <p>Payload handling:</p>
 * <ul>
 *   <li>A payload that is strict base64 (after removing the line breaks HL7 senders insert) is
 *       decoded and exported as {@code Binary}. The file extension is taken from the decoded
 *       bytes' signature (PDF, PNG, JPEG, GIF, TIFF, RTF), written in the {@code ".ext"} form the
 *       exporter already uses for documents; an unrecognised signature is exported without one
 *       and reported, rather than guessed from the OBX identifier.</li>
 *   <li>Anything else (some senders put a text report or a reference in an {@code ED} segment)
 *       is exported as a {@code Text} report so nothing is dropped.</li>
 * </ul>
 *
 * <p>All text is passed through {@link CdsXmlText#stripInvalidXmlCharacters(String)} and cut to
 * the schema's lengths, so the element validates. Reviewer and physician annotations are added by
 * {@link DemographicExportAction42Action}, which owns the lab routing lookup.</p>
 *
 * <p>Adapted from {@code mapLaboratoryResultToReport} in open-osp/Open-O 4417ba821c
 * (Colcamex Resources Inc.). Changes from upstream: strict decoding instead of a regex plus a
 * lenient decoder; the file extension comes from the content, not the OBX identifier; the
 * received/sent dates are not filled from the observation and request dates (they are different
 * events); the ordering provider is not written as a recipient; and lab comments are kept rather
 * than overwritten by the patient-level note.</p>
 *
 * @since 2026-09-26
 */
public final class CdsEmbeddedLabDocument {

    /** Lab map key holding the OBX-5 payload. */
    static final String KEY_PAYLOAD = "measureData";

    static final int SUB_CLASS_MAX = 60;
    static final int SOURCE_FACILITY_MAX = 120;
    static final int MESSAGE_UNIQUE_ID_MAX = 250;
    static final int NOTES_MAX = 32000;

    /** Shortest base64 payload without a known signature that is still treated as a document. */
    static final int MIN_UNRECOGNISED_BINARY_LENGTH = 64;

    private CdsEmbeddedLabDocument() {
    }

    /**
     * What {@link #writeReport(Map, Reports)} produced, so the caller can count the entry and
     * surface the warning in the export log.
     *
     * @param format  the report format written ({@code Binary} or {@code Text})
     * @param warning a non-PHI description of anything the caller should log, or {@code null}
     */
    public record Outcome(ReportFormat.Enum format, String warning) {
    }

    /**
     * Fills {@code report} from one lab OBX.
     *
     * @param lab    the lab values the exporter gathers per OBX ({@code measureData}, {@code name},
     *               {@code labname}, {@code datetime}, {@code accession}, {@code comments})
     * @param report a freshly added, empty {@code Reports} element
     * @return the format written and any warning for the export log
     */
    public static Outcome writeReport(Map<String, String> lab, Reports report) {
        String payload = CdsXmlText.stripInvalidXmlCharacters(StringUtils.noNull(lab.get(KEY_PAYLOAD)));
        String warning = null;

        byte[] decoded = decodeDocument(payload);
        cdsDt.ReportContent content = report.addNewContent();
        ReportFormat.Enum format;
        if (decoded != null) {
            format = ReportFormat.BINARY;
            report.setFormat(format);
            content.setMedia(decoded);
            String extension = extensionFor(decoded);
            if (extension != null) {
                report.setFileExtensionAndVersion(extension);
            } else {
                warning = "embedded lab document has an unrecognised file type; exported without a file extension";
            }
        } else {
            format = ReportFormat.TEXT;
            report.setFormat(format);
            content.setTextContent(payload);
            report.setFileExtensionAndVersion(".txt");
        }

        report.setClass1(ReportClass.LAB_REPORT);

        String name = clean(lab.get("name"));
        if (StringUtils.filled(name)) {
            report.setSubClass(CdsXmlText.truncate(name, SUB_CLASS_MAX));
        }

        String observed = lab.get("datetime");
        if (StringUtils.filled(observed)) {
            report.addNewEventDateTime().setFullDateTime(Util.calDate(observed));
        }

        String facility = clean(lab.get("labname"));
        if (StringUtils.filled(facility)) {
            report.setSourceFacility(CdsXmlText.truncate(facility, SOURCE_FACILITY_MAX));
        }

        String accession = clean(lab.get("accession"));
        if (StringUtils.filled(accession)) {
            report.setMessageUniqueID(CdsXmlText.truncate(accession, MESSAGE_UNIQUE_ID_MAX));
        }

        String comments = clean(lab.get("comments"));
        if (StringUtils.filled(comments)) {
            report.setNotes(CdsXmlText.truncate(Util.replaceTags(comments), NOTES_MAX));
        }
        return new Outcome(format, warning);
    }

    /**
     * Decodes a payload that is a document, or returns {@code null} for one that should be
     * exported as text (see the class comment for the rule).
     */
    static byte[] decodeDocument(String payload) {
        byte[] bytes = decodeBase64(payload);
        if (bytes == null) {
            return null;
        }
        if (extensionFor(bytes) != null
                || payload.replaceAll("\\s+", "").length() >= MIN_UNRECOGNISED_BINARY_LENGTH) {
            return bytes;
        }
        return null;
    }

    /**
     * Decodes an HL7 {@code ED} payload.
     *
     * <p>Strict RFC 4648 decoding after dropping whitespace (HL7 senders wrap base64 at 76 or
     * 80 columns). The lenient commons-codec decoder is deliberately not used: it skips any
     * non-alphabet byte, so it "decodes" ordinary words into garbage and cannot tell a text report
     * from a document.</p>
     *
     * @return the bytes, or {@code null} when the payload is empty or not valid base64
     */
    static byte[] decodeBase64(String payload) {
        if (payload == null) {
            return null;
        }
        String compact = payload.replaceAll("\\s+", "");
        if (compact.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(compact);
            return bytes.length == 0 ? null : bytes;
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    /**
     * Identifies a document by its leading bytes.
     *
     * @return the extension in the {@code ".ext"} form {@link Util#mimeToExt(String)} produces for
     *         other exported documents, or {@code null} when the signature is not recognised
     */
    static String extensionFor(byte[] bytes) {
        if (startsWith(bytes, '%', 'P', 'D', 'F')) {
            return ".pdf";
        }
        if (startsWith(bytes, 0x89, 'P', 'N', 'G')) {
            return ".png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return ".jpg";
        }
        if (startsWith(bytes, 'G', 'I', 'F', '8')) {
            return ".gif";
        }
        if (startsWith(bytes, 'I', 'I', 0x2A, 0x00) || startsWith(bytes, 'M', 'M', 0x00, 0x2A)) {
            return ".tif";
        }
        if (startsWith(bytes, '{', '\\', 'r', 't', 'f')) {
            return ".rtf";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String clean(String value) {
        return CdsXmlText.stripInvalidXmlCharacters(value == null ? null : value.trim());
    }
}
