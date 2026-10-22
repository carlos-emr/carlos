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
import java.net.URL;
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

    /** Shared filename for the antenatal risk-list override and packaged default. */
    public static final String RISK_FILE_NAME = "desantenatalplannerrisks_99_12.xml";

    private AntenatalConfigLocation() {
    }

    /**
     * Resolves a configuration file inside the configured document directory.
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
     * check into the XML parser. This reader deliberately follows a readable
     * operator-managed symbolic link so upgrading does not silently replace active
     * clinical rules with the packaged default. The editor detects that case and
     * becomes read-only; the atomic writer still refuses to replace the link.
     *
     * @param fileName configuration file name to look for
     * @return the override file, or {@code null} to fall back to the packaged default
     */
    // FindSecBugs PATH_TRAVERSAL_IN: the directory comes from trusted DOCUMENT_DIR configuration and the filename is a caller constant, never request input.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "directory is trusted DOCUMENT_DIR configuration and the filename is a constant supplied by the caller, not user-controllable input")
    public static File readableOverride(String fileName) {
        try {
            File override = configuredPath(fileName).toFile();
            return override.isFile() && override.canRead() ? override : null;
        } catch (IOException e) {
            // Readers fall back to the packaged default. The editor separately
            // surfaces the storage problem and becomes read-only.
            return null;
        }
    }

    /**
     * Selects the parser location for an override or its packaged fallback.
     *
     * <p>A servlet resource URL works for both exploded deployments and resources
     * inside a WAR, unlike {@code ServletContext.getRealPath()}, which may return
     * {@code null}. A readable operator override retains precedence.
     *
     * @param fileName override filename inside {@code DOCUMENT_DIR}
     * @param packagedResource packaged servlet resource, or {@code null} if missing
     * @return URI string accepted by the SAX parser
     * @throws IOException when neither source is readable
     */
    public static String readableResourceLocation(String fileName, URL packagedResource) throws IOException {
        File override = readableOverride(fileName);
        if (override != null) {
            return override.toPath().toUri().toString();
        }
        if (packagedResource == null) {
            throw new IOException("The packaged antenatal configuration is unavailable: " + fileName);
        }
        return packagedResource.toExternalForm();
    }
}
