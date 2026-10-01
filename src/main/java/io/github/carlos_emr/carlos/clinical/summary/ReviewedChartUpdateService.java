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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.ChartUpdateReceipt;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** An explicit per-proposal approval appends one record; no model can invoke this service. */
@Service
public class ReviewedChartUpdateService {
    private final ChartUpdateContext context;
    private final ChartUpdateReceiptStore receipts;
    private final TicklerManager ticklers;
    private final CaseManagementManager notes;
    private final ProviderDao providers;

    public record Approval(String text, String dueDate, String assignee, String destination, boolean confirmed, String fingerprint) { }
    public record Result(String kind, long target, boolean replay) { }

    public ReviewedChartUpdateService(ChartUpdateContext context, ChartUpdateReceiptStore receipts,
            TicklerManager ticklers, CaseManagementManager notes, ProviderDao providers) {
        this.context = context;
        this.receipts = receipts;
        this.ticklers = ticklers;
        this.notes = notes;
        this.providers = providers;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result apply(LoggedInInfo user, ChartUpdateReview review, String token, String key, Approval approval) {
        ChartUpdateContext.requireEnabled();
        review.authorize(user.getLoggedInProviderNo(), token);
        var proposal = review.proposal(key);
        if (!approval.confirmed()) throw new IllegalArgumentException("Confirm that you reviewed the source and chart before saving.");
        context.requireWrite(user, review.getPatient(), proposal.kind());
        boolean history = "history".equals(proposal.kind());
        boolean legacy = !Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("AbandonOldChart", "false"));
        receipts.requireTransactionalTables(history, legacy);
        receipts.lockPatient(review.getPatient());
        var fresh = context.load(user, review.getDocument());
        if (fresh.patientId() != review.getPatient() || !fresh.sourceHash().equals(review.getSourceHash())) {
            throw new IllegalStateException("The source changed. Reopen the document and generate proposals again.");
        }
        ChartUpdateReceipt existing = receipts.find(review.receiptKey(key));
        if (existing != null) return new Result(existing.getKind(), existing.getTarget(), true);
        if (history) receipts.requireNoteLock(user, fresh.patientId());
        if (!fresh.fingerprint().equals(review.getFingerprint()) || !fresh.fingerprint().equals(approval.fingerprint())) {
            throw new IllegalStateException("The chart changed during review. Reload the proposals and review the current entries before saving.");
        }
        String text = validateText(approval.text());
        if (fresh.entries().stream().anyMatch(entry -> entry.kind().equals(proposal.kind())
                && normalize(entry.entryText()).equals(normalize(text))
                && (history || (Objects.equals(entry.dueDate(), approval.dueDate())
                        && Objects.equals(entry.assignee(), approval.assignee()))))) {
            throw new IllegalStateException("Matching text is already recorded. Review the existing entry instead of adding it again.");
        }
        String recorded = text + "\n\nSource document #" + fresh.documentId() + " (" + fresh.date() + ")"
                + "\nReviewed source passage:\n" + proposal.evidence();
        long target = history ? saveHistory(user, fresh, recorded, approval.destination())
                : saveTickler(user, fresh, recorded, approval.dueDate(), approval.assignee());
        receipts.save(new ChartUpdateReceipt(review.receiptKey(key), fresh.patientId(), fresh.documentId(),
                user.getLoggedInProviderNo(), proposal.kind(), target, fresh.sourceHash()));
        return new Result(proposal.kind(), target, false);
    }

    private long saveTickler(LoggedInInfo user, ChartUpdateContext.Snapshot snapshot, String text, String date, String assignee) {
        if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Choose a due date.");
        LocalDate due;
        try { due = LocalDate.parse(date); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Choose a valid due date."); }
        if (assignee == null || !assignee.matches("[A-Za-z0-9]{1,20}")
                || providers.getActiveProviders().stream().noneMatch(provider -> assignee.equals(provider.getProviderNo()))) {
            throw new IllegalArgumentException("Choose an active assignee.");
        }
        Tickler tickler = new Tickler();
        tickler.setDemographicNo(snapshot.patientId());
        tickler.setProgramId(Integer.valueOf(snapshot.program()));
        tickler.setMessage(text);
        tickler.setCreator(user.getLoggedInProviderNo());
        tickler.setTaskAssignedTo(assignee);
        tickler.setServiceDate(Date.from(due.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        tickler.setStatus(Tickler.STATUS.A);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setCreateDate(new Date());
        tickler.setUpdateDate(new Date());
        if (!ticklers.addTickler(user, tickler) || tickler.getId() == null) throw new IllegalStateException("Tickler was not saved.");
        TicklerLink link = new TicklerLink();
        link.setTableName("DOC");
        link.setTableId((long) snapshot.documentId());
        link.setTicklerNo(tickler.getId());
        if (!ticklers.addTicklerLink(user, link)) throw new IllegalStateException("Source link was not saved.");
        return tickler.getId();
    }

    private long saveHistory(LoggedInInfo user, ChartUpdateContext.Snapshot snapshot, String text, String destination) {
        if (!Set.of("MedHistory", "Concerns").contains(destination == null ? "" : destination)) {
            throw new IllegalArgumentException("Choose Medical history or Ongoing concerns.");
        }
        var issue = notes.getIssueByCode(destination);
        if (issue == null) throw new IllegalStateException("History destination is unavailable.");
        String patient = String.valueOf(snapshot.patientId());
        var patientIssue = notes.getIssueByIssueCode(patient, destination);
        if (patientIssue == null) {
            patientIssue = new CaseManagementIssue();
            patientIssue.setDemographic_no(snapshot.patientId());
            patientIssue.setIssue_id(issue.getId());
            patientIssue.setIssue(issue);
            patientIssue.setProgram_id(Integer.valueOf(snapshot.program()));
            patientIssue.setType(issue.getRole());
            patientIssue.setUpdate_date(new Date());
            notes.saveCaseIssue(patientIssue);
        }
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(patient);
        note.setProviderNo(user.getLoggedInProviderNo());
        note.setProgram_no(snapshot.program());
        note.setReporter_caisi_role(snapshot.role());
        note.setReporter_program_team("0");
        note.setNote(text);
        note.setCreate_date(new Date());
        note.setObservation_date(new Date());
        note.setUpdate_date(new Date());
        note.setRevision("1");
        note.setSigned(true);
        note.setSigning_provider_no(user.getLoggedInProviderNo());
        note.setArchived(false);
        note.setAppointmentNo(0);
        note.setIssues(new HashSet<>(Set.of(patientIssue)));
        CaseManagementCPP cpp = notes.getCPP(patient);
        if (cpp == null) {
            cpp = new CaseManagementCPP();
            cpp.setDemographic_no(patient);
        }
        String saved = notes.saveNote(cpp, note, user.getLoggedInProviderNo(), user.getLoggedInProvider().getFormattedName(),
                null, notes.getRoleName(user.getLoggedInProviderNo(), snapshot.program()));
        if (note.getId() == null || "Note is null".equals(saved)) throw new IllegalStateException("History entry was not saved.");
        CaseManagementNoteLink link = new CaseManagementNoteLink();
        link.setNoteId(note.getId());
        link.setTableName(CaseManagementNoteLink.DOCUMENT);
        link.setTableId((long) snapshot.documentId());
        notes.saveNoteLink(link);
        return note.getId();
    }

    static String validateText(String text) {
        if (text == null || text.isBlank() || text.length() > 2000 || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Enter between 1 and 2000 characters for the chart entry.");
        }
        return text.strip();
    }
    private static String normalize(String text) { return text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").strip(); }
}
