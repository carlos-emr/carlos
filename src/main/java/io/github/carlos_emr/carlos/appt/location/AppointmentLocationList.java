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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/**
 * A read-only snapshot of the appointment Location List, as one page render or one save sees it:
 * the list's items, active or not, and whether the clinic books by a site setup instead.
 *
 * <p>Load it with {@link AppointmentLocationResolver#load()} once per page, not per appointment.
 * The items come from the lookup-list cache and are shared, so nothing here changes them.</p>
 *
 * <p><b>Location mode.</b> The booking screens offer the list in place of the free-text location
 * only when it has an active item <em>and</em> no site setup is configured (see
 * {@link AppointmentLocationResolver#isSiteSetupConfigured()}): multisite sites, schedule sites
 * and CAISI program locations come first and keep their own controls. There is no separate switch
 * to leave half set; disabling the last active location turns the dropdown off again.</p>
 *
 * @since 2026-11-04
 */
public final class AppointmentLocationList {

    /** The dropdown value that keeps a booking's legacy free-text location, which names no item. */
    public static final String LEGACY_VALUE = "legacy";

    /**
     * The choice a booking's location dropdown starts on.
     *
     * @param value String the option to select: blank for "Not specified", {@link #LEGACY_VALUE},
     *              or an item id
     * @param legacy String the legacy free-text location to offer as its own option, or null
     */
    public record Choice(String value, String legacy) {

        /**
         * The choice a re-rendered booking form starts on, from what it posted (a patient search,
         * a refused save). The value is only ever an item id, {@link #LEGACY_VALUE} or blank,
         * never the posted text itself.
         *
         * @param postedCode String the posted {@code locationCode}, or null
         * @param legacy String the legacy free-text location to offer again, or null
         * @return Choice the starting choice
         */
        public static Choice posted(String postedCode, String legacy) {
            if (postedCode != null && LEGACY_VALUE.equals(postedCode.trim())) {
                return new Choice(LEGACY_VALUE, legacy);
            }
            Integer code = parseCode(postedCode);
            return new Choice(code == null ? "" : String.valueOf(code), legacy);
        }

        /**
         * @return Integer the chosen item's id, or null for "Not specified" or the legacy text
         */
        public Integer code() {
            return parseCode(value);
        }
    }

