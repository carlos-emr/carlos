// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import jakarta.el.ELContext;
import jakarta.el.RecordELResolver;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class QueryPageUnitTest {
    @Test
    void shouldExposeComputedNavigationProperties_whenJakartaElResolvesARecord() {
        var page = new QueryPage<>(List.of("a", "b"), 37, 1, 15);
        var resolver = new RecordELResolver();
        var context = mock(ELContext.class);
        assertThat(resolver.getValue(context, page, "end")).isEqualTo(17L);
        assertThat(resolver.getValue(context, page, "pageCount")).isEqualTo(3);
        assertThat(resolver.getValue(context, page, "previous")).isEqualTo(true);
        assertThat(resolver.getValue(context, page, "next")).isEqualTo(true);
        var last = new QueryPage<>(List.of("c"), 16, 1, 15);
        assertThat(resolver.getValue(context, last, "next")).isEqualTo(false);
        var empty = new QueryPage<>(List.of(), 0, 0, 10);
        assertThat(resolver.getValue(context, empty, "end")).isEqualTo(0L);
        assertThat(resolver.getValue(context, empty, "previous")).isEqualTo(false);
        assertThat(resolver.getValue(context, empty, "next")).isEqualTo(false);
    }
}
