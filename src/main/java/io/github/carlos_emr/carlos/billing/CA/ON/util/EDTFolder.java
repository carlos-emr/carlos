/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.billing.CA.ON.util;

import io.github.carlos_emr.OscarProperties;

public enum EDTFolder {
    INBOX, OUTBOX, SENT, ARCHIVE;
    String path;

    private EDTFolder() {
        this.path = OscarProperties.getInstance().getProperty("ONEDT_" + name());
    }

    public String getPath() {
        return path;
    }

    public static EDTFolder getFolder(String name) {
        for (EDTFolder f : EDTFolder.values()) {
            if (f.name().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return INBOX;
    }

    public boolean providesAccessToFiles() {
        return this == INBOX || this == ARCHIVE;
    }
}