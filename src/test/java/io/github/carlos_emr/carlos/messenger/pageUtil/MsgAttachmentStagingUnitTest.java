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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        assertThat(MsgAttachmentStaging.take(session, window)).isNotNull();
        assertThat(MsgAttachmentStaging.take(session, window)).isNull();
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
