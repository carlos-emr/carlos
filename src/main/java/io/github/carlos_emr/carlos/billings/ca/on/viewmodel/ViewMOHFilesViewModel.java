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

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billing/CA/ON/viewMOHFiles.jsp}, the
 * MOH (Ministry of Health) EDT-folder file listing page used by the
 * Ontario billing administration screens.
 *
 * <p>The legacy JSP scriptlet performed:</p>
 * <ul>
 *   <li>Direct {@code File.listFiles()} on each {@code EDTFolder} path</li>
 *   <li>Inline {@code zip.unzipXML} call when the {@code unzipfile} param was set</li>
 *   <li>Inline {@code SafeEncode}/{@code URLEncoder} composition of {@code <a>} HTML</li>
 *   <li>Direct {@code session.setAttribute("backupfilepath", ...)}</li>
 *   <li>Direct lookup of the {@code project_home} carlos property for the action URL</li>
 * </ul>
 *
 * <p>All of those are now handled by
 * {@link io.github.carlos_emr.carlos.billing.CA.ON.web.MoveMOHFiles2Action}
 * and exposed as request attribute {@code mohModel} so the JSP body can
 * be pure EL/JSTL.</p>
 *
 * @since 2026-04-25
 */
public final class ViewMOHFilesViewModel {

    /** Folder names — match {@link io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder} values. */
    public static final String INBOX = "INBOX";
    public static final String OUTBOX = "OUTBOX";
    public static final String SENT = "SENT";
    public static final String ARCHIVE = "ARCHIVE";

    /** One row in the file-list table. */
    public record FileEntry(
            String displayName,
            String urlEncodedName,
            String date,
            String unzipMessage) { }

    private final String selectedFolder;
    private final boolean isInbox;
    private final boolean isArchive;
    private final boolean isOutbox;
    private final boolean isSent;
    private final boolean providesAccessToFiles;
    private final List<FileEntry> files;
    private final String projectHome;
    private final String unzipMessage;

    private ViewMOHFilesViewModel(Builder b) {
        this.selectedFolder = b.selectedFolder == null ? INBOX : b.selectedFolder;
        this.isInbox = INBOX.equals(this.selectedFolder);
        this.isArchive = ARCHIVE.equals(this.selectedFolder);
        this.isOutbox = OUTBOX.equals(this.selectedFolder);
        this.isSent = SENT.equals(this.selectedFolder);
        this.providesAccessToFiles = this.isInbox || this.isArchive;
        this.files = b.files == null ? Collections.emptyList() : List.copyOf(b.files);
        this.projectHome = b.projectHome == null ? "" : b.projectHome;
        this.unzipMessage = b.unzipMessage == null ? "" : b.unzipMessage;
    }

    public static Builder builder() { return new Builder(); }

    public String getSelectedFolder() { return selectedFolder; }
    public boolean isInbox() { return isInbox; }
    public boolean isArchive() { return isArchive; }
    public boolean isOutbox() { return isOutbox; }
    public boolean isSent() { return isSent; }
    public boolean isProvidesAccessToFiles() { return providesAccessToFiles; }
    public List<FileEntry> getFiles() { return files; }
    public String getProjectHome() { return projectHome; }
    public String getUnzipMessage() { return unzipMessage; }

    public static final class Builder {
        private String selectedFolder;
        private List<FileEntry> files;
        private String projectHome;
        private String unzipMessage;

        public Builder selectedFolder(String v) { this.selectedFolder = v; return this; }
        public Builder files(List<FileEntry> v) { this.files = v == null ? null : List.copyOf(v); return this; }
        public Builder projectHome(String v) { this.projectHome = v; return this; }
        public Builder unzipMessage(String v) { this.unzipMessage = v; return this; }

        public ViewMOHFilesViewModel build() { return new ViewMOHFilesViewModel(this); }
    }
}
