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

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.encounter.display.EchartHeaderViewModel.Phone;
import io.github.carlos_emr.carlos.encounter.display.EchartHeaderViewModel.PhoneKind;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the {@link EchartHeaderViewModel} for {@code casemgmt/newEncounterHeader.jsp}: the OSCAR 19
 * style eChart header bar, filtered by the viewer's eChart display settings.
 *
 * <p>The rules follow OSCAR 19's {@code casemgmt/newEncounterHeader.jsp} so a clinic moving from
 * OSCAR sees the same bar:</p>
 * <ul>
 *   <li>The bar takes the viewer's provider colour (Preferences &gt; Set Provider Colour; a light
 *       blue when never chosen), and the MRP's name is underlined in the MRP's own colour.</li>
 *   <li>"Display Date of Birth" shows yyyy-mm-dd with the age as its tooltip; off shows the age with
 *       the date of birth as its tooltip.</li>
 *   <li>One phone number is shown: the cell when "Display Cell Phone Number if Available" is on and a
 *       cell is on file, otherwise the home number, with the other in the tooltip. When the patient has
 *       marked a preferred number (a trailing {@code *}, OSCAR's convention), every marked number is
 *       shown instead and the setting does not apply: the patient's choice wins.</li>
 *   <li>The HIN and email appear only when their settings are on and a value is on file; an email of
 *       a patient who consented to email for care is a mailto link.</li>
 * </ul>
 *
 * <p>Callers must already have authorized the viewer for the chart; this class reads only the
 * provider colour rows and the {@link Demographic} it is handed.</p>
 *
 * @since 2026-10-16
 */
@Service
public class EchartHeaderViewModelAssembler {

    /** OSCAR's default provider colour when a provider never picked one. */
    static final String DEFAULT_PROVIDER_COLOUR = "#CCCCFF";
    static final String PROVIDER_COLOUR_PROPERTY = UserPropertyDAO.COLOR_PROPERTY;

    private static final Pattern HEX_COLOUR = Pattern.compile("#?[0-9A-Fa-f]{6}");
    private static final String PREFERRED_MARK = "*";
    private static final String DARK_TEXT = "#000000";
    private static final String LIGHT_TEXT = "#FFFFFF";
    /**
     * OSCAR 19's eChart link colour (encounterStyles.css). On its own it reaches under 4:1 even on
     * white, so {@link #readableLinkColour} darkens it per provider colour.
     */
    static final String OSCAR_LINK = "#0088CC";
    /** WCAG 2.1 AA contrast for normal-size text; the bar's details are about 12px bold. Text and links meet it. */
    static final double MIN_LINK_CONTRAST = 4.5;

    private final UserPropertyDAO userPropertyDao;

    public EchartHeaderViewModelAssembler(UserPropertyDAO userPropertyDao) {
        this.userPropertyDao = userPropertyDao;
    }

    /**
     * @param viewerProviderNo the logged-in provider, whose colour the bar takes
     * @param demographic      the patient, loaded with its extensions, MRP and next appointment
     *                         ({@code DemographicManager#getDemographicWithExt})
     * @param preferences      the viewer's eChart display settings
     * @param locale           the negotiated display locale, for the age text
     * @return the header bar's content
     */
    public EchartHeaderViewModel assemble(String viewerProviderNo, Demographic demographic,
                                          EchartDisplayPreferences preferences, Locale locale) {
        String background = providerColour(viewerProviderNo);
        String text = readableTextColour(background);

        Provider mrp = demographic.getMrp();
        String mrpName = mrp == null ? ""
                : StringUtils.normalizeSpace(StringUtils.trimToEmpty(mrp.getFirstName()) + " "
                        + StringUtils.trimToEmpty(mrp.getLastName()));
        String mrpUnderline = mrp == null ? "" : providerColour(mrp.getProviderNo());

        String hin = preferences.showHin() ? StringUtils.trimToEmpty(demographic.getHin()) : "";
        String email = preferences.showEmail() ? StringUtils.trimToEmpty(demographic.getEmail()) : "";

        return new EchartHeaderViewModel(
                background,
                text,
                LIGHT_TEXT.equals(text) ? LIGHT_TEXT : readableLinkColour(background),
                mrpName,
                mrpUnderline,
                StringUtils.trimToEmpty(demographic.getFormattedName()),
                StringUtils.trimToEmpty(demographic.getSex()),
                StringUtils.trimToEmpty(demographic.getGender()),
                StringUtils.trimToEmpty(demographic.getPronoun()),
                preferences.showDob(),
                StringUtils.trimToEmpty(demographic.getBirthDayAsString()),
                StringUtils.trimToEmpty(demographic.getAgeAsOf(new Date(), locale)),
                hin,
                hin.isEmpty() ? "" : StringUtils.trimToEmpty(demographic.getVer()),
                selectPhones(demographic.getCellPhone(), demographic.getPhone(), demographic.getPhone2(),
                        preferences.showCell()),
                preferences.phoneTelLinks(),
                email,
                !email.isEmpty() && Boolean.TRUE.equals(demographic.getConsentToUseEmailForCare()),
                StringUtils.trimToEmpty(demographic.getNextAppointment()));
    }

    /**
     * OSCAR 19's phone choice. With no number marked preferred, show one: the cell when the
     * provider prefers it and one is on file, else the home number. With any number marked preferred
     * (trailing {@code *}), show every marked number in cell, home, work order.
     */
    static List<Phone> selectPhones(String cell, String home, String work, boolean preferCell) {
        String cellNumber = StringUtils.trimToEmpty(cell);
        String homeNumber = StringUtils.trimToEmpty(home);
        String workNumber = StringUtils.trimToEmpty(work);
        List<Phone> phones = new ArrayList<>();

        boolean patientMarkedPreference = cellNumber.endsWith(PREFERRED_MARK)
                || homeNumber.endsWith(PREFERRED_MARK) || workNumber.endsWith(PREFERRED_MARK);
        if (!patientMarkedPreference) {
            if (preferCell && !cellNumber.isEmpty()) {
                phones.add(phone(PhoneKind.CELL, cellNumber, PhoneKind.HOME, homeNumber));
            } else if (!homeNumber.isEmpty()) {
                phones.add(phone(PhoneKind.HOME, homeNumber, PhoneKind.CELL, cellNumber));
            }
            return phones;
        }
        if (cellNumber.endsWith(PREFERRED_MARK)) {
            phones.add(phone(PhoneKind.CELL, cellNumber, PhoneKind.HOME, homeNumber));
        }
        if (homeNumber.endsWith(PREFERRED_MARK)) {
            phones.add(phone(PhoneKind.HOME, homeNumber, PhoneKind.CELL, cellNumber));
        }
        if (workNumber.endsWith(PREFERRED_MARK)) {
            phones.add(phone(PhoneKind.WORK, workNumber, PhoneKind.CELL, cellNumber));
        }
        return phones;
    }

    private static Phone phone(PhoneKind kind, String number, PhoneKind alternateKind, String alternateNumber) {
        return alternateNumber.isEmpty()
                ? new Phone(kind, number, null, "")
                : new Phone(kind, number, alternateKind, alternateNumber);
    }

    /** A provider's colour as {@code #RRGGBB}; the OSCAR default when unset or not a plain hex colour. */
    private String providerColour(String providerNo) {
        if (StringUtils.isBlank(providerNo)) {
            return DEFAULT_PROVIDER_COLOUR;
        }
        Map<String, String> stored = userPropertyDao.getProviderPropertiesByNames(providerNo,
                Set.of(PROVIDER_COLOUR_PROPERTY));
        return normalizeColour(stored.get(PROVIDER_COLOUR_PROPERTY));
    }

    /**
     * Accepts only a six-digit hex colour, with or without {@code #}. The value is written into a
     * style attribute, so anything else (named colours included) falls back to the default rather
     * than being passed through.
     */
    static String normalizeColour(String colour) {
        String trimmed = StringUtils.trimToEmpty(colour);
        if (!HEX_COLOUR.matcher(trimmed).matches()) {
            return DEFAULT_PROVIDER_COLOUR;
        }
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    /**
     * Black or white, whichever has the higher WCAG 2.1 contrast with the background. The better of the
     * two is never under 4.58:1 (they tie at about 4.58:1), so text, and white links, always meet
     * {@value #MIN_LINK_CONTRAST}:1, saturated provider colours such as {@code #FF00FF} included.
     */
    static String readableTextColour(String hexColour) {
        double backgroundLuminance = relativeLuminance(normalizeColour(hexColour));
        return contrast(0.0, backgroundLuminance) >= contrast(1.0, backgroundLuminance) ? DARK_TEXT : LIGHT_TEXT;
    }

    /**
     * Link colour for a bar that takes black text: OSCAR 19's blue, darkened (same hue) only as far as
     * this background needs for {@value #MIN_LINK_CONTRAST}:1, so links keep OSCAR's look and stay
     * readable on every provider colour. Falls back to black, the most a light background allows.
     */
    static String readableLinkColour(String background) {
        double backgroundLuminance = relativeLuminance(normalizeColour(background));
        int[] oscar = rgb(OSCAR_LINK);
        for (int percent = 100; percent > 0; percent--) {
            String candidate = String.format(Locale.ROOT, "#%02X%02X%02X", oscar[0] * percent / 100,
                    oscar[1] * percent / 100, oscar[2] * percent / 100);
            if (contrast(relativeLuminance(candidate), backgroundLuminance) >= MIN_LINK_CONTRAST) {
                return candidate;
            }
        }
        return DARK_TEXT;
    }

    /** WCAG 2.1 contrast ratio between two relative luminances. */
    private static double contrast(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    /** WCAG 2.1 relative luminance of a {@code #RRGGBB} colour. */
    private static double relativeLuminance(String hexColour) {
        int[] channels = rgb(hexColour);
        return 0.2126 * linear(channels[0]) + 0.7152 * linear(channels[1]) + 0.0722 * linear(channels[2]);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int[] rgb(String hexColour) {
        String hex = hexColour.substring(1);
        return new int[] {Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)};
    }
}
