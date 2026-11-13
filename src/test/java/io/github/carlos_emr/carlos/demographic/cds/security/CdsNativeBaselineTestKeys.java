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
package io.github.carlos_emr.carlos.demographic.cds.security;

import java.util.Map;

/** Fixed synthetic native-baseline keys for tests; never a production key. */
public final class CdsNativeBaselineTestKeys {
    public static final String ACTIVE = "test-active";
    public static final String RETIRED = "test-retired";

    private CdsNativeBaselineTestKeys() {}

    public static CdsNativeBaselineKeys keys() {
        return CdsNativeBaselineKeys.of(ACTIVE, Map.of(ACTIVE, bytes(1), RETIRED, bytes(2)));
    }

    /** Keys whose active key is the former retired one, to prove rotation reads old witnesses. */
    public static CdsNativeBaselineKeys rotated() {
        return CdsNativeBaselineKeys.of(RETIRED, Map.of(ACTIVE, bytes(1), RETIRED, bytes(2)));
    }

    public static CdsNativeBaselineKeys none() {
        return CdsNativeBaselineKeys.of(null, Map.of());
    }

    private static byte[] bytes(int seed) {
        byte[] value = new byte[32];
        for (int i = 0; i < value.length; i++) value[i] = (byte) (seed * 31 + i);
        return value;
    }
}
