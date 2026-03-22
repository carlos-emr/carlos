/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billing.CA.ON.util;

import io.github.carlos_emr.CarlosProperties;

/**
 * Enumeration of Electronic Data Transfer (EDT) folder types for Ontario billing.
 * Each folder maps to a configured filesystem path via the {@code ONEDT_} property prefix.
 * Used for organizing MCEDT (Medical Claims EDT) file submissions and responses.
 *
 * @since 2026-03-17
 */
public enum EDTFolder {
    INBOX, OUTBOX, SENT, ARCHIVE;
    String path;

    private EDTFolder() {
        this.path = CarlosProperties.getInstance().getProperty("ONEDT_" + name());
    }

    public String getPath() {
        return path;
    }

    /**
     * Looks up an EDT folder by name (case-insensitive). Defaults to INBOX if not found.
     *
     * @param name String the folder name to look up
     * @return EDTFolder the matching folder, or INBOX if no match
     */
    public static EDTFolder getFolder(String name) {
        for (EDTFolder f : EDTFolder.values()) {
            if (f.name().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return INBOX;
    }

    /**
     * Checks whether this folder type provides access to downloadable files.
     * Only INBOX and ARCHIVE folders support file access.
     *
     * @return boolean true if files can be accessed from this folder
     */
    public boolean providesAccessToFiles() {
        return this == INBOX || this == ARCHIVE;
    }
}