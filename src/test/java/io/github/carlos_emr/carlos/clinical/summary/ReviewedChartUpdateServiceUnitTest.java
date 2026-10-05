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
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewedChartUpdateServiceUnitTest {
    private final ChartUpdateContext context = mock(ChartUpdateContext.class);
    private final ChartUpdateReceiptStore receipts = mock(ChartUpdateReceiptStore.class);
    private final TicklerManager ticklers = mock(TicklerManager.class);
    private final CaseManagementManager notes = mock(CaseManagementManager.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final ReviewedChartUpdateService service = new ReviewedChartUpdateService(context, receipts, ticklers, notes, providers);
    private MockedStatic<CarlosProperties> settings;
    private CarlosProperties properties;
    private ChartUpdateReview review;
    private ChartUpdateProposals.Proposal proposal;
    private ChartUpdateContext.Snapshot snapshot;

    @BeforeEach void setUp() {
        properties = mock(CarlosProperties.class);
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        // Stub each flag by name so a renamed or added gate flag fails closed instead of being masked.
        for (String flag : List.of(ChartUpdateProposals.ENABLED, DocumentSummaryService.ENABLED_PROPERTY,
                ClinicalSummaryGenerationService.ENABLED_PROPERTY)) {
            when(properties.getProperty(flag, "false")).thenReturn("true");
        }
        when(properties.getProperty("AbandonOldChart", "false")).thenReturn("false");
        when(user.getLoggedInProviderNo()).thenReturn("101");
        var provider = new Provider();
        provider.setProviderNo("101");
        when(user.getLoggedInProvider()).thenReturn(provider);
        when(providers.getActiveProviders()).thenReturn(List.of(provider));
        snapshot = snapshot("fresh", "source", List.of());
        when(context.load(user, 42)).thenReturn(snapshot);
        prepare("tickler");
        when(ticklers.addTickler(eq(user), any())).thenAnswer(call -> {
            Tickler tickler = call.getArgument(1);
            tickler.setId(123);
            return true;
        });
        when(ticklers.addTicklerLink(eq(user), any())).thenReturn(true);
    }
    @AfterEach void tearDown() { settings.close(); }

    private ChartUpdateContext.Snapshot snapshot(String fingerprint, String hash, List<ChartUpdateContext.Entry> entries) {
        return new ChartUpdateContext.Snapshot(42, 3001, "Synthetic patient", "Synthetic", "2026-09-28", "Review in two weeks.",
                hash, fingerprint, "10016", "1", entries);
    }
    private void prepare(String kind) {
        proposal = new ChartUpdateProposals.Proposal(kind, "Review in two weeks.");
        review = new ChartUpdateReview("101", snapshot, List.of(proposal));
    }
    private ReviewedChartUpdateService.Approval approval(String text, String date, String assignee, String destination) {
        return new ReviewedChartUpdateService.Approval(text, date, assignee, destination, true, "fresh");
    }
    private ReviewedChartUpdateService.Result apply(ReviewedChartUpdateService.Approval approval) {
        return service.apply(user, review, review.getToken(), proposal.key(), approval);
    }
    private ReviewedChartUpdateService.Approval valid() { return approval("Review symptoms", "2026-10-12", "101", ""); }

    @Test void shouldSaveClinicianText_withSourceAndReceipt() {
        assertThat(apply(valid())).isEqualTo(new ReviewedChartUpdateService.Result("tickler", 123, false));
        verify(ticklers).addTickler(eq(user), argThat(tickler -> tickler.getDemographicNo() == 3001
                && tickler.getTaskAssignedTo().equals("101") && tickler.getStatus() == Tickler.STATUS.A
                && tickler.getMessage().contains("Review symptoms\n\nSource document #42")
                && tickler.getMessage().endsWith(proposal.evidence())));
        verify(ticklers).addTicklerLink(eq(user), argThat(link -> link.getTableName().equals("DOC") && link.getTableId() == 42L));
        var order = inOrder(context, receipts, ticklers);
        order.verify(context).requireWrite(user, 3001, "tickler");
        order.verify(receipts).requireTransactionalTables(false, true);
        order.verify(receipts).lockPatient(3001);
        order.verify(context).load(user, 42);
        order.verify(ticklers).addTickler(eq(user), any());
        order.verify(ticklers).addTicklerLink(eq(user), any());
        order.verify(receipts).save(any());
        verifyNoInteractions(notes);
    }

    @Test void shouldRefuseNativeReviewItems_evenWithConfirmation() {
        proposal = new ChartUpdateProposals.Proposal("review", "Allergies: none.", "Allergies");
        review = new ChartUpdateReview("101", snapshot, List.of(proposal));
        assertThatThrownBy(() -> apply(approval("Allergies: none.", "", "", "MedHistory")))
                .hasMessageContaining("normal chart form");
        verifyNoInteractions(context, receipts, ticklers, notes);
    }

    @Test void shouldRequireConfirmation_beforePersistence() {
        assertThatThrownBy(() -> apply(new ReviewedChartUpdateService.Approval("x", "", "", "", false, "fresh")))
                .hasMessageContaining("Confirm");
        verifyNoInteractions(receipts, ticklers, notes);
    }

    @Test void shouldRejectApproval_withWrongActorTokenOrProposal() {
        assertThatThrownBy(() -> service.apply(user, review, "forged", proposal.key(), valid())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.apply(user, review, review.getToken(), "forged", valid())).isInstanceOf(IllegalArgumentException.class);
        when(user.getLoggedInProviderNo()).thenReturn("102");
        assertThatThrownBy(() -> apply(valid())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(receipts, ticklers, notes);
    }

    @ParameterizedTest
    @ValueSource(strings = {ChartUpdateProposals.ENABLED, DocumentSummaryService.ENABLED_PROPERTY,
            ClinicalSummaryGenerationService.ENABLED_PROPERTY})
    void shouldRejectApproval_whenAnyRequiredFlagDisabled(String flag) {
        when(properties.getProperty(flag, "false")).thenReturn("false");
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("disabled");
        verifyNoInteractions(receipts, ticklers, notes);
    }

    @Test void shouldRejectApproval_whenWritePermissionRevoked() {
        doThrow(new SecurityException()).when(context).requireWrite(user, 3001, "tickler");
        assertThatThrownBy(() -> apply(valid())).isInstanceOf(SecurityException.class);
        verifyNoInteractions(receipts, ticklers, notes);
    }

    @Test void shouldRejectApproval_whenSourceChartOrTabStale() {
        when(context.load(user, 42)).thenReturn(snapshot("fresh", "changed-source", List.of()));
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("source changed");
        when(context.load(user, 42)).thenReturn(snapshot("changed-chart", "source", List.of()));
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("chart changed");
        review.refresh("changed-chart");
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("chart changed");
        verifyNoInteractions(ticklers, notes);
        verify(receipts, never()).save(any());
    }

    @Test void shouldRejectApproval_withMissingOrInvalidFields() {
        for (var invalid : List.of(approval("", "2026-10-12", "101", ""), approval("x", "", "101", ""),
                approval("x", "2026-02-30", "101", ""), approval("x", "2026-10-12", "999", ""))) {
            assertThatThrownBy(() -> apply(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(ticklers, notes);
        verify(receipts, never()).save(any());
    }

    @Test void shouldRejectApproval_whenTextAlreadyExists() {
        when(context.load(user, 42)).thenReturn(snapshot("fresh", "source",
                List.of(new ChartUpdateContext.Entry("tickler-1", "tickler", "REVIEW   SYMPTOMS\nDue: 2026-10-12", "REVIEW   SYMPTOMS", "2026-10-12", "101"))));
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("already recorded");
        verifyNoInteractions(ticklers, notes);
    }

    @Test void shouldAllowDistinctStatements_despiteSharedWordsOrProvenance() {
        for (String[] pair : List.of(new String[]{"No hypertension", "Hypertension"},
                new String[]{"Family history: hypertension", "Hypertension"},
                new String[]{"Right knee osteoarthritis", "Knee osteoarthritis"},
                new String[]{"No action needed\n\nSource document #42 (2026-09-28)\nReviewed source passage:\nHypertension", "Hypertension"})) {
            when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(
                    new ChartUpdateContext.Entry("tickler-1", "tickler", pair[0], pair[0], "2026-10-12", "101"))));
            assertThat(apply(approval(pair[1], "2026-10-12", "101", "")).replay()).isFalse();
        }
    }

    @Test void shouldAllowSameReminderText_withDifferentDueDateOrAssignee() {
        for (String[] metadata : List.of(new String[]{"2026-10-13", "101"}, new String[]{"2026-10-12", "102"})) {
            when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(
                    new ChartUpdateContext.Entry("tickler-1", "tickler", "Review symptoms", "Review symptoms", metadata[0], metadata[1]))));
            assertThat(apply(valid()).replay()).isFalse();
        }
    }

    @Test void shouldBlockWorkflowDuplicates_onlyWithAuthenticatedSourceAnnotation() {
        String annotated = "Review symptoms\n\nSource document #41 (2026-09-27)\nReviewed source passage:\nOriginal evidence.";
        when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(
                new ChartUpdateContext.Entry("tickler-8", "tickler", annotated, annotated, "2026-10-12", "101"))));
        // Identical-looking prose alone must not authenticate a source footer.
        assertThat(apply(valid()).replay()).isFalse();
        when(receipts.hasProvenance(3001, "tickler", 8L, 41, "Original evidence.")).thenReturn(true);
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("already recorded");
        prepare("history");
        when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(
                new ChartUpdateContext.Entry("note-9", "history", annotated, annotated, "", "", java.util.Set.of("MedHistory")))));
        when(receipts.hasProvenance(3001, "history", 9L, 41, "Original evidence.")).thenReturn(true);
        assertThatThrownBy(() -> apply(approval("Review symptoms", "", "", "MedHistory")))
                .hasMessageContaining("already recorded");
    }

    @Test void shouldAllowDistinctReminderMetadata_evenWithAuthenticatedAnnotation() {
        String annotated = "Review symptoms\n\nSource document #41 ()\nReviewed source passage:\nOriginal evidence.";
        when(receipts.hasProvenance(3001, "tickler", 8L, 41, "Original evidence.")).thenReturn(true);
        for (String[] metadata : List.of(new String[]{"2026-10-13", "101"}, new String[]{"2026-10-12", "102"})) {
            when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(
                    new ChartUpdateContext.Entry("tickler-8", "tickler", annotated, annotated, metadata[0], metadata[1]))));
            assertThat(apply(valid()).replay()).isFalse();
        }
    }

    @Test void shouldReplayReceipt_withoutAnotherWrite() {
        when(receipts.find(review.receiptKey(proposal.key()))).thenReturn(new ChartUpdateReceipt(
                review.receiptKey(proposal.key()), 3001, 42, "101", "tickler", 123, "source"));
        when(context.load(user, 42)).thenReturn(snapshot("changed-chart", "source", List.of()));
        assertThat(apply(valid()).replay()).isTrue();
        verifyNoInteractions(ticklers, notes);
        verify(receipts, never()).save(any());
    }

    @Test void shouldAvoidReceipt_whenNativeWriteOrLinkFails() {
        when(ticklers.addTicklerLink(eq(user), any())).thenReturn(false);
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("Source link");
        verify(receipts, never()).save(any());
        when(ticklers.addTickler(eq(user), any())).thenReturn(false);
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("Tickler was not saved");
        verify(receipts, never()).save(any());
    }

    @Test void shouldRequireLockAndDestination_whenSavingHistory() {
        prepare("history");
        assertThatThrownBy(() -> apply(valid())).hasMessageContaining("Choose an available chart section");
        verify(receipts).requireNoteLock(user, 3001);
        doThrow(new IllegalStateException("editing lock")).when(receipts).requireNoteLock(user, 3001);
        assertThatThrownBy(() -> apply(approval("History", "", "", "MedHistory"))).hasMessageContaining("editing lock");
        verifyNoInteractions(ticklers, notes);
    }

    @Test void shouldCompareIdenticalText_onlyWithinTheChosenSection() {
        prepare("history");
        var entry = new ChartUpdateContext.Entry("note-9", "history", "Hypertension", "Hypertension", "", "", java.util.Set.of("FamHistory"));
        when(context.load(user, 42)).thenReturn(snapshot("fresh", "source", List.of(entry)));
        assertThatThrownBy(() -> apply(approval("Hypertension", "", "", "FamHistory"))).hasMessageContaining("already recorded");
        var issue = new Issue();
        issue.setId(7L); issue.setRole("doctor");
        when(notes.getIssueByCode("MedHistory")).thenReturn(issue);
        when(notes.saveNote(any(), any(), any(), any(), isNull(), any())).thenAnswer(call -> {
            ((CaseManagementNote) call.getArgument(1)).setId(456L);
            return "";
        });
        assertThat(apply(approval("Hypertension", "", "", "MedHistory")).target()).isEqualTo(456);
        verify(notes).saveNote(any(), argThat(note -> note.getIssues().iterator().next().getIssue_id() == 7L), any(), any(), isNull(), any());
    }

    @Test void shouldRejectSectionWrite_whenPermissionIsRevoked() {
        prepare("history");
        doThrow(new SecurityException()).when(context).requireSectionWrite(user, 3001, "SocHistory");
        assertThatThrownBy(() -> apply(approval("Lives alone", "", "", "SocHistory"))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(receipts, ticklers, notes);
    }

    @Test void shouldAppendSignedHistory_withOriginalEvidence() {
        prepare("history");
        var issue = new Issue();
        issue.setId(7L);
        issue.setRole("doctor");
        when(notes.getIssueByCode("MedHistory")).thenReturn(issue);
        when(notes.saveNote(any(), any(), any(), any(), isNull(), any())).thenAnswer(call -> {
            CaseManagementNote note = call.getArgument(1);
            assertThat(note.isSigned()).isTrue();
            assertThat(note.getSigning_provider_no()).isEqualTo("101");
            assertThat(note.getDemographic_no()).isEqualTo("3001");
            assertThat(note.getNote()).contains(proposal.evidence());
            note.setId(456L);
            return "";
        });
        when(notes.getRoleName("101", "10016")).thenReturn("doctor");
        assertThat(apply(approval("Clinician verified history", "", "", "MedHistory")).target()).isEqualTo(456);
        verify(notes).saveNoteLink(argThat(link -> link.getNoteId() == 456L && link.getTableId() == 42L));
        verify(receipts).requireTransactionalTables(true, true);
        verify(receipts).save(any());
        verifyNoInteractions(ticklers);
    }

    @Test void shouldSkipLegacyChartTable_whenOldChartAbandoned() {
        when(properties.getProperty("AbandonOldChart", "false")).thenReturn("true");
        prepare("history");
        var issue = new Issue();
        issue.setId(7L);
        when(notes.getIssueByCode("MedHistory")).thenReturn(issue);
        when(notes.saveNote(any(), any(), any(), any(), isNull(), any())).thenAnswer(call -> {
            ((CaseManagementNote) call.getArgument(1)).setId(456L);
            return "";
        });
        assertThat(apply(approval("Clinician verified history", "", "", "MedHistory")).target()).isEqualTo(456);
        verify(receipts).requireTransactionalTables(true, false);
    }
}
