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

import io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder;

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
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.MoveMohFiles2Action}
 * and exposed as request attribute {@code mohModel} so the JSP body can
 * be pure EL/JSTL.</p>
 *
 * @since 2026-04-25
 */
public final class ViewMohFilesViewModel {

    /** One row in the file-list table. */
    public record FileEntry(
            String displayName,
            String urlEncodedName,
            String date,
            String unzipMessage) { }

    private final EDTFolder selectedFolder;
    private final List<FileEntry> files;
    private final String projectHome;
    private final String unzipMessage;

    private ViewMohFilesViewModel(Builder b) {
        this.selectedFolder = b.selectedFolder == null ? EDTFolder.INBOX : b.selectedFolder;
        this.files = b.files == null ? Collections.emptyList() : List.copyOf(b.files);
        this.projectHome = b.projectHome == null ? "" : b.projectHome;
        this.unzipMessage = b.unzipMessage == null ? "" : b.unzipMessage;
    }

    public static Builder builder() { return new Builder(); }

    /** Folder name in upper case, matching the {@link EDTFolder} enum constant. */
    public String getSelectedFolder() { return selectedFolder.name(); }
    public boolean isInbox() { return selectedFolder == EDTFolder.INBOX; }
    public boolean isArchive() { return selectedFolder == EDTFolder.ARCHIVE; }
    public boolean isOutbox() { return selectedFolder == EDTFolder.OUTBOX; }
    public boolean isSent() { return selectedFolder == EDTFolder.SENT; }
    public boolean isProvidesAccessToFiles() { return selectedFolder.providesAccessToFiles(); }
    public List<FileEntry> getFiles() { return files; }
    public String getProjectHome() { return projectHome; }
    public String getUnzipMessage() { return unzipMessage; }

    public static final class Builder {
        private EDTFolder selectedFolder;
        private List<FileEntry> files;
        private String projectHome;
        private String unzipMessage;

        public Builder selectedFolder(EDTFolder v) { this.selectedFolder = v; return this; }
        public Builder files(List<FileEntry> v) { this.files = v == null ? null : List.copyOf(v); return this; }
        public Builder projectHome(String v) { this.projectHome = v; return this; }
        public Builder unzipMessage(String v) { this.unzipMessage = v; return this; }

        public ViewMohFilesViewModel build() { return new ViewMohFilesViewModel(this); }
    }
}
