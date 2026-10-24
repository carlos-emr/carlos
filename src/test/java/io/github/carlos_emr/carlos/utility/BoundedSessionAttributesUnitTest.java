/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class BoundedSessionAttributesUnitTest {
    @Test
    void shouldEvictOldMetadata_withoutAffectingOtherCategoriesOrDrafts() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("draft:1", "unsaved");
        BoundedSessionAttributes.put(session, "visits", "visit:1", "first visit");
        for (int i = 0; i <= BoundedSessionAttributes.MAX_ENTRIES; i++) {
            BoundedSessionAttributes.put(session, "encounters", "entry:" + i, "entry");
        }
        assertThat(session.getAttribute("entry:0")).isNull();
        assertThat(session.getAttribute("entry:1")).isEqualTo("entry");
        assertThat(session.getAttribute("visit:1")).isEqualTo("first visit");
        assertThat(session.getAttribute("draft:1")).isEqualTo("unsaved");
    }

    @Test
    void shouldRetainRefreshedPatient_whenMetadataReachesItsBound() {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < BoundedSessionAttributes.MAX_ENTRIES; i++) {
            BoundedSessionAttributes.put(session, "visits", "visit:" + i, "old visit");
        }
        BoundedSessionAttributes.put(session, "visits", "visit:0", "new visit");
        BoundedSessionAttributes.put(session, "visits", "visit:100", "latest visit");
        assertThat(session.getAttribute("visit:0")).isEqualTo("new visit");
        assertThat(session.getAttribute("visit:1")).isNull();
    }
}
