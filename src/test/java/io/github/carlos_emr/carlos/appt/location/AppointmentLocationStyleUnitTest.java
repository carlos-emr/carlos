/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/**
 * The location style rules (#3982): only whitelisted icons and #rrggbb colours are stored or
 * drawn, because both end up in class and style attributes.
 */
@DisplayName("AppointmentLocationStyle")
@Tag("unit")
@Tag("appointment")
class AppointmentLocationStyleUnitTest {

    @Test
    void shouldNameOnlyIconsTheBundledFontAwesomeDefines_forWhitelist() throws IOException {
        String css = Files.readString(Path.of("src/main/webapp/css/fontawesome-all.min.css"), StandardCharsets.UTF_8);
        for (String icon : AppointmentLocationStyle.ICONS) {
            assertThat(Pattern.compile("\\." + Pattern.quote(icon) + "[,:{]").matcher(css).find())
                    .as("%s is defined by css/fontawesome-all.min.css", icon).isTrue();
        }
        assertThat(AppointmentLocationStyle.ICONS).contains(AppointmentLocationStyle.DEFAULT_ICON).doesNotHaveDuplicates();
    }

    @Test
    void shouldNormaliseColour_toLowerCaseOrNull() {
        assertThat(AppointmentLocationStyle.normaliseColour(" #AbCdEf ")).isEqualTo("#abcdef");
        assertThat(AppointmentLocationStyle.normaliseColour("")).isNull();
        assertThat(AppointmentLocationStyle.normaliseColour(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"red", "#abc", "#abcdeg", "abcdef", "#abcdef;color:red", "#abcdef\"><b>", "url(x)"})
    void shouldRefuseColour_whenNotHexTriplet(String colour) {
        assertThatThrownBy(() -> AppointmentLocationStyle.normaliseColour(colour))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AppointmentLocationStyle.isValidColour(colour)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fa-skull", "fa-house fa-spin", "glyphicon-home", "\"><script>", "FA-HOUSE"})
    void shouldRefuseIcon_whenNotWhitelisted(String icon) {
        assertThatThrownBy(() -> AppointmentLocationStyle.normaliseIcon(icon))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptWhitelistedIcon_andClearOnBlank() {
        assertThat(AppointmentLocationStyle.normaliseIcon(" fa-video ")).isEqualTo("fa-video");
        assertThat(AppointmentLocationStyle.normaliseIcon(" ")).isNull();
    }

    @Test
    void shouldFallBackToDefaults_whenStoredStyleIsMissingOrInvalid() {
        LookupListItem plain = item(1, "Room", 1, true);
        LookupListItem tampered = item(2, "Room", 1, true);
        tampered.setColour("red;background:url(x)");
        tampered.setIcon("fa-house\" onclick=\"x");

        assertThat(AppointmentLocationStyle.chipColour(plain)).isEqualTo(AppointmentLocationStyle.DEFAULT_COLOUR);
        assertThat(AppointmentLocationStyle.chipIcon(plain)).isEqualTo(AppointmentLocationStyle.DEFAULT_ICON);
        assertThat(AppointmentLocationStyle.chipColour(tampered)).isEqualTo(AppointmentLocationStyle.DEFAULT_COLOUR);
        assertThat(AppointmentLocationStyle.chipIcon(tampered)).isEqualTo(AppointmentLocationStyle.DEFAULT_ICON);
        assertThat(AppointmentLocationStyle.chipColour(null)).isEqualTo(AppointmentLocationStyle.DEFAULT_COLOUR);
    }

    @Test
    void shouldUseStoredStyle_whenValid() {
        LookupListItem styled = item(3, "Virtual", 1, true);
        styled.setColour("#2fcccf");
        styled.setIcon("fa-video");

        assertThat(AppointmentLocationStyle.chipColour(styled)).isEqualTo("#2fcccf");
        assertThat(AppointmentLocationStyle.chipIcon(styled)).isEqualTo("fa-video");
    }

    @Test
    void shouldPickReadableTextColour_byBackgroundLuminance() {
        LookupListItem light = item(4, "Light", 1, true);
        light.setColour("#fdfec7");
        LookupListItem dark = item(5, "Dark", 1, true);
        dark.setColour("#1a237e");

        assertThat(AppointmentLocationStyle.chipTextColour(light)).isEqualTo("#000000");
        assertThat(AppointmentLocationStyle.chipTextColour(dark)).isEqualTo("#ffffff");
        assertThat(AppointmentLocationStyle.chipTextColour(item(6, "Default", 1, true))).isEqualTo("#ffffff");
    }
}
