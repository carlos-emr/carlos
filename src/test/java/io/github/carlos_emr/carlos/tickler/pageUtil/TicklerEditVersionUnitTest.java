/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerComment;
import io.github.carlos_emr.carlos.commn.model.TicklerUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.util.Date;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TicklerEditVersionUnitTest {
    @Test
    void shouldDetectStatusAndAppendOnlyChanges_evenWhenTheTimestampDoesNotChange() {
        Tickler tickler = new Tickler();
        String original = TicklerEditVersion.of(tickler);
        tickler.setStatus(Tickler.STATUS.C);
        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(original);
        tickler.setStatus(Tickler.STATUS.A);
        assertThat(TicklerEditVersion.of(tickler)).isEqualTo(original);
        TicklerComment comment = new TicklerComment(); comment.setId(11);
        tickler.getComments().add(comment);
        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(original);
        tickler.getComments().clear();
        TicklerUpdate update = new TicklerUpdate(); update.setId(12);
        tickler.getUpdates().add(update);
        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(original);
    }

    @Test
    void shouldCanonicalizeDatesAndCollectionOrder_withoutLosingFieldBoundaries() {
        Tickler tickler = new Tickler();
        tickler.setServiceDate(new Date(1000));
        tickler.setMessage("ab"); tickler.setCreator("c");
        TicklerComment first = new TicklerComment(); first.setId(1);
        TicklerComment second = new TicklerComment(); second.setId(2);
        tickler.setComments(new java.util.LinkedHashSet<>(java.util.List.of(first, second)));
        String original = TicklerEditVersion.of(tickler);
        tickler.setServiceDate(new java.sql.Timestamp(1000));
        tickler.setComments(new java.util.LinkedHashSet<>(java.util.List.of(second, first)));
        assertThat(TicklerEditVersion.of(tickler)).isEqualTo(original);
        tickler.setMessage("a"); tickler.setCreator("bc");
        assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(original);
        tickler.setMessage(null); original = TicklerEditVersion.of(tickler);
        tickler.setMessage(""); assertThat(TicklerEditVersion.of(tickler)).isNotEqualTo(original);
    }
}
