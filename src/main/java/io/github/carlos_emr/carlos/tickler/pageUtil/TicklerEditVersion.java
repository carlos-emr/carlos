/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

import io.github.carlos_emr.carlos.commn.model.Tickler;

/**
 * The version of a tickler the Edit Tickler form was rendered from.
 *
 * <p>The edit popup posts the status, priority, assignee and service date it showed, and
 * {@code EditTickler2Action} wrote every one that differed from the stored row. A popup opened
 * before another user completed or deleted the task therefore put it back to Active when it only
 * meant to change the priority. The form now carries this version as {@value #PARAMETER}; the
 * action locks the row, computes the version again and refuses a form that no longer matches.</p>
 *
 * <p>The version is a SHA-256 over the fields the form writes, the row's update time and the
 * number of comments. The update time alone is not enough: the column is stored to the second, so
 * two saves within one second would look the same. A post without the parameter (an older page,
 * a hand-built request) is saved without the check.</p>
 *
 * @since 2026-11-12
 */
public final class TicklerEditVersion {

    /** The request parameter, and hidden edit-form field, that carries the version. */
    public static final String PARAMETER = "ticklerVersion";

    private TicklerEditVersion() { }

    /**
     * @param version the posted {@value #PARAMETER} value
     * @return whether the form carried a version to compare
     */
    public static boolean isPresent(String version) {
        return version != null && !version.isBlank();
    }

    /**
     * Computes the version of a tickler as stored.
     *
     * @param tickler the tickler, with its comments loaded
     * @return an opaque token that changes whenever the status, priority, assignee, service date,
     *         update time or number of comments changes
     */
    public static String of(Tickler tickler) {
        String state = String.join("\u0000",
                tickler.getStatus() == null ? "" : tickler.getStatus().name(),
                tickler.getPriority() == null ? "" : tickler.getPriority().name(),
                tickler.getTaskAssignedTo() == null ? "" : tickler.getTaskAssignedTo(),
                millis(tickler.getServiceDate()),
                millis(tickler.getUpdateDate()),
                Integer.toString(tickler.getComments() == null ? 0 : tickler.getComments().size()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", impossible);
        }
    }

    private static String millis(Date date) {
        return date == null ? "" : Long.toString(date.getTime());
    }
}
