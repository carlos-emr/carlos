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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A synthetic Excelleris (PATHL7, HL7 v2.3) ORU message that mixes discrete results with an
 * embedded PDF, for the OMD CDS export tests of #3946. All identifiers are fictitious.
 *
 * <p>OBR 1 carries a numeric result, a 150-character free-text result made only of
 * base64-alphabet characters (the shape the former {@code Base64.isBase64} check mistook for a
 * document, so it escaped the 120-character limit) and a short result containing a vertical tab
 * (a character XML 1.0 forbids); OBR 2 carries the PDF in an {@code ED} OBX. The same message shape is
 * seeded by {@code scripts/cds-export-lab-documents-playwright-checks.js}.</p>
 *
 * @since 2026-09-26
 */
public final class PathL7EmbeddedDocumentMessage {

    /** Bytes of the embedded document; a minimal PDF. */
    public static final byte[] PDF = ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n"
            + "trailer << /Root 1 0 R >>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);

    /** Result text of 150 base64-alphabet characters. */
    public static final String LONG_TEXT_RESULT = "ABCDEFGHIJ" + "KLMNOPQRST".repeat(14);

    /** Result text containing a vertical tab (U+000B). */
    public static final String CONTROL_TEXT_RESULT = "Hemolysed\u000Bsample";

    public static final String ACCESSION = "PW3946-ACC";

    private PathL7EmbeddedDocumentMessage() {
    }

    /** The message, segments separated by carriage returns as HL7 requires. */
    public static String message() {
        return String.join("\r",
                "MSH|^~\\&|PATHL7|CARLOSTEST|HTTPCLIENT|carlos|20260901101500||ORU^R01|PW3946MSG|P|2.3|||ER|AL",
                "PID||9999999999|3946||PLAYWRIGHT^EXPORTLAB||19700101|F",
                "ORC|RE||" + ACCESSION + "|||||||||TESTLAB^CARLOS^TEST LAB",
                "OBR|1||" + ACCESSION + "|CHEM^Chemistry|RT|20260901100000|20260901100000|||||||20260901100000||"
                        + "TESTLAB^CARLOS^TEST LAB||||||20260901100000||CHEM1|F",
                "OBX|1|NM|GLU^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||20260901100000",
                "OBX|2|FT|NOTE^Interpretation||" + LONG_TEXT_RESULT + "||||||F|||20260901100000",
                "OBX|3|ST|SPEC^Specimen Quality||" + CONTROL_TEXT_RESULT + "||||||F|||20260901100000",
                "OBR|2||" + ACCESSION + "|PDF^Pathology Report|RT|20260901100000|20260901100000|||||||20260901100000||"
                        + "TESTLAB^CARLOS^TEST LAB||||||20260901100000||PATH|F",
                "OBX|1|ED|PDF^Pathology Report||^TEXT^PDF^Base64^" + Base64.getEncoder().encodeToString(PDF)
                        + "||||||F|||20260901100000") + "\r";
    }
}
