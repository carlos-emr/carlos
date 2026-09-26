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
package io.github.carlos_emr.carlos.documentManager.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The request contract shared by the attachment picker ({@code attachDocument.jsp}) and the
 * tickler actions that consume it.
 *
 * <p>The picker submits one multi-valued parameter per type ({@code docNo}, {@code labNo},
 * {@code eFormNo}, {@code hrmNo}, {@code formNo}). Because an edit POST that never opened the
 * picker carries none of them, the tickler forms also send {@link #SUBMITTED_MARKER} whenever
 * the picker's selection is authoritative. Without the marker a sync would read "no ids" as
 * "detach everything", which is what the parallel fork's edit action did on every save.</p>
 *
 * @since 2026-09-26
 */
public final class TicklerAttachmentParameters {

    /** Present (value {@code 1}) when the form's picker selection should replace the stored set. */
    public static final String SUBMITTED_MARKER = "attachmentsSubmitted";

    private static final Map<DocumentType, String> PARAMETER_NAMES;

    static {
        Map<DocumentType, String> names = new LinkedHashMap<>();
        names.put(DocumentType.DOC, "docNo");
        names.put(DocumentType.LAB, "labNo");
        names.put(DocumentType.EFORM, "eFormNo");
        names.put(DocumentType.HRM, "hrmNo");
        names.put(DocumentType.FORM, "formNo");
        PARAMETER_NAMES = Collections.unmodifiableMap(names);
    }

    private TicklerAttachmentParameters() {
        // static contract holder
    }

    /**
     * @param documentType DocumentType the attachment type
     * @return String the picker parameter name for that type
     */
    public static String parameterName(DocumentType documentType) {
        return PARAMETER_NAMES.get(documentType);
    }

    /**
     * @return Map&lt;DocumentType, String&gt; every type the picker can submit, keyed by type,
     *         in the order the tickler windows list them
     */
    public static Map<DocumentType, String> parameterNames() {
        return PARAMETER_NAMES;
    }

    /**
     * @param request HttpServletRequest the form submission
     * @return boolean true when the request carries the picker marker
     */
    public static boolean isSubmitted(HttpServletRequest request) {
        return "1".equals(request.getParameter(SUBMITTED_MARKER));
    }

    /**
     * Reads every picker parameter from the request. Blank values are dropped and duplicates
     * collapsed; a type with no values maps to an empty set, which a sync treats as "detach
     * all of this type". Call only after {@link #isSubmitted} (or when the caller knows the
     * selection is authoritative).
     *
     * @param request HttpServletRequest the form submission
     * @return Map&lt;DocumentType, Set&lt;String&gt;&gt; the submitted ids per type, every type present
     */
    public static Map<DocumentType, Set<String>> read(HttpServletRequest request) {
        Map<DocumentType, Set<String>> submitted = new EnumMap<>(DocumentType.class);
        for (Map.Entry<DocumentType, String> entry : PARAMETER_NAMES.entrySet()) {
            Set<String> ids = new LinkedHashSet<>();
            String[] values = request.getParameterValues(entry.getValue());
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.trim().isEmpty()) {
                        ids.add(value.trim());
                    }
                }
            }
            submitted.put(entry.getKey(), ids);
        }
        return submitted;
    }

    /**
     * Maps a legacy forward-from-document {@code docType} code (a {@code tickler_link.table_name}
     * value: {@code DOC}, {@code HRM} or a lab source such as {@code HL7}) to the attachment type
     * it belongs to.
     *
     * @param legacyDocType String the legacy code, may be null
     * @return DocumentType the attachment type, or {@code null} when the code is blank or unknown
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static DocumentType fromLegacyDocType(String legacyDocType) {
        if (legacyDocType == null || legacyDocType.trim().isEmpty()) {
            return null;
        }
        String code = legacyDocType.trim();
        if (LabResultData.DOCUMENT.equalsIgnoreCase(code)) {
            return DocumentType.DOC;
        }
        if (LabResultData.HRM.equalsIgnoreCase(code)) {
            return DocumentType.HRM;
        }
        if (List.of(LabResultData.HL7TEXT, LabResultData.MDS, LabResultData.CML, LabResultData.EXCELLERIS)
                .stream().anyMatch(code::equalsIgnoreCase)) {
            return DocumentType.LAB;
        }
        return null;
    }
}