    private static final Comparator<LookupListItem> DISPLAY_ORDER =
            Comparator.comparingInt(LookupListItem::getDisplayOrder)
                    .thenComparing(LookupListItem::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final List<LookupListItem> items;
    private final boolean siteSetup;

    AppointmentLocationList(List<LookupListItem> items, boolean siteSetup) {
        // Display order can repeat (an item added while another is inactive may take its number),
        // so the id breaks ties and every screen lists the locations in the same order.
        this.items = items == null ? List.of() : items.stream().filter(Objects::nonNull).sorted(DISPLAY_ORDER).toList();
        this.siteSetup = siteSetup;
    }

    /**
     * Wraps items that were already loaded, for callers that hold them.
     *
     * @param items List&lt;LookupListItem&gt; the Location List's items, active or not, or null
     * @param siteSetup boolean whether a site setup is configured
     * @return AppointmentLocationList the snapshot
     */
    public static AppointmentLocationList of(List<LookupListItem> items, boolean siteSetup) {
        return new AppointmentLocationList(items, siteSetup);
    }

    /**
     * @return boolean true if the booking screens offer the list's active items
     */
    public boolean isLocationMode() {
        return !siteSetup && items.stream().anyMatch(LookupListItem::isActive);
    }

    /**
     * @return boolean true if the clinic books by multisite sites, schedule sites or CAISI programs
     */
    public boolean isSiteSetup() {
        return siteSetup;
    }

    /**
     * Finds an item of this list, active or not.
     *
     * @param id Integer a LookupListItem id, such as an appointment's location code
     * @return LookupListItem the item, or null if id is null or names no item of this list
     */
    public LookupListItem find(Integer id) {
        if (id == null) {
            return null;
        }
        return items.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
    }

    /**
     * @return List&lt;LookupListItem&gt; the items a new booking may choose, in display order
     */
    public List<LookupListItem> getActiveItems() {
        return items.stream().filter(LookupListItem::isActive).toList();
    }

    /**
     * Every item as the settings page lists them: the active ones in booking order, then the
     * inactive ones by name. An inactive item's stored order means nothing any more.
     *
     * @return List&lt;LookupListItem&gt; the active items in display order, then the inactive by name
     */
    public List<LookupListItem> getItemsActiveFirst() {
        Stream<LookupListItem> inactive = items.stream()
                .filter(item -> !item.isActive())
                .sorted(Comparator.comparing(LookupListItem::getLabel, String.CASE_INSENSITIVE_ORDER));
        return Stream.concat(getActiveItems().stream(), inactive).toList();
    }

    /**
     * The items an edit screen offers a booking that already has a location code: the active
     * items, plus its own item if that has since been deactivated, so saving unchanged keeps it.
     *
     * @param current Integer the booking's location code, or null
     * @return List&lt;LookupListItem&gt; the choices, in display order
     */
    public List<LookupListItem> getChoices(Integer current) {
        LookupListItem currentItem = find(current);
        return items.stream().filter(item -> item.isActive() || item == currentItem).toList();
    }

    /**
     * Works out what an edit screen's dropdown starts on for a saved booking: its item when its
     * code names one; otherwise the active item its text names (ignoring case and surrounding
     * spaces), so a location typed before the list existed is linked on the next save; otherwise
     * its text as a legacy option, selected; or "Not specified" when it has no location.
     *
     * @param code Integer the booking's location code, or null
     * @param location String the booking's location text, or null
     * @return Choice the starting choice
     */
    public Choice choiceFor(Integer code, String location) {
        if (find(code) != null) {
            return new Choice(String.valueOf(code), null);
        }
        LookupListItem named = findActiveByName(location, null);
        if (named != null) {
            return new Choice(String.valueOf(named.getId()), null);
        }
        return StringUtils.isBlank(location) ? new Choice("", null) : new Choice(LEGACY_VALUE, location);
    }

    /**
     * The name a pasted appointment's location is kept under when the booking dropdown cannot
     * select it. A paste (a cut is a move) keeps its location: an active item is selected by its
     * code, so nothing is needed; an item disabled since keeps its name; a location typed before the
     * list existed keeps its text. Either is offered as a legacy option, which saves the name
     * without a code. The add and edit pages both paste through this.
     *
     * <p>A disabled item gives its current name, not the booking's saved one: copy and cut fill the
     * clipboard from the edit form, whose location text is blank for a booking with a code.</p>
     *
     * @param pastedCode String the pasted appointment's location code as stored, or null
     * @param pastedLocation String the pasted appointment's location text, or null
     * @return String the name to offer as a legacy option, or null when there is none to offer
     */
    public String pastedLegacyText(String pastedCode, String pastedLocation) {
        LookupListItem item = find(parseCode(pastedCode));
        if (item != null && item.isActive()) {
            return null;
        }
        String text = item != null ? item.getLabel() : pastedLocation;
        return StringUtils.isBlank(text) ? null : text;
    }

    /**
     * The item whose chip marks an appointment on the schedule. Site setups mark their own
     * location, so a code saved before a clinic switched to one draws no chip.
     *
     * @param appointment Appointment the appointment shown
     * @return LookupListItem its item, active or not, or null for no chip
     */
    public LookupListItem chipItemFor(Appointment appointment) {
        return siteSetup || appointment == null ? null : find(appointment.getLocationCode());
    }

    /**
     * Finds the active item with a name, ignoring case and surrounding spaces. Two active locations
     * with one name could not be told apart in the dropdown or a patient's history.
     *
     * @param name String the name, or null
     * @param exceptId Integer an item to leave out, such as the one being renamed, or null
     * @return LookupListItem the other active item with that name, or null
     */
    public LookupListItem findActiveByName(String name, Integer exceptId) {
        String wanted = normaliseName(name);
        if (wanted.isEmpty()) {
            return null;
        }
        return items.stream()
                .filter(LookupListItem::isActive)
                .filter(item -> !Objects.equals(item.getId(), exceptId))
                .filter(item -> wanted.equals(normaliseName(item.getLabel())))
                .findFirst()
                .orElse(null);
    }

    /**
     * Reads a location code as a booking form posts it.
     *
     * @param posted String the posted {@code locationCode}, or null
     * @return Integer the item id, or null when blank, {@link #LEGACY_VALUE} or not a positive number
     */
    public static Integer parseCode(String posted) {
        if (posted == null) {
            return null;
        }
        try {
            int id = Integer.parseInt(posted.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* Location names match ignoring case and surrounding spaces. */
    // FindSecBugs IMPROPER_UNICODE: case-folds a clinic-chosen location name to compare two names for display uniqueness; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-folds a clinic-chosen location name to compare two names for display uniqueness; not a security or authorization decision")
    static String normaliseName(String name) {
        return StringUtils.trimToEmpty(name).toLowerCase(Locale.ROOT);
    }
}
