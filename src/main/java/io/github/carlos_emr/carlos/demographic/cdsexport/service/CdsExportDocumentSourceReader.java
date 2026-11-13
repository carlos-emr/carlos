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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Complete patient-associated report metadata. Shared and dangling documents require review
 * before payload capture.
 *
 * <p>A document the chart shows only to providers working in its program is exported when every
 * current exporting identity works in that program, the chart's own rule. The program
 * restrictions are read first, in their own short transaction ({@link #forCapture}); each program
 * is authorized live before any document value is read ({@link #authorizeReadScope}); and the
 * capture proves inside its snapshot that the restrictions are the ones authorized. A program
 * nobody exporting works in holds the patient with
 * {@code CDS_EXPORT_DOCUMENT_PROGRAM_SCOPE}. The report mapper checks each
 * program again, live, before the document is written.
 */
@Service
public class CdsExportDocumentSourceReader extends CdsExportPatientTableSourceReader {
    /** Hold code for a document restricted to a program no exporting identity works in. */
    static final String PROGRAM_HOLD =
            CdsExportReviewException.Reason.DOCUMENT_PROGRAM_SCOPE.code();

    private final DocumentDao documents;
    private final CdsExportDocumentProgramScopeService scopes;
    private final int patient;
    private final SortedMap<Integer, Integer> restrictions;

    @Autowired
    public CdsExportDocumentSourceReader(
            @Qualifier("documentDaoImpl") DocumentDao source,
            CdsExportDocumentProgramScopeService scopes) {
        this(source, scopes, 0, Map.of());
    }

    private CdsExportDocumentSourceReader(
            DocumentDao source,
            CdsExportDocumentProgramScopeService scopes,
            int patient,
            Map<Integer, Integer> restrictions) {
        super(
                Objects.requireNonNull(source),
                CdsExportPatientTableReadSupport.Plan.DOCUMENTS,
                Set.of(CdsCategory.REPORTS));
        this.documents = source;
        this.scopes = Objects.requireNonNull(scopes);
        this.patient = patient;
        this.restrictions =
                java.util.Collections.unmodifiableSortedMap(new TreeMap<>(restrictions));
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("document", "ctl_document", CdsExportDocumentFileSourceReader.TABLE);
    }

    @Override
    public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control)
            throws IOException {
        if (this.patient != 0 || patient < 1)
            throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_SCOPE_INVALID");
        control.checkpoint();
        var read = scopes.read(patient);
        control.checkpoint();
        if (read == null) throw new IOException("CDS_EXPORT_DOCUMENT_CAPTURE_SCOPE_INVALID");
        return new CdsExportDocumentSourceReader(documents, scopes, patient, read);
    }

    /**
     * Live program check for every program a document of this chart is restricted to, outside
     * the source transaction. A restored capture is checked when its documents are mapped.
     */
    @Override
    public void authorizeReadScope(int patient, CdsExportRunControl control) {
        if (this.patient == 0) return;
        if (this.patient != patient)
            throw new SecurityException("CDS_EXPORT_DOCUMENT_CAPTURE_SCOPE_INVALID");
        for (int program : new TreeSet<>(restrictions.values())) {
            control.checkpoint();
            if (!control.documentProgramReadable(patient, Integer.toString(program)))
                throw programHold();
        }
    }

    @Override
    public long count(int patient) {
        requireSnapshot(patient);
        return super.count(patient);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        requireSnapshot(patient);
        super.capture(patient, output);
    }

    /**
     * Inside the source snapshot, the restrictions must be exactly the ones authorized. A reader
     * that was never prepared reads nothing restricted, as before restricted documents could be
     * authorized.
     */
    private void requireSnapshot(int patient) {
        var current = documents.readCdsExportProgramRestrictions(patient);
        if (this.patient == 0) {
            if (!current.isEmpty()) throw programHold();
            return;
        }
        if (this.patient != patient)
            throw new IllegalStateException("CDS_EXPORT_DOCUMENT_CAPTURE_NOT_PREPARED");
        if (!restrictions.equals(current))
            throw new CdsExportReviewException(CdsExportReviewException.Reason.SOURCE_CHANGED);
    }

    private static CdsExportReviewException programHold() {
        return new CdsExportReviewException(CdsExportReviewException.Reason.DOCUMENT_PROGRAM_SCOPE);
    }
}
