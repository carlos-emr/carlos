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
package io.github.carlos_emr.carlos.encounter.display;

/**
 * The per-provider eChart display settings, in the order the Preferences page lists them.
 *
 * <p>OSCAR 19 offered these as clinic-wide toggles on Administration &gt; eChart &gt; eChart
 * Display Settings ({@code SystemPreferences.ECHART_PREFERENCE_KEYS}). CARLOS makes them a
 * personal choice: each provider sets them in the "eChart" section of their Preferences, and the
 * value lives in the {@code property} table under {@link #propertyName()} for that provider.
 * The property names deliberately reuse OSCAR 19's key names so a clinic migrating from OSCAR
 * recognizes them; the OLIS link key is out of scope and is not carried over. {@link #PHONE_TEL_LINKS}
 * is CARLOS's own addition (issue #852) and has no OSCAR 19 key.</p>
 *
 * <p>A provider who has never saved the section gets {@link #defaultValue()}: everything on
 * except click-to-dial phone links, the Referring Doctor widget and the Ocean toolbar, which a
 * provider has to opt in to. Values are stored as {@code "true"}/{@code "false"}, matching OSCAR 19's
 * radio values.</p>
 *
 * @since 2026-10-16
 */
public enum EchartDisplayPreference {

    /** The encounter timer button (pastes start/end/elapsed time into the note). */
    SHOW_TIMER("echart_show_timer", true),

    /** File the left-nav Documents list into one folder per document type. */
    GROUP_DOCUMENTS_BY_TYPE("echart_show_group_document_by_type", true),

    /** Show the date of birth (yyyy-mm-dd) in the header instead of the age. */
    SHOW_DOB("echart_show_DOB", true),

    /** Prefer the cell phone number over the home number in the header when one is on file. */
    SHOW_CELL("echart_show_cell", true),

    /**
     * Render the header's phone numbers as {@code tel:} links, so a click dials through the
     * workstation's softphone or calling app instead of copying the number. Not in OSCAR 19.
     */
    PHONE_TEL_LINKS("echart_phone_tel_links", false),

    /** Show the health insurance number in the header. */
    SHOW_HIN("echart_show_HIN", true),

    /** Show the patient's email address in the header when one is on file. */
    SHOW_EMAIL("echart_email_indicator", true),

    /** Family Doctor panel at the top of the left navigation column. */
    SHOW_FAMILY_DOCTOR("echart_show_fam_doc_widget", true),

    /** Referring Doctor panel at the top of the left navigation column. */
    SHOW_REFERRING_DOCTOR("echart_show_ref_doc_widget", false),

    /**
     * The Ocean toolbar placeholder. The clinic-wide {@code echart_show_ocean} system preference
     * (Administration &gt; eChart Display Settings) and the {@code cme_js=ocean} server property still
     * have to allow Ocean; this setting only opts the provider in.
     */
    SHOW_OCEAN("echart_show_ocean", false);

    private static final String LABEL_KEY_PREFIX = "provider.providerpreference.echart.";

    private final String propertyName;
    private final boolean defaultValue;

    EchartDisplayPreference(String propertyName, boolean defaultValue) {
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
    }

    /**
     * @return the {@code property.name} the setting is stored under; also the form field name on
     *         the Preferences page
     */
    public String propertyName() {
        return propertyName;
    }

    /** @return the value a provider gets when they have never saved the setting */
    public boolean defaultValue() {
        return defaultValue;
    }

    /** @return the {@code oscarResources} key of the setting's label on the Preferences page */
    public String labelKey() {
        return LABEL_KEY_PREFIX + propertyName;
    }

    /**
     * Interprets a stored value. Anything other than {@code "true"}/{@code "false"} (surrounding
     * blanks ignored), including no row at all, reads as the default rather than as "off",
     * so a hand-edited or legacy value cannot silently hide part of the chart.
     *
     * @param storedValue the {@code property.value}, or {@code null} when there is no row
     * @return the effective setting
     */
    public boolean resolve(String storedValue) {
        Boolean parsed = parse(storedValue);
        return parsed != null ? parsed : defaultValue;
    }

    /**
     * Parses a submitted or stored value.
     *
     * @param value the raw value
     * @return {@code TRUE}/{@code FALSE} for exactly {@code "true"}/{@code "false"} (surrounding blanks
     *         ignored; these are the only values the Preferences page writes), otherwise {@code null}
     */
    public static Boolean parse(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }
}
