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
package io.github.carlos_emr.carlos.appt.location;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/**
 * The style rules for an appointment location: which icons and colours may be stored, and how a
 * location chip is drawn from them.
 *
 * <p>Both values end up in markup on the schedule and the booking screens: the icon in a
 * {@code class} attribute and the colour in a {@code style} attribute. Only whitelisted icons and
 * {@code #rrggbb} colours are ever stored, and the drawing helpers fall back to the defaults for
 * anything else, so a value written to the database by other means still cannot inject markup.</p>
 *
 * <p>Icons are Font Awesome 6 Free solid classes, which CARLOS already bundles
 * ({@code css/fontawesome-all.min.css}); {@code AppointmentLocationStyleUnitTest} checks every
 * entry against that stylesheet.</p>
 *
 * @since 2026-11-04
 */
public final class AppointmentLocationStyle {

    /** The colour of a chip whose location has none. */
    public static final String DEFAULT_COLOUR = "#6c757d";

    /** The icon of a chip whose location has none. */
    public static final String DEFAULT_ICON = "fa-location-dot";

    /** The icons a location may use, in the order the settings page offers them. */
    public static final List<String> ICONS = List.of(
            DEFAULT_ICON, "fa-map-pin", "fa-house", "fa-building", "fa-hospital", "fa-house-medical",
            "fa-door-open", "fa-video", "fa-phone", "fa-laptop-medical", "fa-stethoscope", "fa-user-doctor",
            "fa-user-nurse", "fa-people-group", "fa-person-walking", "fa-car", "fa-truck-medical",
            "fa-wheelchair", "fa-baby", "fa-syringe", "fa-vial", "fa-flask", "fa-tooth", "fa-eye", "fa-brain",
            "fa-heart-pulse", "fa-bed-pulse", "fa-kit-medical", "fa-notes-medical", "fa-briefcase-medical",
            "fa-school", "fa-comments", "fa-star", "fa-circle");

    private static final Pattern COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");

    private AppointmentLocationStyle() {
    }

    /**
     * @param colour String a candidate colour
     * @return boolean true if it is exactly {@code #rrggbb}
     */
    public static boolean isValidColour(String colour) {
        return colour != null && COLOUR.matcher(colour).matches();
    }

    /**
     * @param icon String a candidate icon class
     * @return boolean true if it is one of {@link #ICONS}
     */
    public static boolean isValidIcon(String icon) {
        return icon != null && ICONS.contains(icon);
    }

    /**
     * Normalises a posted colour for storage.
     *
     * @param colour String the posted colour, or blank to clear it
     * @return String the lower-case {@code #rrggbb} colour, or null for blank
     * @throws IllegalArgumentException if it is neither blank nor {@code #rrggbb}
     */
    // FindSecBugs IMPROPER_UNICODE: lower-cases an already validated ASCII #rrggbb colour for storage; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "lower-cases an already validated ASCII #rrggbb colour for storage; not a security or authorization decision")
    public static String normaliseColour(String colour) {
        if (colour == null || colour.isBlank()) {
            return null;
        }
        String value = colour.trim();
        if (!isValidColour(value)) {
            throw new IllegalArgumentException("not a #rrggbb colour");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Normalises a posted icon for storage.
     *
     * @param icon String the posted icon class, or blank to clear it
     * @return String the icon class, or null for blank
     * @throws IllegalArgumentException if it is neither blank nor one of {@link #ICONS}
     */
    public static String normaliseIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }
        String value = icon.trim();
        if (!isValidIcon(value)) {
            throw new IllegalArgumentException("not a whitelisted icon");
        }
        return value;
    }

    /**
     * The colour a chip is drawn in.
     *
     * @param item LookupListItem the location
     * @return String its {@code #rrggbb} colour, or {@link #DEFAULT_COLOUR} when it has none or a
     *         stored value is not {@code #rrggbb}
     */
    public static String chipColour(LookupListItem item) {
        return item != null && isValidColour(item.getColour()) ? item.getColour() : DEFAULT_COLOUR;
    }

    /**
     * The icon a chip is drawn with.
     *
     * @param item LookupListItem the location
     * @return String its icon class, or {@link #DEFAULT_ICON} when it has none or a stored value is
     *         not whitelisted
     */
    public static String chipIcon(LookupListItem item) {
        return item != null && isValidIcon(item.getIcon()) ? item.getIcon() : DEFAULT_ICON;
    }

    /**
     * The text colour that stays readable on a chip's background: black on light colours, white on
     * dark ones, by the WCAG relative luminance of the background.
     *
     * @param item LookupListItem the location
     * @return String {@code #000000} or {@code #ffffff}
     */
    public static String chipTextColour(LookupListItem item) {
        String colour = chipColour(item);
        double luminance = 0.2126 * channel(colour, 1) + 0.7152 * channel(colour, 3) + 0.0722 * channel(colour, 5);
        // 0.179 is where black and white text have equal contrast against the background.
        return luminance > 0.179 ? "#000000" : "#ffffff";
    }

    private static double channel(String colour, int offset) {
        double value = Integer.parseInt(colour.substring(offset, offset + 2), 16) / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
