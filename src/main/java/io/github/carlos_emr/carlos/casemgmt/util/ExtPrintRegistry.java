/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.casemgmt.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded process-wide registration of chart print extensions. Existing registrations are
 * never silently evicted; callers receive an explicit failure when capacity is exhausted.
 *
 * @since 2019-10-11
 */
public class ExtPrintRegistry {
    static final int MAX_ENTRIES = 128;
    static final int MAX_NAME_LENGTH = 256;
    private static final Map<String, String> entries = new LinkedHashMap<>();

    /**
     * Registers or updates an extension atomically.
     *
     * @param name extension display name, nonblank and at most 256 characters
     * @param beanName Spring bean name, nonblank and at most 256 characters
     * @throws IllegalArgumentException if either name is absent or too long
     * @throws IllegalStateException if a new registration would exceed capacity
     */
    public static synchronized void addEntry(String name, String beanName) {
        validateName(name);
        validateName(beanName);
        if (!entries.containsKey(name) && entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Print extension registry is full");
        }
        entries.put(name, beanName);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Print extension names must contain 1 to 256 characters");
        }
    }

    /**
     * Returns a stable snapshot that cannot bypass registry validation or capacity limits.
     *
     * @return immutable registrations in insertion order
     */
    public static synchronized Map<String, String> getEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * Looks up a registered extension.
     *
     * @param name extension display name
     * @return bean name, or null when unregistered
     */
    public static synchronized String getEntry(String name) {
        return entries.get(name);
    }
}
