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
package io.github.carlos_emr.carlos.email.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One-time keyed staging of eForm email drafts, so two windows each open their own compose (#4101). */
@Tag("unit")
@Tag("security")
@DisplayName("EmailComposeStaging")
class EmailComposeStagingUnitTest {
    private static final String ATTRIBUTE = EmailComposeStaging.class.getName() + ".drafts";

    private static EmailAttachmentSettings settings(String patient, String name) {
        return new EmailAttachmentSettings("20" + patient, patient, null, new String[]{"30" + patient}, null, null,
                null, true, false, false, false, false, false, null, null, "fake@example.com", "FAKE subject " + name,
                "FAKE message " + name, null, "FULL");
    }

    @Test
    @DisplayName("should give every draft its own one-time key, taken once")
    void shouldTakeEachDraftOnce_byItsOwnKey() {
        MockHttpSession session = new MockHttpSession();
        String first = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        String second = EmailComposeStaging.stage(session, "40002", settings("10002", "B"));

        assertThat(first).isNotEqualTo(second);
        assertThat(EmailComposeStaging.isKey(first)).isTrue();
        assertThat(EmailComposeStaging.isKey(second)).isTrue();
        // Second window first here; the next test takes them the other way round.
        assertThat(EmailComposeStaging.take(session, second).settings().subjectEmail()).isEqualTo("FAKE subject B");
        assertThat(EmailComposeStaging.take(session, first).fid()).isEqualTo("40001");
        assertThat(EmailComposeStaging.take(session, first)).as("a reused key").isNull();
        assertThat(EmailComposeStaging.take(session, second)).as("a reused key").isNull();
        assertThat(session.getAttribute(ATTRIBUTE)).as("nothing left behind").isNull();
    }

    @Test
    @DisplayName("should give each window its own draft when the first window takes first")
    void shouldTakeEachDraftOnce_inSavingOrder() {
        MockHttpSession session = new MockHttpSession();
        String first = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        String second = EmailComposeStaging.stage(session, "40002", settings("10002", "B"));

        assertThat(EmailComposeStaging.take(session, first).settings().subjectEmail()).isEqualTo("FAKE subject A");
        assertThat(EmailComposeStaging.take(session, second).fid()).isEqualTo("40002");
        assertThat(EmailComposeStaging.take(session, first)).isNull();
        assertThat(EmailComposeStaging.take(session, second)).isNull();
        assertThat(session.getAttribute(ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should find nothing for a missing, malformed or unknown key")
    void shouldFindNothing_forMissingMalformedOrUnknownKey() {
        MockHttpSession session = new MockHttpSession();
        String key = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));

        assertThat(EmailComposeStaging.take(session, null)).isNull();
        assertThat(EmailComposeStaging.take(session, "")).isNull();
        assertThat(EmailComposeStaging.take(session, key + "x")).isNull();
        assertThat(EmailComposeStaging.take(session, "AAAAAAAAAAAAAAAAAAAAAA")).isNull();
        assertThat(EmailComposeStaging.take(session, key)).as("still staged after the misses").isNotNull();
    }

    @Test
    @DisplayName("should keep at most the newest drafts and drop the oldest")
    void shouldDropOldestDraft_whenMoreThanMaxAreStaged() {
        MockHttpSession session = new MockHttpSession();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i <= EmailComposeStaging.MAX_DRAFTS; i++) {
            keys.add(EmailComposeStaging.stage(session, "4000" + i, settings("1000" + i, "N" + i)));
        }

        assertThat(EmailComposeStaging.take(session, keys.get(0))).as("the oldest was dropped").isNull();
        for (int i = 1; i <= EmailComposeStaging.MAX_DRAFTS; i++) {
            assertThat(EmailComposeStaging.take(session, keys.get(i)).fid()).isEqualTo("4000" + i);
        }
    }

    @Test
    @DisplayName("should never change a published draft map, only replace it")
    @SuppressWarnings("unchecked")
    void shouldReplacePublishedMap_neverChangeIt() {
        MockHttpSession session = new MockHttpSession();
        String first = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        Map<String, Object> published = (Map<String, Object>) session.getAttribute(ATTRIBUTE);

        EmailComposeStaging.stage(session, "40002", settings("10002", "B"));
        EmailComposeStaging.take(session, first);

        assertThat(published).hasSize(1).containsKey(first);
        Map<String, Object> current = (Map<String, Object>) session.getAttribute(ATTRIBUTE);
        assertThat(current).isNotSameAs(published);
        assertThatThrownBy(published::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should put a taken draft back under its own key without touching another window's")
    void shouldRestoreDraft_underItsOwnKey() {
        MockHttpSession session = new MockHttpSession();
        String first = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        String second = EmailComposeStaging.stage(session, "40002", settings("10002", "B"));
        EmailComposeStaging.Draft taken = EmailComposeStaging.take(session, first);

        EmailComposeStaging.restore(session, first, taken);
        EmailComposeStaging.restore(session, "not-a-key", taken);
        EmailComposeStaging.restore(session, second, null);

        assertThat(EmailComposeStaging.take(session, first)).isSameAs(taken);
        assertThat(EmailComposeStaging.take(session, second).fid()).isEqualTo("40002");
        assertThat(session.getAttribute(ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should drop the oldest pending draft when a restore finds the session full")
    void shouldDropOldestDraft_whenRestoringIntoFullSession() {
        MockHttpSession session = new MockHttpSession();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < EmailComposeStaging.MAX_DRAFTS; i++) {
            keys.add(EmailComposeStaging.stage(session, "4000" + i, settings("1000" + i, "N" + i)));
        }
        String failing = keys.get(EmailComposeStaging.MAX_DRAFTS - 1);
        EmailComposeStaging.Draft taken = EmailComposeStaging.take(session, failing);
        String newest = EmailComposeStaging.stage(session, "40099", settings("10099", "late"));

        EmailComposeStaging.restore(session, failing, taken);

        assertThat(EmailComposeStaging.take(session, keys.get(0))).as("the oldest was dropped").isNull();
        assertThat(EmailComposeStaging.take(session, failing)).isSameAs(taken);
        assertThat(EmailComposeStaging.take(session, newest)).isNotNull();
    }

    @Test
    @DisplayName("should do nothing when restoring into a session that has ended")
    void shouldIgnoreRestore_whenSessionWasInvalidated() {
        MockHttpSession session = new MockHttpSession();
        String key = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        EmailComposeStaging.Draft taken = EmailComposeStaging.take(session, key);
        session.invalidate();

        EmailComposeStaging.restore(session, key, taken);  // must not throw
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("should keep drafts serializable for a persisted or replicated session, and redacted")
    void shouldSerializeAndRedactDrafts_forSessionStorage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String key = EmailComposeStaging.stage(session, "40001", settings("10001", "A"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(session.getAttribute(ATTRIBUTE));
        }
        MockHttpSession restored = new MockHttpSession();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.setAttribute(ATTRIBUTE, in.readObject());
        }
        EmailComposeStaging.Draft back = EmailComposeStaging.take(restored, key);
        assertThat(back.fid()).isEqualTo("40001");
        assertThat(back.settings().attachedDocuments()).containsExactly("3010001");
        EmailComposeStaging.Draft draft = new EmailComposeStaging.Draft("40001", settings("10001", "A"));
        assertThat(draft.toString()).doesNotContain("FAKE").doesNotContain("10001");
        assertThat(draft.settings().toString()).doesNotContain("FAKE").doesNotContain("10001");
        assertThat(Collections.singletonList(draft).toString()).doesNotContain("FAKE message");
    }
}
