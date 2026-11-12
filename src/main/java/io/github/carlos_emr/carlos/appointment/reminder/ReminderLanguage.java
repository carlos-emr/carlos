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
package io.github.carlos_emr.carlos.appointment.reminder;

import java.util.Locale;
import java.util.Map;

/**
 * The language an appointment reminder is written in (#5887): the patient's spoken language when CARLOS has
 * a translation for it, otherwise their official language, otherwise English. CARLOS ships English, French,
 * Spanish, Polish and Brazilian Portuguese.
 *
 * <p>Both fields hold the language names CARLOS stores ({@code spoken_languages_codes.properties} for the spoken
 * language; English, French or Other for the official one), so they are matched exactly, without case folding.
 *
 * @since 2026-11-12
 */
public final class ReminderLanguage {

    private static final Locale PORTUGUESE_BRAZIL = Locale.of("pt", "BR");

    private static final Map<String, Locale> TRANSLATED = Map.of(
            "English", Locale.ENGLISH,
            "French", Locale.FRENCH,
            "Spanish", Locale.of("es"),
            "Spanish; Castilian", Locale.of("es"),
            "Polish", Locale.of("pl"),
            "Portuguese", PORTUGUESE_BRAZIL);

    private ReminderLanguage() {
    }

    public static Locale of(String officialLanguage, String spokenLanguage) {
        Locale spoken = spokenLanguage == null ? null : TRANSLATED.get(spokenLanguage.trim());
        if (spoken != null) {
            return spoken;
        }
        return officialLanguage != null && "French".equals(officialLanguage.trim()) ? Locale.FRENCH : Locale.ENGLISH;
    }
}
