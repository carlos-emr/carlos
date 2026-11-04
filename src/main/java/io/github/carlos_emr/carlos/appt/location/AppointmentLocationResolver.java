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

import java.util.function.BooleanSupplier;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Resolves an appointment's location from what a booking form posted, and loads the Location List
 * the booking screens offer (issue #3982).
 *
 * <p><b>The one write rule.</b> An appointment's {@code locationCode} (a Location List item id) is
 * only ever saved together with that item's label as the {@code location} text, and is cleared
 * whenever the text changes for any other reason. Every booking-form writer goes through
 * {@link #applyPostedLocation(Appointment, HttpServletRequest)}; every integration (SOAP, REST,
 * HL7) only sets text and goes through {@link #setLocationText(Appointment, String)}, so a code
 * never outlives its label. Renaming an item later does not rewrite past appointments: they keep
 * the label they were booked with.</p>
 *
 * <p>Reading the list needs no privilege: every user who can book an appointment sees it.</p>
 *
 * @since 2026-11-04
 */
@Service
public class AppointmentLocationResolver {

    /** The {@code LookupList.name} of the Location List, seeded by Flyway {@code V1.0.81}. */
    public static final String LIST_NAME = "appointmentLocationCode";

    /** The request parameter a booking form's location dropdown posts. */
    public static final String LOCATION_CODE_PARAM = "locationCode";

    /** The request parameter that carries the location text. */
    public static final String LOCATION_PARAM = "location";

    /**
     * Width of {@code appointment.location} (Flyway {@code V1.0.8}), which holds the label snapshot.
     * Location labels are kept to this width, so the snapshot is never cut.
     */
    public static final int LABEL_MAX_LENGTH = 255;

    private final LookupListDao lookupListDao;
    private final BooleanSupplier siteSetup;

    @Autowired
    public AppointmentLocationResolver(LookupListDao lookupListDao) {
        this(lookupListDao, AppointmentLocationResolver::isSiteSetupConfigured);
    }

    /* Tests supply the site-setup answer instead of reading the deployment properties. */
    AppointmentLocationResolver(LookupListDao lookupListDao, BooleanSupplier siteSetup) {
        this.lookupListDao = lookupListDao;
        this.siteSetup = siteSetup;
    }

    /**
     * Loads the Location List as one page render or one save sees it.
     *
     * @return AppointmentLocationList the list, empty if the lookup list is missing
     */
    public AppointmentLocationList load() {
        LookupList list = lookupListDao.findByName(LIST_NAME);
        return new AppointmentLocationList(list == null ? null : list.getItems(), siteSetup.getAsBoolean());
    }

    /**
     * Saves the location a booking form posted.
     *
     * <ul>
     *   <li>No {@code locationCode}: the form offered sites, programs or free text, so the posted
     *       text is saved through {@link #setLocationText}, which keeps the code only while the text
     *       is unchanged (as when the list was switched off after the booking was made).</li>
     *   <li>{@link AppointmentLocationList#LEGACY_VALUE}: the code is cleared and the posted text,
     *       the legacy location the edit screen offered, is kept.</li>
     *   <li>The appointment's own code: nothing changes. Its saved label stays even if the item
     *       has been renamed or deactivated since, so saving an edit unchanged never rewrites a
     *       past booking.</li>
     *   <li>Another active item, while the list is in location mode: its id becomes the code and
     *       its label the text.</li>
     *   <li>Blank ("Not specified") or not a number: in location mode the code and the text are
     *       cleared. Outside it no dropdown was offered (the patient-search pages pass a blank code
     *       through), so the text is saved through {@link #setLocationText}.</li>
     *   <li>Any other id: a deactivated item, an id of another lookup list, or an item while the
     *       list is not in location mode. Only a hand-made request, or a form opened before the
     *       item was disabled, can post one; it is refused and the saved location is left as it
     *       is, so a disabled location is refused on the server too.</li>
     * </ul>
     *
     * @param appointment Appointment the appointment about to be saved, holding its saved location
     * @param postedCode String the posted {@code locationCode}, or null when the form had none
     * @param postedText String the posted {@code location} text, or null
     */
    public void applyPostedLocation(Appointment appointment, String postedCode, String postedText) {
        if (postedCode == null) {
            setLocationText(appointment, postedText);
            return;
        }
        if (AppointmentLocationList.LEGACY_VALUE.equals(postedCode.trim())) {
            appointment.setLocationCode(null);
            appointment.setLocation(postedText);
            return;
        }
        Integer chosen = AppointmentLocationList.parseCode(postedCode);
        if (chosen != null && chosen.equals(appointment.getLocationCode())) {
            return;
        }
        AppointmentLocationList locations = load();
        if (chosen == null) {
            if (locations.isLocationMode()) {
                appointment.setLocationCode(null);
                appointment.setLocation("");
            } else {
                setLocationText(appointment, postedText);
            }
            return;
        }
        LookupListItem item = locations.isLocationMode() ? locations.find(chosen) : null;
        if (item == null || !item.isActive()) {
            if (appointment.getLocation() == null) {
                appointment.setLocation("");
            }
            return;
        }
        appointment.setLocationCode(item.getId());
        appointment.setLocation(StringUtils.left(item.getLabel(), LABEL_MAX_LENGTH));
    }

    /**
     * Saves the location a booking form posted, for the request-scoped writers (Struts actions and
     * the legacy record JSPs). A form that posts no {@code locationCode} never loads the list.
     *
     * @param appointment Appointment the appointment about to be saved
     * @param request HttpServletRequest the booking form's request
     */
    public static void applyPostedLocation(Appointment appointment, HttpServletRequest request) {
        String postedCode = request.getParameter(LOCATION_CODE_PARAM);
        String postedText = request.getParameter(LOCATION_PARAM);
        if (postedCode == null) {
            setLocationText(appointment, postedText);
            return;
        }
        SpringUtils.getBean(AppointmentLocationResolver.class).applyPostedLocation(appointment, postedCode, postedText);
    }

    /**
     * Saves location text that was not chosen from the Location List: typed on a booking form or
     * sent by an integration. The code is kept while the text is unchanged (null and blank count
     * as the same) and cleared once it changes, because the new text no longer names that item.
     *
     * @param appointment Appointment the appointment about to be saved
     * @param text String the new location text, or null
     */
    public static void setLocationText(Appointment appointment, String text) {
        if (!StringUtils.defaultString(appointment.getLocation()).equals(StringUtils.defaultString(text))) {
            appointment.setLocationCode(null);
        }
        appointment.setLocation(text);
    }

    /**
     * Whether a location resolved from a booking form says the same as the saved one: the same
     * text and code, or the saved typed text linked to the active item with that name, which the
     * edit screen offers in its place (see {@link AppointmentLocationList#choiceFor}).
     *
     * @param saved Appointment the saved appointment
     * @param resolved Appointment the same appointment with the posted location applied
     * @return boolean true if saving the form would not move the appointment to another location
     */
    public static boolean isSameLocation(Appointment saved, Appointment resolved) {
        String savedText = StringUtils.defaultString(saved.getLocation());
        if (savedText.equals(StringUtils.defaultString(resolved.getLocation()))
                && java.util.Objects.equals(saved.getLocationCode(), resolved.getLocationCode())) {
            return true;
        }
        String savedName = AppointmentLocationList.normaliseName(savedText);
        return saved.getLocationCode() == null && resolved.getLocationCode() != null && !savedName.isEmpty()
                && savedName.equals(AppointmentLocationList.normaliseName(resolved.getLocation()));
    }

    /**
     * Whether the clinic books by a site setup, which the booking screens offer before the
     * Location List: multisite sites, a schedule site ({@code scheduleSiteID}), or CAISI program
     * locations ({@code ModuleNames} containing Caisi with {@code useProgramLocation=true}). These
     * are the same switches that pick the booking screens' location control.
     *
     * @return boolean true if a site setup is configured
     */
    public static boolean isSiteSetupConfigured() {
        if (IsPropertiesOn.isMultisitesEnable()) {
            return true;
        }
        CarlosProperties properties = CarlosProperties.getInstance();
        if (StringUtils.isNotEmpty(properties.getProperty("scheduleSiteID", ""))) {
            return true;
        }
        return StringUtils.containsIgnoreCase(properties.getProperty("ModuleNames", ""), "Caisi")
                && "true".equals(properties.getProperty("useProgramLocation", ""));
    }
}
