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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDiagnosisCodeReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Public code-table descriptions for the codes a patient's Disease Registry entries and episodes
 * name, read inside the export's read-only patient snapshot. The code tables ({@code icd9},
 * {@code icd10}, {@code ichppccode}, {@code diagnosticcode}) keep their REFERENCE ruling in the
 * source catalogue: only rows matching one of this patient's codes are read, and only through
 * the fixed {@link Reference} allowlist, the same four systems the legacy exporter's
 * {@code ALLOWED_CODE_SYSTEM_DAOS} admitted. A stored coding-system value never becomes an SQL
 * identifier; an unlisted system (for example {@code SnomedCore}, whose licensed table is outside
 * the physical contract) is simply not looked up.
 *
 * <p>The lookup compares the stored code with the code column under that column's own collation,
 * exactly as the chart's registry list joins them ({@code DxresearchDAOImpl}), so the
 * description captured is the one the chart shows. Every matching row is returned: a code table
 * that holds a code more than once is reported, never resolved to an arbitrary first row.
 *
 * @since 2026-11-13
 */
public final class CdsExportDiagnosisCodeReadSupport {
    /** Patient rows (registry entries plus episodes) one capture may name. */
    public static final int MAXIMUM_ROWS = 8192;

    /** Code-table rows one code may match before the capture refuses the chart's size. */
    static final int MAXIMUM_MATCHES = 64;

    /** The only code tables a stored coding system can select; identifiers are compile-time constants. */
    public enum Reference {
        ICD9("icd9", "icd9", "icd9", "id"),
        ICD10("icd10", "icd10", "icd10", "id"),
        ICHPPC("ichppccode", "ichppccode", "ichppccode", "ichppccode"),
        MSP("msp", "diagnosticcode", "diagnostic_code", "diagnosticcode_no");

        private final String system, table, code, key;

        Reference(String system, String table, String code, String key) {
            this.system = system;
            this.table = table;
            this.code = code;
            this.key = key;
        }

        public String table() {
            return table;
        }

        /**
         * The allowlisted code table for a stored coding system, or {@code null}. The registry
         * list compares the system under a case-insensitive collation, so ASCII case is ignored;
         * nothing else is normalized.
         */
        public static Reference of(String codingSystem) {
            if (codingSystem == null) return null;
            String folded = asciiLower(codingSystem);
            for (var reference : values()) if (reference.system.equals(folded)) return reference;
            return null;
        }

        private static String asciiLower(String value) {
            char[] characters = value.toCharArray();
            for (int i = 0; i < characters.length; i++)
                if (characters[i] >= 'A' && characters[i] <= 'Z')
                    characters[i] = (char) (characters[i] + ('a' - 'A'));
            return new String(characters);
        }
    }

    private record Pair(String system, String code) implements Comparable<Pair> {
        @Override
        public int compareTo(Pair other) {
            int result = system.compareTo(other.system);
            return result != 0 ? result : code.compareTo(other.code);
        }
    }

    private CdsExportDiagnosisCodeReadSupport() {}

    /**
     * Every allowlisted code-table row matching a code of this patient's current registry entries
     * and episodes. Deleted entries (registry status {@code D}, episode status {@code Deleted})
     * are not exported, so their codes are not looked up.
     */
    // Table and column identifiers come only from the Reference enum; patient and code are bound.
    @SuppressWarnings({"java:S2077", "unchecked"})
    public static List<CdsExportDiagnosisCodeReferenceDto> read(EntityManager em, int patient) {
        if (patient < 1) throw new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID");
        List<Object[]> rows =
                CdsExportPatientTableReadSupport.query(
                                em,
                                "select CAST(x.`coding_system` AS BINARY),CAST(x.`dxresearch_code`"
                                        + " AS BINARY) from `dxresearch` x where"
                                        + " x.`demographic_no`=:patient and (x.`status` is null or"
                                        + " CAST(x.`status` AS BINARY)<>'D') union all select"
                                        + " CAST(e.`codingSystem` AS BINARY),CAST(e.`code` AS"
                                        + " BINARY) from `Episode` e where e.`demographicNo`=:patient"
                                        + " and (e.`status` is null or CAST(e.`status` AS"
                                        + " BINARY)<>'Deleted')")
                        .setParameter("patient", patient)
                        .setMaxResults(MAXIMUM_ROWS + 1)
                        .getResultList();
        if (rows.size() > MAXIMUM_ROWS)
            throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
        var pairs = new TreeSet<Pair>();
        for (var row : rows) {
            if (row.length != 2) invalid();
            String system = text(row[0]), code = text(row[1]);
            if (system == null || code == null || system.isEmpty() || code.isEmpty()) continue;
            if (Reference.of(system) != null) pairs.add(new Pair(system, code));
        }
        var result = new ArrayList<CdsExportDiagnosisCodeReferenceDto>();
        for (var pair : pairs) {
            var reference = Reference.of(pair.system());
            List<Object[]> matches =
                    CdsExportPatientTableReadSupport.query(
                                    em,
                                    "select CAST(c.`"
                                            + reference.key
                                            + "` AS BINARY),CAST(c.`"
                                            + reference.code
                                            + "` AS BINARY),CAST(c.`description` AS BINARY) from `"
                                            + reference.table
                                            + "` c where c.`"
                                            + reference.code
                                            + "`=:code order by c.`"
                                            + reference.key
                                            + "`")
                            .setParameter("code", pair.code())
                            .setMaxResults(MAXIMUM_MATCHES + 1)
                            .getResultList();
            if (matches.size() > MAXIMUM_MATCHES)
                throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
            for (var match : matches) {
                if (match.length != 3) invalid();
                String id = text(match[0]), code = text(match[1]);
                if (id == null || code == null) invalid();
                result.add(
                        new CdsExportDiagnosisCodeReferenceDto(
                                pair.system(),
                                pair.code(),
                                reference.table,
                                id,
                                code,
                                text(match[2])));
            }
        }
        return List.copyOf(result);
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (!(value instanceof byte[] bytes)) {
            invalid();
            return null;
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException malformed) {
            throw new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID");
        }
    }

    private static void invalid() {
        throw new IllegalStateException("CDS_EXPORT_NATIVE_SOURCE_INVALID");
    }
}
