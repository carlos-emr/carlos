/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("filter")
@DisplayName("DeferredContentLength state machine shared by the response wrappers")
class DeferredContentLengthUnitTest {

    private final List<Long> published = new ArrayList<>();
    private final AtomicInteger removed = new AtomicInteger();
    private DeferredContentLength lengths;

    @BeforeEach
    void setUp() {
        lengths = new DeferredContentLength(published::add, removed::incrementAndGet);
    }

    @Test
    void shouldPublishPendingOnce_whenChannelIsSelected() {
        lengths.defer(42);
        assertThat(lengths.isPending()).isTrue();
        assertThat(lengths.publishPending()).isTrue();
        assertThat(lengths.publishPending()).isFalse();
        assertThat(published).containsExactly(42L);
        assertThat(lengths.isPending()).isFalse();
        assertThat(lengths.isPublished()).isTrue();
    }

    @Test
    void shouldReplaceEarlierPending_whenDeclaredAgain() {
        lengths.defer(1);
        lengths.defer(2);
        lengths.publishPending();
        assertThat(published).containsExactly(2L);
    }

    @Test
    void shouldPublishImmediately_forPassthroughChannels() {
        lengths.publishNow(7);
        assertThat(published).containsExactly(7L);
        assertThat(lengths.isPublished()).isTrue();
        assertThat(lengths.isPending()).isFalse();
    }

    @Test
    void shouldRetractPublishedToPending_whenTypeTurnsHtml() {
        lengths.defer(10);
        lengths.publishPending();
        lengths.retractToPending();
        assertThat(removed.get()).isEqualTo(1);
        assertThat(lengths.isPublished()).isFalse();
        assertThat(lengths.isPending()).isTrue();
        // A newer pending value wins over the retracted one.
        lengths.defer(11);
        lengths.publishPending();
        assertThat(published).containsExactly(10L, 11L);
        // Nothing published: retracting is a no-op.
        lengths.retractToPending();
        assertThat(removed.get()).isEqualTo(2);
        lengths.retractToPending();
        assertThat(removed.get()).isEqualTo(2);
    }

    @Test
    void shouldDropPendingAndRetractPublished_onBufferReset() {
        lengths.defer(5);
        lengths.onBufferReset();
        assertThat(lengths.isPending()).isFalse();
        assertThat(removed.get()).isZero();
        lengths.publishNow(6);
        lengths.onBufferReset();
        assertThat(removed.get()).isEqualTo(1);
        assertThat(lengths.isPublished()).isFalse();
    }

    @Test
    void shouldForgetEverythingWithoutRemoving_onFullReset() {
        lengths.defer(5);
        lengths.publishNow(6);
        lengths.onReset();
        assertThat(lengths.isPending()).isFalse();
        assertThat(lengths.isPublished()).isFalse();
        assertThat(removed.get()).isZero();
    }

    @Test
    void shouldRemoveAndForget_whenHeaderIsCleared() {
        lengths.defer(5);
        lengths.publishNow(6);
        lengths.remove();
        assertThat(lengths.isPending()).isFalse();
        assertThat(lengths.isPublished()).isFalse();
        assertThat(removed.get()).isEqualTo(1);
    }

    @Test
    void shouldDiscardPendingOnly_whenDecorationChangesTheBody() {
        lengths.defer(5);
        lengths.discardPending();
        assertThat(lengths.isPending()).isFalse();
        assertThat(lengths.publishPending()).isFalse();
        assertThat(published).isEmpty();
    }

    @Test
    void shouldParseOnlyWellFormedValues_forHeaderStrings() {
        assertThat(DeferredContentLength.parse("123")).isEqualTo(OptionalLong.of(123));
        assertThat(DeferredContentLength.parse(" 123 ")).isEqualTo(OptionalLong.of(123));
        assertThat(DeferredContentLength.parse("abc")).isEmpty();
        assertThat(DeferredContentLength.parse("")).isEmpty();
        assertThat(DeferredContentLength.parse(null)).isEmpty();
        assertThat(DeferredContentLength.parse("-1")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Length", "content-length", "CONTENT-LENGTH", "cOnTeNt-LeNgTh"})
    void shouldMatchHeaderNameCaseInsensitively_forContentLength(String name) {
        assertThat(DeferredContentLength.isContentLengthHeader(name)).isTrue();
        assertThat(DeferredContentLength.isContentLengthHeader("Content-Type")).isFalse();
        assertThat(DeferredContentLength.isContentLengthHeader(null)).isFalse();
    }
}
