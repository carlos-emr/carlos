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
package io.github.carlos_emr.carlos.utility;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class for validating redirect URLs to prevent open redirect attacks.
 *
 * <p>Only relative, same-origin paths are considered safe. The following are rejected:</p>
 * <ul>
 *   <li>Absolute URIs — any URL with a scheme (e.g. {@code https://evil.com},
 *       {@code javascript:alert(1)}, {@code data:text/html,...})</li>
 *   <li>Protocol-relative URLs — authority present without a scheme
 *       (e.g. {@code //evil.com})</li>
 *   <li>Backslash-based bypasses — some browsers normalise {@code /\evil.com}
 *       to {@code //evil.com} after a redirect</li>
 *   <li>Path-traversal sequences — {@code ..} segments that escape the
 *       application root (e.g. {@code /../evil})</li>
 *   <li>Syntactically invalid URIs</li>
 *   <li>{@code null}</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * if (RedirectValidationUtils.isValidRelativeRedirect(nextPage)) {
 *     response.sendRedirect(nextPage);
 * } else {
 *     response.sendRedirect(request.getContextPath() + "/loginfailed.jsp");
 * }
 * </pre>
 *
 * @since 2026-04-02
 */
public final class RedirectValidationUtils {

    private RedirectValidationUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Returns {@code true} when {@code url} is a safe relative redirect target.
     *
     * <p>A URL is considered safe when it:</p>
     * <ol>
     *   <li>is not {@code null}</li>
     *   <li>does not contain a backslash ({@code \})</li>
     *   <li>is parseable as a {@link URI}</li>
     *   <li>has no scheme (not absolute)</li>
     *   <li>has no authority component (no {@code //host} prefix)</li>
     *   <li>does not contain path-traversal sequences ({@code /../})</li>
     * </ol>
     *
     * @param url String the redirect target URL to validate (may be {@code null})
     * @return boolean {@code true} if the URL is a safe relative redirect; {@code false} otherwise
     */
    public static boolean isValidRelativeRedirect(String url) {
        if (url == null) {
            return false;
        }

        // Block backslash-based bypasses: /\evil.com normalises to //evil.com in browsers
        if (url.contains("\\")) {
            return false;
        }

        // Block path-traversal sequences that could escape the application root
        if (url.contains("/../")) {
            return false;
        }

        try {
            URI uri = new URI(url);

            // Block absolute URIs: http://, https://, javascript:, data:, etc.
            if (uri.isAbsolute()) {
                return false;
            }

            // Block protocol-relative URLs: //evil.com (authority without scheme)
            if (uri.getAuthority() != null) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
