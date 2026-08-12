/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.decision;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;

/**
 * Single definition of where the antenatal decision-support configuration lives.
 *
 * <p>The risk list and the checklist are each read by three pages and written by
 * one service, and every one of those sites used to resolve {@code DOCUMENT_DIR}
 * on its own. They drifted: the writer moved to {@code Path.resolve} while the
 * readers still concatenated, so an administrator's override was stored correctly
 * and then never read whenever {@code DOCUMENT_DIR} lacked a trailing separator.
 * Keeping the precedence and the blank-value rule here means the next change to
 * either cannot leave one caller behind.
 *
 * @since 2026-08-12
 */
public final class AntenatalConfigLocation {

    private AntenatalConfigLocation() {
    }

    /**
     * Returns the configured document directory.
     *
     * @param fileName configuration file name to resolve inside the directory
     * @return the absolute path the override would occupy, whether or not it exists
     * @throws IOException when {@code DOCUMENT_DIR} is unset, blank, or not a usable
     *         path; reported as an I/O failure so callers surface a storage error and
     *         keep the submitted document, rather than an error page
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the directory comes from trusted DOCUMENT_DIR configuration and the filename is a caller constant, never request input.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "directory is trusted DOCUMENT_DIR configuration and the filename is a constant supplied by the caller, not user-controllable input")
    public static Path configuredPath(String fileName) throws IOException {
        String documentDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (documentDirectory == null || documentDirectory.isBlank()) {
            throw new IOException("DOCUMENT_DIR is not configured.");
        }
        try {
            return Path.of(documentDirectory).resolve(fileName);
        } catch (InvalidPathException e) {
            // Unchecked, so it would otherwise escape the save action's IOException
            // handler and lose the administrator's submission to an error page.
            throw new IOException("DOCUMENT_DIR is not a usable path.", e);
        }
    }

    /**
     * Returns the administrator's override for a configuration file, if usable.
     *
     * <p>A blank {@code DOCUMENT_DIR} is treated as "no override" rather than
     * resolving a bare filename against the JVM working directory, and the result
     * must be a readable regular file — a directory at that path is not a
     * configuration document and previously slipped through a {@code canRead()}
     * check into the XML parser.
     *
     * @param fileName configuration file name to look for
     * @return the override file, or {@code null} to fall back to the packaged default
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the directory comes from trusted DOCUMENT_DIR configuration and the filename is a caller constant, never request input.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "directory is trusted DOCUMENT_DIR configuration and the filename is a constant supplied by the caller, not user-controllable input")
    public static File readableOverride(String fileName) {
        String documentDirectory = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        if (documentDirectory == null || documentDirectory.isBlank()) {
            return null;
        }
        File override = new File(documentDirectory, fileName);
        return override.isFile() && override.canRead() ? override : null;
    }
}
