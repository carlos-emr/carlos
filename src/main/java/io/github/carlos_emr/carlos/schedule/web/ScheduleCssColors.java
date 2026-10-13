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
package io.github.carlos_emr.carlos.schedule.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a configured schedule colour into a value that is safe to write into a CSS {@code background-color}.
 * <p>
 * Schedule template codes and sites store the colour exactly as typed. The ON and BC seed data use bare hex such as
 * {@code EED2EE} and {@code FFF68F}; the old {@code bgcolor} attribute accepted that, but CSS does not, so bare hex
 * gets its {@code #}. Anything that is neither hex nor a plain colour name is refused, which keeps CSS syntax out of
 * the style attribute it is written into.
 *
 * @since 2026-09-28
 */
public final class ScheduleCssColors {
    private static final Pattern HEX = Pattern.compile(
            "#?([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern NAME = Pattern.compile("[a-zA-Z]+");

    private ScheduleCssColors() {
    }

    /**
     * @param configuredColor the stored colour, as typed in the schedule template code or site settings
     * @return the colour as CSS (hex always with {@code #}), or {@code null} when it is missing or not a safe colour
     */
    public static String safeCssColor(Object configuredColor) {
        if (configuredColor == null) {
            return null;
        }
        String color = configuredColor.toString().trim();
        Matcher hex = HEX.matcher(color);
        if (hex.matches()) {
            return "#" + hex.group(1);
        }
        return NAME.matcher(color).matches() ? color : null;
    }
}
