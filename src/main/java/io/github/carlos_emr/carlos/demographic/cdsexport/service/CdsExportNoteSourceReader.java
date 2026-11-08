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

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Physical case notes through the owning DAO, with immutable preauthorized patient scope.
 * The complete history category scope is required until an independent native issue classifier
 * qualifies partial category projections. No locked, malformed or unauthorized note is skipped.
 * Raw fields remain private; their clinical/operational/secret dispositions require separate
 * note/issue/revision reconciliation before any clinical artifact can be published.
 */
@Service
public class CdsExportNoteSourceReader extends CdsExportPatientTableSourceReader {
    public static final Set<CdsCategory> CATEGORIES = Set.of(CdsCategory.PERSONAL_HISTORY, CdsCategory.FAMILY_HISTORY,
            CdsCategory.PAST_HEALTH, CdsCategory.PROBLEM_LIST, CdsCategory.RISK_FACTORS,
            CdsCategory.ALERTS_AND_SPECIAL_NEEDS, CdsCategory.CLINICAL_NOTES);
    private static final int MAXIMUM = 8192;
    private final CaseManagementNoteDAO notes;
    private final CdsExportNoteAccessDao scopes;
    private final CdsExportNoteScopeProjectionService projection;
    private final int patient;
    private final List<CdsExportNoteAccessDto> access;
    @Autowired public CdsExportNoteSourceReader(@Qualifier("caseManagementNoteDAO") CaseManagementNoteDAO notes, CdsExportNoteAccessDao scopes, CdsExportNoteScopeProjectionService projection) {
        this(notes, scopes, projection, 0, List.of());
    }
    private CdsExportNoteSourceReader(CaseManagementNoteDAO notes, CdsExportNoteAccessDao scopes, CdsExportNoteScopeProjectionService projection,
            int patient, List<CdsExportNoteAccessDto> access) {
        super(Objects.requireNonNull(notes), CdsExportPatientTableReadSupport.Plan.CASE_NOTES, CATEGORIES);
        this.notes = notes; this.scopes = Objects.requireNonNull(scopes); this.projection = Objects.requireNonNull(projection);
        this.patient = patient; this.access = List.copyOf(access);
    }
    @Override public CdsExportNativeSourceReader forCapture(int patient, CdsExportRunControl control) throws IOException {
        if (this.patient != 0 || patient < 1) throw new IOException("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
        control.checkpoint(); var access = projection.read(patient, MAXIMUM); control.checkpoint();
        if (access == null || access.size() > MAXIMUM) throw new IOException("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
        long previous = 0;
        for (var scope : access) {
            if (scope == null || scope.patient() != patient || scope.noteId() <= previous) throw new IOException("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
            previous = scope.noteId();
        }
        return new CdsExportNoteSourceReader(notes, scopes, projection, patient, access);
    }
    @Override public List<CdsExportNoteAccessDto> noteAccess() { return access; }
    @Override public void authorizeReadScope(int patient, CdsExportRunControl control) {
        // Restored captures authorize their sealed manifest scopes, never today's note universe.
        if (this.patient == 0) return;
        if (this.patient != patient) throw new SecurityException("CDS_EXPORT_NOTE_CAPTURE_SCOPE_INVALID");
        for (var scope : access) { control.checkpoint(); control.authorizeNote(CdsCategory.CLINICAL_NOTES, scope); }
    }
    @Override public long count(int patient) { requireSnapshot(patient); long count = super.count(patient); if (count != access.size()) changed(); return count; }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output) throws IOException {
        requireSnapshot(patient); super.capture(patient, output);
    }
    private void requireSnapshot(int patient) {
        if (this.patient == 0 || this.patient != patient) throw new IllegalStateException("CDS_EXPORT_NOTE_CAPTURE_NOT_PREPARED");
        if (!access.equals(scopes.readScopes(patient, MAXIMUM))) changed();
    }
    private static void changed() { throw new CdsExportReviewException(CdsExportReviewException.Reason.SOURCE_CHANGED); }
}
