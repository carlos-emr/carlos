/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerComment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/** The evidence the tickler forms carry of what they were rendered from. */
@Tag("unit")
@Tag("tickler")
@DisplayName("Tickler edit version and rendered list status")
class TicklerEditVersionUnitTest extends CarlosUnitTestBase {

    private Tickler tickler;

    @BeforeEach
    void setUp() {
        tickler = new Tickler();
        tickler.setId(42);
        tickler.setStatus(Tickler.STATUS.A);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(new Date(1_800_000_000_000L));
        tickler.setUpdateDate(new Date(1_800_000_100_000L));
    }

    @Test
    void shouldStayTheSame_whenNothingTheFormShowsChanged() {
        String rendered = TicklerEditVersion.of(tickler);
        tickler.setMessage("the message is not edited by the form");

        assertThat(TicklerEditVersion.of(tickler)).isEqualTo(rendered).hasSize(64);
    }

    @Test
    void shouldChange_whenAnotherUserCompletesTheTaskWithinTheSameSecond() {
        String rendered = TicklerEditVersion.of(tickler);
        // update_date is stored to the second, so it need not move.
        tickler.setStatus(Tickler.STATUS.C);

        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(rendered);
    }

    @Test
    void shouldChange_whenPriorityAssigneeServiceDateOrUpdateTimeChanges() {
        String rendered = TicklerEditVersion.of(tickler);

        tickler.setPriority(Tickler.PRIORITY.High);
        String afterPriority = TicklerEditVersion.of(tickler);
        tickler.setTaskAssignedTo("999997");
        String afterAssignee = TicklerEditVersion.of(tickler);
        tickler.setServiceDate(new Date(1_800_086_400_000L));
        String afterServiceDate = TicklerEditVersion.of(tickler);
        tickler.setUpdateDate(new Date(1_800_000_101_000L));
        String afterUpdate = TicklerEditVersion.of(tickler);

        assertThat(java.util.List.of(rendered, afterPriority, afterAssignee, afterServiceDate, afterUpdate))
                .doesNotHaveDuplicates();
    }

    @Test
    void shouldChange_whenAnotherUserAddsAComment() {
        String rendered = TicklerEditVersion.of(tickler);
        tickler.getComments().add(new TicklerComment());

        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(rendered);
    }

    @Test
    void shouldTreatBlankAsNoVersion_forUnversionedPosts() {
        assertThat(TicklerEditVersion.isPresent(null)).isFalse();
        assertThat(TicklerEditVersion.isPresent("  ")).isFalse();
        assertThat(TicklerEditVersion.isPresent(TicklerEditVersion.of(tickler))).isTrue();
    }

    @Test
    void shouldReadRenderedStatuses_fromTheBulkFormsSingleField() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(TicklerRenderedStatus.PARAMETER, "123:A, 124:C,125:D");

        assertThat(TicklerRenderedStatus.read(request)).containsExactlyInAnyOrderEntriesOf(Map.of(
                123, Tickler.STATUS.A, 124, Tickler.STATUS.C, 125, Tickler.STATUS.D));
    }

    @Test
    void shouldIgnoreMalformedPairs_whenReadingRenderedStatuses() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(TicklerRenderedStatus.PARAMETER, "123:A,abc:A,124:X,:C,125,126:C");

        assertThat(TicklerRenderedStatus.read(request)).containsExactlyInAnyOrderEntriesOf(Map.of(
                123, Tickler.STATUS.A, 126, Tickler.STATUS.C));
        assertThat(TicklerRenderedStatus.read(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void shouldRememberRefusedTasksOnce_forTheNextListRendering() {
        MockHttpSession session = new MockHttpSession();
        TicklerRenderedStatus.rememberRefused(session, java.util.List.of());
        assertThat(TicklerRenderedStatus.takeRefused(session)).isEmpty();

        TicklerRenderedStatus.rememberRefused(session, java.util.List.of(7, 9));

        assertThat(TicklerRenderedStatus.takeRefused(session)).containsExactly(7, 9);
        assertThat(TicklerRenderedStatus.takeRefused(session)).isEmpty();
        assertThat(TicklerRenderedStatus.takeRefused(null)).isEmpty();
    }
}
