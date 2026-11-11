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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each Messenger compose window keeps its attachments under its own key, so one window's
 * attachments never go out with a message written in another (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("messenger")
@DisplayName("Messenger per-window attachment staging")
class MsgAttachmentStagingUnitTest {

    private final MockHttpSession session = new MockHttpSession();

    @Test
    void shouldIssueDistinctWellFormedKeys_forEachComposeWindow() {
        String first = MsgAttachmentStaging.newKey();
        String second = MsgAttachmentStaging.newKey();

        assertThat(MsgAttachmentStaging.isKey(first)).isTrue();
        assertThat(MsgAttachmentStaging.isKey(second)).isTrue();
        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "AAAAAAAAAAAAAAAAAAAAA!", "AAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAA "})
    void shouldRejectValue_whenNotAComposeKey(String value) {
        assertThat(MsgAttachmentStaging.isKey(value)).isFalse();
        assertThat(MsgAttachmentStaging.peek(session, value)).isNull();
        assertThat(MsgAttachmentStaging.take(session, value)).isNull();
        assertThatThrownBy(() -> MsgAttachmentStaging.stagePdfs(session, value, 101, "<PDF/>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepWindowsApart_whenTwoWindowsStageForDifferentPatients() {
        String windowA = MsgAttachmentStaging.newKey();
        String windowB = MsgAttachmentStaging.newKey();

        MsgAttachmentStaging.stagePdfs(session, windowA, 101, "<PDF>A</PDF>");
        MsgAttachmentStaging.stagePdfs(session, windowB, 202, "<PDF>B</PDF>");

        assertThat(MsgAttachmentStaging.take(session, windowA))
                .extracting(MsgAttachmentStaging.Staged::pdfDemographicNo, MsgAttachmentStaging.Staged::pdfAttachment)
                .containsExactly(101, "<PDF>A</PDF>");
        assertThat(MsgAttachmentStaging.peek(session, windowB).pdfAttachment()).isEqualTo("<PDF>B</PDF>");
    }

    @Test
    void shouldHandOutAttachmentsOnce_whenTaken() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A</PDF>");

        MsgAttachmentStaging.Staged taken = MsgAttachmentStaging.take(session, window);
        assertThat(taken).isNotNull();
        assertThat(MsgAttachmentStaging.take(session, window)).isNull();
        assertThat(Collections.list(session.getAttributeNames())).hasSize(1);
        MsgAttachmentStaging.releaseSource(session, window, taken);
        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }

    @Test
    void shouldKeepTheOtherKind_whenReplacingPdfsOrItems() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stageItems(session, window, "<root/>");
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A</PDF>");
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A2</PDF>");

        MsgAttachmentStaging.Staged staged = MsgAttachmentStaging.peek(session, window);
        assertThat(staged.itemAttachment()).isEqualTo("<root/>");
        assertThat(staged.pdfAttachment()).isEqualTo("<PDF>A2</PDF>");

        MsgAttachmentStaging.stageItems(session, window, "<root2/>");
        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isEqualTo("<PDF>A2</PDF>");
    }

    @Test
    void shouldForgetTheWindow_whenItsPdfsAreClearedAndNothingElseIsAttached() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A</PDF>");

        MsgAttachmentStaging.stagePdfs(session, window, 101, null);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }

    @Test
    void shouldRecordNoPatient_whenNoPdfsAreStaged() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stageItems(session, window, "<root/>");
        MsgAttachmentStaging.stagePdfs(session, window, 101, null);

        assertThat(MsgAttachmentStaging.peek(session, window).pdfDemographicNo()).isZero();
    }

    @Test
    void shouldPutBackOnlyIfNothingNewerWasStaged_whenRestoring() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A</PDF>");
        MsgAttachmentStaging.Staged taken = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.restore(session, window, taken);
        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isEqualTo("<PDF>A</PDF>");

        MsgAttachmentStaging.Staged again = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>newer</PDF>");
        MsgAttachmentStaging.restore(session, window, again);
        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isEqualTo("<PDF>newer</PDF>");
    }

    @Test
    void shouldKeepPdfsCleared_whenAnEarlierSendFails() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);

        // The chooser submits no selected records while sendMessage2 is still in flight.
        MsgAttachmentStaging.stagePdfs(session, window, 101, null);
        MsgAttachmentStaging.restore(session, window, sending);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
    }

    @Test
    void shouldKeepItemsCleared_whenAnEarlierSendFails() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stageItems(session, window, "<root>FAKE item</root>");
        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.stageItems(session, window, null);
        MsgAttachmentStaging.restore(session, window, sending);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
    }

    @Test
    void shouldKeepDiscardedAttachmentsAbsent_whenAnEarlierSendFails() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.stageItems(session, window, "<root>FAKE item</root>");
        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.discard(session, window);
        MsgAttachmentStaging.restore(session, window, sending);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
    }

    @Test
    void shouldRestoreOnlyTheNewerSend_whenTwoAttemptsFinishOutOfOrder() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged first = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.stagePdfs(session, window, 202, "<PDF>FAKE B</PDF>");
        MsgAttachmentStaging.Staged second = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.restore(session, window, first);
        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        MsgAttachmentStaging.restore(session, window, second);
        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isEqualTo("<PDF>FAKE B</PDF>");
        assertThat(MsgAttachmentStaging.peek(session, window).pdfDemographicNo()).isEqualTo(202);
    }

    @Test
    void shouldKeepTheReplacement_whenAnOlderSendFailsAfterClearAndRestage() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.stagePdfs(session, window, 101, null);
        MsgAttachmentStaging.stageItems(session, window, "<root>FAKE replacement</root>");

        MsgAttachmentStaging.restore(session, window, sending);

        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isNull();
        assertThat(MsgAttachmentStaging.peek(session, window).itemAttachment())
                .isEqualTo("<root>FAKE replacement</root>");
    }

    @Test
    void shouldConsumeRestorationOwnership_whenTheSendSucceededWithoutASource() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged sent = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.releaseSource(session, window, sent);
        MsgAttachmentStaging.restore(session, window, sent);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }

    @Test
    void shouldKeepTheNewerClaim_whenAnOlderSendSucceeds() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged first = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.stagePdfs(session, window, 202, "<PDF>FAKE B</PDF>");
        MsgAttachmentStaging.Staged second = MsgAttachmentStaging.take(session, window);

        MsgAttachmentStaging.releaseSource(session, window, first);
        MsgAttachmentStaging.restore(session, window, second);

        assertThat(MsgAttachmentStaging.peek(session, window).pdfAttachment()).isEqualTo("<PDF>FAKE B</PDF>");
        assertThat(MsgAttachmentStaging.peek(session, window).pdfDemographicNo()).isEqualTo(202);
    }

    @Test
    void shouldGiveRetriesNewOwnership_whenTheSameValuesAreTakenAgain() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged first = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.restore(session, window, first);
        MsgAttachmentStaging.Staged retry = MsgAttachmentStaging.take(session, window);
        assertThat(retry).isEqualTo(first).isNotSameAs(first);
        assertThat(retry.hashCode()).isEqualTo(first.hashCode());

        MsgAttachmentStaging.restore(session, window, first);
        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        MsgAttachmentStaging.releaseSource(session, window, first);
        MsgAttachmentStaging.restore(session, window, retry);
        assertThat(MsgAttachmentStaging.peek(session, window)).isSameAs(retry);
    }

    @Test
    void shouldNotResurrectAClaim_whenClearIsFollowedByManyOtherSends() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged first = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.discard(session, window);
        for (int i = 0; i < 5 * MsgAttachmentStaging.MAX_ENTRIES; i++) {
            String other = MsgAttachmentStaging.newKey();
            MsgAttachmentStaging.stageItems(session, other, "<root>FAKE other</root>");
            MsgAttachmentStaging.take(session, other);
        }

        MsgAttachmentStaging.restore(session, window, first);

        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, window)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBoundOpaqueClaimsAndReportReleasedAttachments_whenTooManySendsOverlap() {
        List<String> windows = new ArrayList<>();
        List<MsgAttachmentStaging.Staged> sending = new ArrayList<>();
        for (int i = 0; i <= 4 * MsgAttachmentStaging.MAX_ENTRIES; i++) {
            String window = MsgAttachmentStaging.newKey();
            windows.add(window);
            MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE patient chart</PDF>");
            sending.add(MsgAttachmentStaging.take(session, window));
        }
        Map<String, String> claims = (Map<String, String>) session.getAttribute(
                MsgAttachmentStaging.class.getName() + ".restores");
        assertThat(claims).hasSize(4 * MsgAttachmentStaging.MAX_ENTRIES);
        assertThat(claims.values()).allMatch(MsgAttachmentStaging::isKey);
        assertThat(claims.toString()).doesNotContain("<PDF>", "<root>", "FAKE patient chart");
        assertThatThrownBy(claims::clear).isInstanceOf(UnsupportedOperationException.class);

        MsgAttachmentStaging.restore(session, windows.get(0), sending.get(0));
        assertThat(MsgAttachmentStaging.peek(session, windows.get(0))).isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, windows.get(0))).isTrue();
        assertThat(MsgAttachmentStaging.claimDropped(session, windows.get(0))).isFalse();
        MsgAttachmentStaging.restore(session, windows.get(1), sending.get(1));
        assertThat(MsgAttachmentStaging.peek(session, windows.get(1))).isEqualTo(sending.get(1));
    }

    @Test
    void shouldRestoreTheExactAttempt_whenSessionMetadataIsSerialized() throws Exception {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>FAKE A</PDF>");
        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);
        Map<String, Object> attributes = new LinkedHashMap<>();
        Collections.list(session.getAttributeNames()).forEach(name -> attributes.put(name, session.getAttribute(name)));
        MockHttpSession persisted = new MockHttpSession();
        serializedCopy(attributes).forEach(persisted::setAttribute);
        MsgAttachmentStaging.Staged persistedSending = serializedCopy(sending);

        MsgAttachmentStaging.restore(persisted, window, persistedSending);

        assertThat(persistedSending).isEqualTo(sending).isNotSameAs(sending);
        assertThat(MsgAttachmentStaging.peek(persisted, window)).isEqualTo(sending);
        assertThat(Collections.list(persisted.getAttributeNames())).hasSize(1);
    }

    @Test
    void shouldReadPreviousSessionEntries_whenTheirSerializedRecordPredatesRevisions() throws Exception {
        // Written by the original 3-component Staged record (serialVersionUID 0) before this fix.
        String legacy = "rO0ABXNyAEppby5naXRodWIuY2FybG9zX2Vtci5jYXJsb3MubWVzc2VuZ2VyLnBhZ2VVdGlsLk1zZ0F0dGFjaG1lbnRTdGFnaW5nJFN0YWdlZAAAAAAAAAAAAgADSQAQcGRmRGVtb2dyYXBoaWNOb0wADml0ZW1BdHRhY2htZW50dAASTGphdmEvbGFuZy9TdHJpbmc7TAANcGRmQXR0YWNobWVudHEAfgABeHAAAABldAAWPHJvb3Q+RkFLRSBpdGVtPC9yb290PnQAETxQREY+RkFLRSBBPC9QREY+";
        MsgAttachmentStaging.Staged saved;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(legacy)))) {
            saved = (MsgAttachmentStaging.Staged) input.readObject();
        }
        String window = MsgAttachmentStaging.newKey();
        session.setAttribute(MsgAttachmentStaging.class.getName() + ".entries", Map.of(window, saved));

        MsgAttachmentStaging.Staged sending = MsgAttachmentStaging.take(session, window);
        MsgAttachmentStaging.restore(session, window, serializedCopy(sending));

        assertThat(MsgAttachmentStaging.peek(session, window))
                .isEqualTo(new MsgAttachmentStaging.Staged(101, "<PDF>FAKE A</PDF>", "<root>FAKE item</root>"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T serializedCopy(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    @Test
    void shouldDropTheOldestWindow_whenMoreWindowsStageThanTheCap() {
        List<String> windows = new ArrayList<>();
        for (int i = 0; i <= MsgAttachmentStaging.MAX_ENTRIES; i++) {
            String window = MsgAttachmentStaging.newKey();
            windows.add(window);
            MsgAttachmentStaging.stagePdfs(session, window, 100 + i, "<PDF>" + i + "</PDF>");
        }

        assertThat(MsgAttachmentStaging.peek(session, windows.get(0))).isNull();
        for (int i = 1; i < windows.size(); i++) {
            assertThat(MsgAttachmentStaging.peek(session, windows.get(i))).isNotNull();
        }
    }

    @Test
    void shouldTellTheWindowOnce_whenTheCapDroppedItsAttachments() {
        String dropped = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, dropped, 101, "<PDF>A</PDF>");
        String kept = null;
        for (int i = 0; i < MsgAttachmentStaging.MAX_ENTRIES; i++) {
            kept = MsgAttachmentStaging.newKey();
            MsgAttachmentStaging.stageItems(session, kept, "<root/>");
        }

        assertThat(MsgAttachmentStaging.take(session, dropped)).isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, dropped)).isTrue();
        // Told once: the next send from that window goes out without them.
        assertThat(MsgAttachmentStaging.claimDropped(session, dropped)).isFalse();
        assertThat(MsgAttachmentStaging.claimDropped(session, kept)).isFalse();
        assertThat(MsgAttachmentStaging.claimDropped(session, MsgAttachmentStaging.newKey())).isFalse();
        assertThat(MsgAttachmentStaging.claimDropped(session, "malformed")).isFalse();
    }

    @Test
    void shouldForgetTheDrop_whenTheWindowStagesAgainOrIsCleared() {
        String restaged = MsgAttachmentStaging.newKey();
        String cleared = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, restaged, 101, "<PDF>A</PDF>");
        MsgAttachmentStaging.stagePdfs(session, cleared, 102, "<PDF>B</PDF>");
        for (int i = 0; i < MsgAttachmentStaging.MAX_ENTRIES; i++) {
            MsgAttachmentStaging.stageItems(session, MsgAttachmentStaging.newKey(), "<root/>");
        }

        MsgAttachmentStaging.stagePdfs(session, restaged, 101, "<PDF>A again</PDF>");
        MsgAttachmentStaging.discard(session, cleared);

        assertThat(MsgAttachmentStaging.claimDropped(session, restaged)).isFalse();
        assertThat(MsgAttachmentStaging.peek(session, restaged).pdfAttachment()).isEqualTo("<PDF>A again</PDF>");
        assertThat(MsgAttachmentStaging.claimDropped(session, cleared)).isFalse();
    }

    @Test
    void shouldGiveEveryViewOfAMessageTheSameKey_whenForwardingIt() {
        String forwardKey = MsgAttachmentStaging.forwardKey(session, 77);

        assertThat(MsgAttachmentStaging.isKey(forwardKey)).isTrue();
        // A second render of the same message (a reload, or linking a patient) keeps the key.
        assertThat(MsgAttachmentStaging.forwardKey(session, 77)).isEqualTo(forwardKey);
        assertThat(MsgAttachmentStaging.forwardKey(session, 78)).isNotEqualTo(forwardKey);
        // Derived from the session's own secret: another login cannot predict it.
        assertThat(MsgAttachmentStaging.forwardKey(new MockHttpSession(), 77)).isNotEqualTo(forwardKey);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRefuseMessageId_whenNotPositive(int messageId) {
        assertThatThrownBy(() -> MsgAttachmentStaging.forwardKey(session, messageId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MsgAttachmentStaging.composeKey(session, messageId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldGiveEachComposeWindowItsOwnCopy_whenOpenedFromOneMessage() {
        String saved = MsgAttachmentStaging.forwardKey(session, 77);
        MsgAttachmentStaging.stageItems(session, saved, "<root>FAKE item</root>");

        String reply = MsgAttachmentStaging.composeKey(session, 77);
        String forward = MsgAttachmentStaging.composeKey(session, 77);
        MsgAttachmentStaging.stagePdfs(session, reply, 101, "<PDF>A</PDF>");

        assertThat(reply).isNotEqualTo(forward).isNotEqualTo(saved);
        assertThat(MsgAttachmentStaging.isKey(reply)).isTrue();
        assertThat(MsgAttachmentStaging.peek(session, forward).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, forward).pdfAttachment()).isNull();
        // Sending the reply takes its copy only: the forward and the saved items keep theirs.
        assertThat(MsgAttachmentStaging.take(session, reply).pdfAttachment()).isEqualTo("<PDF>A</PDF>");
        assertThat(MsgAttachmentStaging.peek(session, forward).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, saved).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, saved).pdfAttachment()).isNull();
    }

    @Test
    void shouldStageNothing_whenNothingWasSavedForTheMessage() {
        String window = MsgAttachmentStaging.composeKey(session, 77);

        assertThat(MsgAttachmentStaging.isKey(window)).isTrue();
        assertThat(MsgAttachmentStaging.peek(session, window)).isNull();
        assertThat(Collections.list(session.getAttributeNames()))
                .as("nothing is staged: only the forwarding secret is in the session")
                .hasSize(1);
    }

    @Test
    void shouldDropAnotherWindowNotTheSavedItems_whenACopyOverfillsTheCap() {
        String saved = MsgAttachmentStaging.forwardKey(session, 77);
        MsgAttachmentStaging.stageItems(session, saved, "<root>FAKE item</root>");
        List<String> windows = new ArrayList<>();
        for (int i = 1; i < MsgAttachmentStaging.MAX_ENTRIES; i++) {
            String window = MsgAttachmentStaging.newKey();
            windows.add(window);
            MsgAttachmentStaging.stagePdfs(session, window, 100 + i, "<PDF>" + i + "</PDF>");
        }

        String reply = MsgAttachmentStaging.composeKey(session, 77);

        // The saved items were the oldest entry; the oldest other window is dropped instead, and
        // its send is told so.
        assertThat(MsgAttachmentStaging.peek(session, saved).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, reply).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, windows.get(0))).isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, windows.get(0))).isTrue();
        assertThat(MsgAttachmentStaging.claimDropped(session, saved)).isFalse();
    }

    @Test
    void shouldReleaseTheMessagesSavedItems_whenAWindowOpenedFromItSends() {
        String saved = MsgAttachmentStaging.forwardKey(session, 77);
        MsgAttachmentStaging.stageItems(session, saved, "<root>FAKE item</root>");
        String reply = MsgAttachmentStaging.composeKey(session, 77);
        String forward = MsgAttachmentStaging.composeKey(session, 77);

        // The reply is sent: the send takes its own entry, then releases the message's saved items.
        assertThat(MsgAttachmentStaging.take(session, reply).itemAttachment()).isEqualTo("<root>FAKE item</root>");
        MsgAttachmentStaging.releaseSource(session, reply);

        assertThat(MsgAttachmentStaging.peek(session, saved)).isNull();
        assertThat(MsgAttachmentStaging.peek(session, forward).itemAttachment())
                .as("a window already open keeps its own copy")
                .isEqualTo("<root>FAKE item</root>");
        assertThat(MsgAttachmentStaging.peek(session, MsgAttachmentStaging.composeKey(session, 77)))
                .as("a reply opened afterwards does not attach them again")
                .isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, forward)).isFalse();
    }

    @Test
    void shouldReleaseNothing_whenTheWindowWasNotOpenedFromSavedItems() {
        String saved = MsgAttachmentStaging.forwardKey(session, 77);
        MsgAttachmentStaging.stageItems(session, saved, "<root>FAKE item</root>");
        String plain = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, plain, 101, "<PDF>A</PDF>");

        MsgAttachmentStaging.take(session, plain);
        MsgAttachmentStaging.releaseSource(session, plain);
        MsgAttachmentStaging.releaseSource(session, "malformed");

        assertThat(MsgAttachmentStaging.peek(session, saved).itemAttachment()).isEqualTo("<root>FAKE item</root>");
    }

    @Test
    void shouldTellEachWindowOnce_whenTheMessagesSavedItemsWereReleasedBeforeItOpened() {
        String saved = MsgAttachmentStaging.forwardKey(session, 77);
        MsgAttachmentStaging.stageItems(session, saved, "<root>FAKE item</root>");
        for (int i = 0; i < MsgAttachmentStaging.MAX_ENTRIES; i++) {
            MsgAttachmentStaging.stagePdfs(session, MsgAttachmentStaging.newKey(), 100 + i, "<PDF>" + i + "</PDF>");
        }

        String reply = MsgAttachmentStaging.composeKey(session, 77);

        assertThat(MsgAttachmentStaging.peek(session, reply)).isNull();
        // Its send is refused once with the reason, as a window whose own attachments were released.
        assertThat(MsgAttachmentStaging.claimDropped(session, reply)).isTrue();
        assertThat(MsgAttachmentStaging.claimDropped(session, reply)).isFalse();
        // A forward opened from the same message afterwards is told too, not sent without them.
        String forward = MsgAttachmentStaging.composeKey(session, 77);
        assertThat(MsgAttachmentStaging.peek(session, forward)).isNull();
        assertThat(MsgAttachmentStaging.claimDropped(session, forward)).isTrue();
        assertThat(MsgAttachmentStaging.claimDropped(session, forward)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReplaceTheSessionValue_whenStaging() {
        String window = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stagePdfs(session, window, 101, "<PDF>A</PDF>");
        Object before = Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute).findFirst().orElseThrow();

        MsgAttachmentStaging.stageItems(session, window, "<root/>");
        Object after = Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute).findFirst().orElseThrow();

        // A container that persists or replicates sessions only sees setAttribute calls.
        assertThat(after).isNotSameAs(before);
        assertThatThrownBy(() -> ((java.util.Map<String, Object>) after).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRedactContent_inToString() {
        assertThat(new MsgAttachmentStaging.Staged(101, "<PDF>FAKE chart</PDF>", "<root/>").toString())
                .doesNotContain("FAKE").doesNotContain("101");
    }
}
