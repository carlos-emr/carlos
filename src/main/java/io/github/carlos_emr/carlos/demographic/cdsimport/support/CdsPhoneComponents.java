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

/** Explicit NANP component projection; null means the supplied structure needs source-only review. */
public final class CdsPhoneComponents {
    private CdsPhoneComponents() {}

    public static String combine(String area, String exchange, String number) {
        if (area == null || !area.matches("[0-9]{3}") || number == null) return null;
        String local;
        if (number.matches("[0-9]{7}")) local = number.substring(0, 3) + "-" + number.substring(3);
        else if (number.matches("[0-9]{3}-[0-9]{4}")) local = number;
        else if (number.matches("[0-9]{4}") && exchange != null && exchange.matches("[0-9]{3}"))
            return area + "-" + exchange + "-" + number;
        else return null;
        if (exchange != null
                && !exchange.isEmpty()
                && (!exchange.matches("[0-9]{3}") || !local.startsWith(exchange + "-")))
            return null;
        return area + "-" + local;
    }
}
