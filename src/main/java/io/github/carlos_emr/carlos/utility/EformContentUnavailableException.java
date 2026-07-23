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

/**
 * Signals that an eForm browser render failed <em>only</em> because one or more of the eForm's own
 * same-origin (CARLOS-served) visual assets — a signature block, a form image, a stylesheet — could
 * not be loaded. The captured PDF would therefore be visually incomplete.
 *
 * <p>Unlike a plain {@link PDFGenerationException}, this failure is <strong>user-recoverable</strong>:
 * the page itself loaded and the rest of the form rendered, so a caller may re-issue the render with
 * the "render anyway" flag to accept the incomplete document (e.g. after prompting the clinician).
 * It is deliberately distinct from hard failures that are <em>not</em> user-overridable — a main
 * document that never loaded (nothing to render) or an attempted live egress channel (a security
 * signal) — so the web layer can offer the override for this case alone.</p>
 *
 * <p>{@link #getMissingAssetCount()} reports how many critical assets failed (a count only — never
 * asset URLs or names, which can carry PHI).</p>
 */
public class EformContentUnavailableException extends PDFGenerationException {

    private static final long serialVersionUID = 1L;

    private final int missingAssetCount;

    /**
     * @param message the detail message (must not embed asset URLs/names — count only)
     * @param missingAssetCount the number of the eForm's own critical assets that failed to load
     */
    public EformContentUnavailableException(String message, int missingAssetCount) {
        super(message);
        this.missingAssetCount = missingAssetCount;
    }

    /**
     * @return the number of the eForm's own same-origin critical assets that failed to load
     */
    public int getMissingAssetCount() {
        return missingAssetCount;
    }
}
