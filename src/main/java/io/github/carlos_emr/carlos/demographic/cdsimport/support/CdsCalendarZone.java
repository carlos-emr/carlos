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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;

import java.time.ZoneId;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/** Finite evidence for the actual legacy Calendar rules, independently of an ID-derived ZoneId. */
public final class CdsCalendarZone {
    private static final Class<?> JDK_ZONE = TimeZone.getTimeZone("UTC").getClass();

    private CdsCalendarZone() {}

    public static String evidence(TimeZone zone) {
        requireTrusted(zone);
        String id = zone.getID();
        if (id == null || id.length() > 128) throw unsupported();
        var digest = CdsImportKeys.digest();
        CdsImportKeys.add(digest, zone.getClass().getName());
        CdsImportKeys.add(digest, id);
        CdsImportKeys.add(digest, Integer.toString(zone.getRawOffset()));
        CdsImportKeys.add(digest, Integer.toString(zone.getDSTSavings()));
        CdsImportKeys.add(digest, Boolean.toString(zone.useDaylightTime()));
        CdsImportKeys.add(digest, Integer.toString(zone.hashCode()));
        if (zone.getClass() == SimpleTimeZone.class) {
            // The frozen JDK implementation exposes every SimpleTimeZone transition-rule
            // field in this bounded representation; no reflection or object graph is read.
            String rules = zone.toString();
            if (rules.length() > 4096) throw unsupported();
            CdsImportKeys.add(digest, rules);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    /** Rejects a same-ID Calendar override before any import uses native Date/Calendar consumers. */
    public static ZoneId requireCurrentCalendarRules() {
        TimeZone actual = TimeZone.getDefault();
        requireTrusted(actual);
        final ZoneId selected;
        try {
            selected = actual.toZoneId();
        } catch (java.time.DateTimeException invalid) {
            throw new CdsImportException("IMPORT_RUNTIME_ZONE_MISMATCH");
        }
        TimeZone canonical = TimeZone.getTimeZone(selected);
        boolean matches;
        if (actual.getClass() == SimpleTimeZone.class) {
            matches =
                    !actual.useDaylightTime()
                            && selected.getRules().isFixedOffset()
                            && actual.getRawOffset() == canonical.getRawOffset();
        } else {
            matches = actual.hasSameRules(canonical) && canonical.hasSameRules(actual);
        }
        if (!matches) throw new CdsImportException("IMPORT_RUNTIME_ZONE_MISMATCH");
        return selected;
    }

    public static void requireTrusted(TimeZone zone) {
        if (zone == null
                || (zone.getClass() != JDK_ZONE && zone.getClass() != SimpleTimeZone.class))
            throw unsupported();
    }

    private static CdsImportException unsupported() {
        return new CdsImportException("IMPORT_RUNTIME_TIME_ZONE_UNSUPPORTED");
    }
}
