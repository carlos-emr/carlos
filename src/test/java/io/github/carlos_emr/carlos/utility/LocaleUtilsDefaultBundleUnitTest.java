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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link LocaleUtils}' built-in bundle to the one the WAR ships.
 *
 * <p>The default used to name {@code string_tables/strings}, which does not exist, and only
 * {@code ContextStartupListener} replaced it at web-context start. Every lookup made without that
 * listener -- unit tests above all -- returned the key itself, so a label such as
 * {@code RxPreview.msgTel} rendered as the literal key and a test could not tell. Nothing here sets
 * {@link LocaleUtils#BASE_NAME}; the tests that do restore it afterwards.</p>
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("i18n")
@DisplayName("LocaleUtils default message bundle")
class LocaleUtilsDefaultBundleUnitTest {

    @Test
    @DisplayName("should default to the shipped oscarResources bundle")
    void shouldDefaultToOscarResources_withoutStartupListener() {
        assertThat(LocaleUtils.BASE_NAME).isEqualTo("oscarResources");
    }

    @Test
    @DisplayName("should resolve a message rather than echo its key")
    void shouldResolveMessage_forEnglishLocale() {
        assertThat(LocaleUtils.getMessage(Locale.ENGLISH, "RxPreview.msgTel")).isEqualTo("Tel");
        assertThat(LocaleUtils.getMessage(Locale.ENGLISH, "tickler.ticklerMain.stActive"))
                .isNotEqualTo("tickler.ticklerMain.stActive");
    }

    @Test
    @DisplayName("should resolve the locale's own translation")
    void shouldResolveTranslation_forFrenchLocale() {
        assertThat(LocaleUtils.getMessage(Locale.FRENCH, "RxPreview.msgTel")).isEqualTo("Téléphone");
    }

    @Test
    @DisplayName("should still return the key for a message no bundle defines")
    void shouldReturnKey_forUnknownMessage() {
        assertThat(LocaleUtils.getMessage(Locale.ENGLISH, "no.such.message.key")).isEqualTo("no.such.message.key");
    }
}
