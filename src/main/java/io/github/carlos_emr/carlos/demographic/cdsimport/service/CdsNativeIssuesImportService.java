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

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsNativeIssuesDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Restores a note-free marker in its own receipted unit; all other NewCategory data stays source-only. */
@Service
public class CdsNativeIssuesImportService implements CdsCategoryImportService {
    private final CdsNativeIssuesReader reader;
    private final CdsNativePreparationService preparation;
    private final CaseManagementIssueDAO patientIssues;
    private final IssueDAO issues;

    public CdsNativeIssuesImportService(
            CdsFieldParser fields,
            CdsNativePreparationService preparation,
            @Qualifier("CaseManagementIssueDAO") CaseManagementIssueDAO patientIssues,
            @Qualifier("IssueDAO") IssueDAO issues) {
        reader = new CdsNativeIssuesReader(fields);
        this.preparation = preparation;
        this.patientIssues = patientIssues;
        this.issues = issues;
    }

    @Override
    public Set<String> sections() { return Set.of("NewCategory"); }

    @Override
    public void validate(CdsRecordDto record, CdsImportCommand command,
            CdsSnapshotDto snapshot, String xmlRelative) throws IOException {
        normalized(record, command);
    }

    @Override
    public void prepare(String run, long epoch, String unitKey, CdsRecordDto record,
            CdsImportCommand command, CdsSnapshotDto snapshot, String xmlRelative)
            throws IOException {
        preparation.store(run, epoch, unitKey, record.effectiveSha256(), command,
                normalized(record, command));
    }

    private CdsNativeIssuesDto normalized(CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        var value = reader.read(record);
        var marker = value.marker();
        if (marker == null) return value;
        Issue definition = definition(marker);
        if (definition == null)
            throw refused(record, "CDS_NATIVE_ISSUE_CATALOGUE_REQUIRES_REVIEW");
        Integer program = marker.programId();
        if (program != null && program != 0) {
            if (command.programId() == null)
                throw refused(record, "CDS_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
            program = command.programId();
        }
        try {
            var time = CdsNativeIssuesReader.calendar(marker.updated()).plusDays(command.timeshiftDays());
            if (time.getYear() < 1 || time.getYear() > 9999)
                throw refused(record, "CDS_NATIVE_ISSUE_DATE_REQUIRES_REVIEW");
            return new CdsNativeIssuesDto(marker.target(definition.getId(), program, time.toString()));
        } catch (java.time.DateTimeException invalid) {
            throw refused(record, "CDS_NATIVE_ISSUE_DATE_REQUIRES_REVIEW");
        }
    }

    /** Catalogue keys and access roles belong to the receiver; importing a marker never edits them. */
    private Issue definition(CdsNativeIssuesDto.Marker marker) {
        var matches = issues.findCandidatesByCode(marker.code());
        if (matches.size() != 1) return null;
        var definition = matches.getFirst();
        return definition.getId() != null && definition.getId() > 0
                        && Objects.equals(definition.getCode(), marker.code())
                        && Objects.equals(definition.getType(), marker.catalogue().get("type"))
                        && Objects.equals(definition.getRole(), marker.catalogue().get("role"))
                ? definition : null;
    }

    @Override
    public Map<String, String> write(String run, long epoch, String unitKey, int demographicNo,
            CdsRecordDto record, CdsImportCommand command) throws IOException {
        var prepared = preparation.load(run, epoch, unitKey, record.effectiveSha256(), command,
                CdsNativeIssuesDto.class);
        var references = new LinkedHashMap<String, String>();
        references.put("demographic", Integer.toString(demographicNo));
        var marker = prepared.marker();
        if (marker == null) return references;
        var definition = definition(marker);
        if (definition == null || definition.getId() != marker.issueId())
            throw new CdsImportException("CDS_NATIVE_ISSUE_CATALOGUE_CHANGED");
        // A same-code issue may already file a note or be another distinct marker. Preserve
        // source cardinality and never overwrite that row. The unit checkpoint owns retries.
        var issue = new CaseManagementIssue();
        issue.setDemographic_no(demographicNo);
        issue.setIssue_id(marker.issueId());
        issue.setAcute(marker.acute());
        issue.setCertain(marker.certain());
        issue.setMajor(marker.major());
        issue.setResolved(marker.resolved());
        issue.setType(marker.type());
        issue.setProgram_id(marker.programId());
        issue.setUpdate_date(Timestamp.valueOf(CdsNativeIssuesReader.calendar(marker.updated())));
        patientIssues.insertCdsMarker(issue);
        if (issue.getId() == null || issue.getId() < 1)
            throw new CdsImportException("CDS_NATIVE_ISSUE_ID_MISSING");
        references.put("casemgmt_issue", issue.getId().toString());
        return references;
    }

    private static CdsInputException refused(CdsRecordDto record, String code) {
        return new CdsInputException(code, record.sourceXPath(),
                "The CARLOS system marker requires a compatible catalogue and program mapping.");
    }
}
