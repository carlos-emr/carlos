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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One document to print: its HTML, the directory its relative references resolve against, and the
 * directories it may read local files from.
 *
 * <p>The constructor validates, so a request that exists is safe to hand to the render service:</p>
 *
 * <ul>
 *   <li>{@code html} and {@code source} are not {@code null}. An empty or whitespace-only
 *       {@code html} is accepted here; deciding that there is nothing to print is the service's
 *       job;</li>
 *   <li>{@code allowedDirectories} is not empty and every entry is absolute, normalised and not a
 *       filesystem root. It is copied, so the caller's list can change afterwards;</li>
 *   <li>{@code baseDirectory} is {@code null}, or absolute, normalised and equal to or inside one
 *       of the allowed directories.</li>
 * </ul>
 *
 * <p>An invalid request is a programming error, not a runtime condition, so the constructor throws
 * {@link NullPointerException} or {@link IllegalArgumentException}, with a message that names the
 * failed check and never a path or any of the HTML. The checks are lexical and never touch the
 * file system; the render engine checks every file it opens against the allowed directories again
 * (symbolic links included).</p>
 *
 * <p>The HTML and the paths can identify a patient. {@link #toString()} prints only the source,
 * the HTML length and the number of allowed directories, so logging a request cannot write them
 * (a record's default {@code toString()} would print the HTML). The accessors return the values as
 * given and must not be logged.</p>
 *
 * @param html the document markup as the caller produced it
 * @param baseDirectory the directory relative stylesheet and image references resolve against
 *        (the webapp root for forms), or {@code null} when the caller cannot resolve one; the
 *        service then renders against a private empty directory and reports
 *        {@link HtmlPdfRenderDegradation#WEBAPP_ROOT_UNAVAILABLE}
 * @param allowedDirectories the directories the document may read local stylesheets and images
 *        from, for example the webapp root, the eForm image directory and the JVM temporary
 *        directory; the render adds only its own private temporary directories to them
 * @param source which kind of document this is, for the render's log line
 * @since 2026-10-29
 */
public record HtmlPdfRenderRequest(
        String html,
        Path baseDirectory,
        List<Path> allowedDirectories,
        HtmlPdfRenderSource source) {

    /**
     * Validates the components and copies the allowed directories; the class description lists
     * the rules.
     *
     * @throws NullPointerException if {@code html}, {@code source}, {@code allowedDirectories} or
     *         an entry of the list is {@code null}
     * @throws IllegalArgumentException if {@code allowedDirectories} is empty, an allowed
     *         directory is relative, not normalised or a filesystem root, or {@code baseDirectory}
     *         is relative, not normalised or outside every allowed directory
     */
    public HtmlPdfRenderRequest {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(allowedDirectories, "allowedDirectories");
        // List.copyOf also rejects a null entry (NullPointerException).
        allowedDirectories = List.copyOf(allowedDirectories);
        if (allowedDirectories.isEmpty()) {
            throw new IllegalArgumentException("allowedDirectories must not be empty");
        }
        for (Path directory : allowedDirectories) {
            requireAbsoluteAndNormalised(directory, "allowedDirectories entries");
            if (directory.getParent() == null) {
                throw new IllegalArgumentException("allowedDirectories entries must not be a filesystem root");
            }
        }
        if (baseDirectory != null) {
            requireAbsoluteAndNormalised(baseDirectory, "baseDirectory");
            // Path.startsWith compares whole path elements, so "/data/webapp-evil" is not inside
            // "/data/webapp". Both sides are normalised above, which is what makes a ".." segment
            // unable to climb out of the allowed directory it appears to start with.
            if (allowedDirectories.stream().noneMatch(baseDirectory::startsWith)) {
                throw new IllegalArgumentException("baseDirectory must be inside an allowed directory");
            }
        }
    }

    private static void requireAbsoluteAndNormalised(Path path, String what) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(what + " must be absolute");
        }
        if (!path.equals(path.normalize())) {
            throw new IllegalArgumentException(what + " must be normalised");
        }
    }

    /**
     * Prints the source, the HTML length and the number of allowed directories only: never the
     * HTML, a directory or any other value that could identify a patient.
     */
    @Override
    public String toString() {
        return "HtmlPdfRenderRequest[source=" + source
                + ", htmlLength=" + html.length()
                + ", allowedDirectories=" + allowedDirectories.size() + "]";
    }
}
