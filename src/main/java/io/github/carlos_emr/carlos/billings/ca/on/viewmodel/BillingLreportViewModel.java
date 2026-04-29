/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

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
public final class BillingLreportViewModel {

    private final String fileContents;
    private final String xslName;
    private final String filename;

    private BillingLreportViewModel(Builder b) {
        this.fileContents = nullToEmpty(b.fileContents);
        this.xslName = nullToEmpty(b.xslName);
        this.filename = nullToEmpty(b.filename);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

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

        public BillingLreportViewModel build() { return new BillingLreportViewModel(this); }
    }
}
