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
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies bounded disposable session metadata retains refreshed patients and leaves
 * other metadata categories and unsaved clinical drafts untouched.
 *
 * @since 2026-10-24
 */
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
