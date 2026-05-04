/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

/**
 * Immutable view model for {@code billingLreport.jsp}, the Ontario MOH
 * report renderer that XSL-transforms an EDT response file into HTML.
 *
 * <p>Captures the three pieces the legacy JSP body computed inline:</p>
 * <ul>
 *   <li>{@code fileContents} — the UTF-8 contents of the response file in
 *       {@code ONEDT_INBOX}, or empty when the filename was unsafe (the
 *       legacy guard rejected names containing {@code ".."}).</li>
 *   <li>{@code xslName} — {@code "OU"} or {@code "ES"} chosen by the third
 *       /fourth character of the filename (legacy heuristic).</li>
 *   <li>{@code filename} — the user-supplied filename echoed into the
 *       page for diagnostic / display use.</li>
 * </ul>
 *
 * @since 2026-04-25
 */
public final class BillingLegacyReportViewModel {

    private final String fileContents;
    private final String xslName;
    private final String filename;

    private BillingLegacyReportViewModel(Builder b) {
        this.fileContents = BillingViewStrings.nullToEmpty(b.fileContents);
        this.xslName = BillingViewStrings.nullToEmpty(b.xslName);
        this.filename = BillingViewStrings.nullToEmpty(b.filename);
    }

    public static Builder builder() { return new Builder(); }

    public String getFileContents() { return fileContents; }
    public String getXslName() { return xslName; }
    public String getFilename() { return filename; }

    public static final class Builder {
        private String fileContents;
        private String xslName;
        private String filename;

        public Builder fileContents(String v) { this.fileContents = v; return this; }
        public Builder xslName(String v) { this.xslName = v; return this; }
        public Builder filename(String v) { this.filename = v; return this; }

        public BillingLegacyReportViewModel build() { return new BillingLegacyReportViewModel(this); }
    }
}
