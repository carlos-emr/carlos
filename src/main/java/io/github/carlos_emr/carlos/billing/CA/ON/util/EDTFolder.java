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
     * Returns the {@link EDTFolder} enum constant whose name matches {@code name}
     * (case-insensitive).
     *
     * @param name String the folder name to look up (e.g., {@code "inbox"}, {@code "OUTBOX"})
     * @return EDTFolder the matching enum constant, or {@code null} if no constant
     *         matches {@code name}
     */
    public static EDTFolder getFolder(String name) {
        for (EDTFolder f : EDTFolder.values()) {
            if (f.name().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    public boolean providesAccessToFiles() {
        return this == INBOX || this == ARCHIVE;
    }
}