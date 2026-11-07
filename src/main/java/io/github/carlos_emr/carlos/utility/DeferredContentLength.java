/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.util.OptionalLong;
import java.util.function.LongConsumer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The one Content-Length state machine shared by every CARLOS response decorator.
 *
 * <p>A decorator that may append to the body (CSRF script, logout script, print notice) cannot
 * let a downstream Content-Length reach the container until it knows whether it will decorate.
 * Each wrapper therefore holds the declared length in one of two states and applies one rule set:</p>
 * <ul>
 *   <li><b>pending</b>: declared by downstream code but not yet handed to the delegate;</li>
 *   <li><b>published</b>: handed to the delegate for a channel that will not be decorated.</li>
 * </ul>
 * <p>The transitions the wrappers share: {@link #publishPending()} when a channel is selected and
 * the type is known not to be HTML; {@link #retractToPending()} when the type turns to HTML before
 * commit; {@link #discardPending()} once a decoration changes the body; {@link #onBufferReset()}
 * after an <em>accepted</em> {@code resetBuffer()}, which drops a pending length and removes a
 * published one because both described the discarded body (a forward always replaces the body,
 * so this is stricter than the bare container, which keeps the header); {@link #onReset()} after an
 * accepted full {@code reset()}, which already cleared the delegate's headers; and {@link #remove()}
 * for a downstream {@code setHeader("Content-Length", null)}. The wrapper calls the delegate before
 * the transition, so a rejected reset leaves this state unchanged.</p>
 *
 * <p>The object never touches the servlet response itself: the wrapper supplies the two delegate
 * calls at construction, which keeps the state machine testable without a container.</p>
 *
 * @since 2026-11-05
 */
public final class DeferredContentLength {

    /** The header this object manages. */
    public static final String HEADER = "Content-Length";

    private final LongConsumer publisher;
    private final Runnable remover;
    private Long pending;
    private Long published;

    /**
     * @param publisher hands a length to the delegate, normally {@code super::setContentLengthLong}
     * @param remover   removes the header from the delegate, normally
     *                  {@code () -> super.setHeader(HEADER, null)}
     */
    public DeferredContentLength(LongConsumer publisher, Runnable remover) {
        this.publisher = publisher;
        this.remover = remover;
    }

    /** Records a declared length; a later declaration replaces an earlier pending one. */
    public void defer(long length) {
        pending = length;
    }

    /** Hands a length straight to the delegate for a pass-through channel and records it as published. */
    public void publishNow(long length) {
        publisher.accept(length);
        published = length;
    }

    /**
     * Hands the pending length to the delegate, if there is one.
     *
     * @return whether a length was published by this call
     */
    public boolean publishPending() {
        if (pending == null) {
            return false;
        }
        publisher.accept(pending);
        published = pending;
        pending = null;
        return true;
    }

    /**
     * Removes a published length from the delegate and keeps it pending, unless a newer pending
     * length already exists. Used when the content type turns to HTML before commit: the body may
     * still be decorated, but may also end up needing the original length after all.
     */
    public void retractToPending() {
        if (published == null) {
            return;
        }
        remover.run();
        if (pending == null) {
            pending = published;
        }
        published = null;
    }

    /** Forgets a pending length once a decoration has changed the body. */
    public void discardPending() {
        pending = null;
    }

    /** Applies the shared rule for an accepted {@code resetBuffer()}: drop pending, remove published. */
    public void onBufferReset() {
        pending = null;
        if (published != null) {
            remover.run();
            published = null;
        }
    }

    /** Forgets both states after an accepted full {@code reset()}; the delegate cleared its own headers. */
    public void onReset() {
        pending = null;
        published = null;
    }

    /** Honours a downstream {@code setHeader("Content-Length", null)}: forget both states and remove. */
    public void remove() {
        pending = null;
        published = null;
        remover.run();
    }

    public boolean isPending() {
        return pending != null;
    }

    public boolean isPublished() {
        return published != null;
    }

    /**
     * Parses a Content-Length header string the way the wrappers accept one: a non-negative
     * decimal integer, surrounding whitespace tolerated. Anything else is empty, and the caller
     * ignores it with a warning rather than letting a malformed value reach the container.
     */
    public static OptionalLong parse(String value) {
        if (value == null) {
            return OptionalLong.empty();
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed < 0 ? OptionalLong.empty() : OptionalLong.of(parsed);
        } catch (NumberFormatException _) {
            return OptionalLong.empty();
        }
    }

    /** HTTP field names are case-insensitive (RFC 9110); this selects response framing, not identity. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an HTTP header name; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an HTTP header name; not a security or authorization decision")
    public static boolean isContentLengthHeader(String name) {
        return HEADER.equalsIgnoreCase(name);
    }
}
