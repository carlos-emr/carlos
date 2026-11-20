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

import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;

/**
 * Signals that an eForm browser render is potentially incomplete because required content could
 * not be loaded or represented. This includes resources such as images, stylesheets, scripts, and
 * data requests, as well as detected signature, layout, or timer-compatibility failures.
 *
 * <p>Unlike a plain {@link PDFGenerationException}, this failure is <strong>user-recoverable</strong>:
 * the page itself loaded and the rest of the form rendered, so a caller may re-issue the render with
 * a server-issued exact approval capability after prompting the clinician.
 * It is deliberately distinct from hard failures that are <em>not</em> user-overridable — a main
 * document that never loaded (nothing to render) or an attempted live egress channel (a security
 * signal) — so the web layer can offer the override for this case alone.</p>
 *
 * <p>{@link #getIssueCount()} reports only a sanitized aggregate count; resource URLs and names
 * are never exposed because they can carry PHI.</p>
 */
public class EformContentUnavailableException extends PDFGenerationException {

    private static final long serialVersionUID = 1L;

    private final int fdid;
    private final EFormRenderCompletenessReport report;
    private final java.util.List<String> severeConsoleDetails;

    /**
     * @param message the detail message (must not embed asset URLs/names — count only)
     * @param report sanitized categories and counts describing the incomplete render
     */
    public EformContentUnavailableException(
            String message, int fdid, EFormRenderCompletenessReport report) {
        this(message, fdid, report, java.util.List.of());
    }

    /**
     * @param severeConsoleDetails PHI-safe per-error descriptions (type + line:col only) for the
     *     informed-override screen. NOT part of the completeness report and NOT bound into the
     *     approval digest — the digest is over the report's counts; these are display only.
     */
    public EformContentUnavailableException(
            String message, int fdid, EFormRenderCompletenessReport report,
            java.util.List<String> severeConsoleDetails) {
        super(message);
        this.fdid = fdid;
        this.report = java.util.Objects.requireNonNull(report, "report must not be null");
        this.severeConsoleDetails = java.util.List.copyOf(
                java.util.Objects.requireNonNullElse(severeConsoleDetails, java.util.List.of()));
    }

    public int getFdid() {
        return fdid;
    }

    public int getIssueCount() {
        return report.issueCount();
    }

    public EFormRenderCompletenessReport getReport() {
        return report;
    }

    /**
     * PHI-safe one-line descriptions of the severe page-script errors (type + source line:col),
     * for display on the informed-override screen. Empty unless severe console errors were present.
     */
    public java.util.List<String> getSevereConsoleDetails() {
        return severeConsoleDetails;
    }
}
