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
package io.github.carlos_emr.carlos.eform.util;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Single source of truth for the eForm asset MIME allowlist shared by the browser-PDF image route
 * ({@link EFormImageViewForPdfGenerationServlet}) and the normal eForm image display action
 * ({@code io.github.carlos_emr.carlos.eform.actions.DisplayImage2Action}). Keeping the allowlist —
 * and the extension parsing/lowercasing that keys into it — in one place stops the streaming paths
 * from drifting when asset types change.
 */
public final class EformAssetContentType {

    private static final String IMAGE_JPEG = "image/jpeg";
    private static final String TEXT_HTML = "text/html";

    /**
     * Resolves the allowlisted content type for an eForm asset filename. Owns the extension
     * parsing and locale-safe lowercasing so consumers cannot re-implement (and drift on) either.
     *
     * @param fileName plain filename such as {@code background.PNG}; null-safe
     * @return the MIME type, or empty when the extension is not on the allowlist
     */
    // IMPROPER_UNICODE: lowercases a literal file extension to key the MIME allowlist; a
    // case-folding surprise can only miss the allowlist (deny), never widen it.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case folds a literal file extension for a MIME allowlist lookup; a fold surprise can only fail closed, and this is not an authorization decision")
    public static Optional<String> forFilename(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(BY_EXTENSION.get(extension));
    }

    /** Lowercase file extension → MIME content type for the assets the eForm routes may stream. */
    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpeg", IMAGE_JPEG),
            Map.entry("jpe", IMAGE_JPEG),
            Map.entry("jpg", IMAGE_JPEG),
            Map.entry("bmp", "image/bmp"),
            Map.entry("cod", "image/cis-cod"),
            Map.entry("ief", "image/ief"),
            Map.entry("jfif", "image/pipeg"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("tif", "image/tiff"),
            Map.entry("pbm", "image/x-portable-bitmap"),
            Map.entry("pnm", "image/x-portable-anymap"),
            Map.entry("pgm", "image/x-portable-greymap"),
            Map.entry("ppm", "image/x-portable-pixmap"),
            Map.entry("xbm", "image/x-xbitmap"),
            Map.entry("xpm", "image/x-xpixmap"),
            Map.entry("xwd", "image/x-xwindowdump"),
            Map.entry("rgb", "image/x-rgb"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("cmx", "image/x-cmx"),
            Map.entry("ras", "image/x-cmu-raster"),
            Map.entry("gif", "image/gif"),
            Map.entry("js", "text/javascript"),
            Map.entry("css", "text/css"),
            Map.entry("json", "application/json"),
            Map.entry("rtl", TEXT_HTML),
            Map.entry("html", TEXT_HTML),
            Map.entry("htm", TEXT_HTML)
    );

    private EformAssetContentType() {
    }
}
