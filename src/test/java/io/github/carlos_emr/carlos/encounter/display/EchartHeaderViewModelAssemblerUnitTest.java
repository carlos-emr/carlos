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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the OSCAR 19 header bar rules the assembler applies: provider colours, date of birth versus
 * age, HIN, the phone choice (including the patient's preferred-number marks), and email.
 */
@Tag("unit")
@Tag("fast")
@Tag("encounter")
@DisplayName("EchartHeaderViewModelAssembler")
class EchartHeaderViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    private static final String VIEWER = "999998";
    private static final String MRP = "100";

    private UserPropertyDAO userPropertyDao;
    private EchartHeaderViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        userPropertyDao = mock(UserPropertyDAO.class);
        when(userPropertyDao.getProviderPropertiesByNames(any(), any())).thenReturn(Map.of());
        assembler = new EchartHeaderViewModelAssembler(userPropertyDao);
    }

    @Nested
    @DisplayName("with the default settings")
    class Defaults {

        @Test
        @DisplayName("should show the date of birth with the age as its tooltip")
        void shouldShowDateOfBirth_withAgeAsTooltip() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.showDateOfBirth()).isTrue();
            assertThat(header.dateOfBirth()).isEqualTo("1980-01-02");
            assertThat(header.age()).isNotBlank();
        }

        @Test
        @DisplayName("should show the name, sex, HIN with version, email, and next appointment")
        void shouldShowIdentityItems_withDefaultSettings() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.patientName()).isEqualTo("FAKE-Doe, FAKE-Jane (Janie)");
            assertThat(header.sex()).isEqualTo("F");
            assertThat(header.hin()).isEqualTo("9876543225");
            assertThat(header.hinVersion()).isEqualTo("AB");
            assertThat(header.email()).isEqualTo("jane@example.test");
            assertThat(header.emailConsented()).isFalse();
            assertThat(header.nextAppointment()).isEqualTo("2026-11-02");
        }

        @Test
        @DisplayName("should prefer the cell phone and offer the home number in the tooltip")
        void shouldPreferCell_withHomeNumberAsAlternate() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.phones()).containsExactly(new Phone(PhoneKind.CELL, "905-555-0100", PhoneKind.HOME, "905-555-0199"));
        }

        @Test
        @DisplayName("should name the MRP first name first and underline it in the MRP's own colour")
        void shouldNameMrp_andUnderlineInMrpColour() {
            when(userPropertyDao.getProviderPropertiesByNames(eq(MRP), any())).thenReturn(Map.of("ProviderColour", "#336699"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.mrpName()).isEqualTo("FAKE-Ann FAKE-Smith");
            assertThat(header.mrpUnderline()).isEqualTo("#336699");
        }
    }

    @Nested
    @DisplayName("with settings turned off")
    class SettingsOff {

        @Test
        @DisplayName("should show the age with the date of birth as its tooltip")
        void shouldShowAge_whenDateOfBirthSettingOff() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), off("echart_show_DOB"), Locale.CANADA);

            assertThat(header.showDateOfBirth()).isFalse();
            assertThat(header.dateOfBirth()).isEqualTo("1980-01-02");
        }

        @Test
        @DisplayName("should hide the HIN and its version")
        void shouldHideHin_whenHinSettingOff() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), off("echart_show_HIN"), Locale.CANADA);

            assertThat(header.hin()).isEmpty();
            assertThat(header.hinVersion()).isEmpty();
        }

        @Test
        @DisplayName("should hide the email")
        void shouldHideEmail_whenEmailSettingOff() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), off("echart_email_indicator"), Locale.CANADA);

            assertThat(header.email()).isEmpty();
            assertThat(header.emailConsented()).isFalse();
        }

        @Test
        @DisplayName("should show the home number with the cell in the tooltip")
        void shouldShowHomeNumber_whenCellSettingOff() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), off("echart_show_cell"), Locale.CANADA);

            assertThat(header.phones()).containsExactly(new Phone(PhoneKind.HOME, "905-555-0199", PhoneKind.CELL, "905-555-0100"));
        }
    }

    @Nested
    @DisplayName("phone choice")
    class PhoneChoice {

        @Test
        @DisplayName("should fall back to the home number when no cell is on file")
        void shouldFallBackToHome_whenNoCell() {
            assertThat(EchartHeaderViewModelAssembler.selectPhones("", "905-555-0199", "", true))
                    .containsExactly(new Phone(PhoneKind.HOME, "905-555-0199", null, ""));
        }

        @Test
        @DisplayName("should show nothing when the patient has no number")
        void shouldShowNothing_whenNoNumber() {
            assertThat(EchartHeaderViewModelAssembler.selectPhones(null, " ", null, true)).isEmpty();
        }

        @Test
        @DisplayName("should show every number the patient marked preferred, whatever the setting")
        void shouldShowMarkedNumbers_whateverTheSetting() {
            List<Phone> phones = EchartHeaderViewModelAssembler.selectPhones("905-555-0100", "905-555-0199*", "416-555-0123*", true);

            assertThat(phones).extracting(Phone::kind).containsExactly(PhoneKind.HOME, PhoneKind.WORK);
            assertThat(phones.get(0).number()).isEqualTo("905-555-0199*");
        }
    }

    @Nested
    @DisplayName("colours")
    class Colours {

        @Test
        @DisplayName("should use the viewer's provider colour with readable black text and OSCAR 19's blue, darkened, for links")
        void shouldUseViewerColour_withReadableText() {
            when(userPropertyDao.getProviderPropertiesByNames(eq(VIEWER), any())).thenReturn(Map.of("ProviderColour", "#FFCC99"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.headerBackground()).isEqualTo("#FFCC99");
            assertThat(header.headerText()).isEqualTo("#000000");
            assertThat(header.headerLink()).isEqualTo("#006192");
        }

        @ParameterizedTest
        @ValueSource(strings = {"#CCCCFF", "#FFCC99", "#FFFFFF", "#FFFF99", "#99CCFF", "#CCCCCC", "#A0A0A0", "#80C080"})
        @DisplayName("should keep links at 4.5:1 or better on every light provider colour, in OSCAR 19's blue hue")
        void shouldKeepLinksReadable_onLightProviderColours(String background) {
            String link = EchartHeaderViewModelAssembler.readableLinkColour(background);

            assertThat(contrast(link, background)).as("%s on %s", link, background).isGreaterThanOrEqualTo(4.5);
            assertThat(link).as("still OSCAR's blue: no red, green about two thirds of blue").startsWith("#00");
            int green = Integer.parseInt(link.substring(3, 5), 16);
            int blue = Integer.parseInt(link.substring(5, 7), 16);
            assertThat(green * 3).isBetween(blue * 2 - 3, blue * 2 + 3);
        }

        @Test
        @DisplayName("should pick black text and a dark blue link on saturated magenta, where white reaches only 3.1:1")
        void shouldPickBlackText_onSaturatedMagenta() {
            when(userPropertyDao.getProviderPropertiesByNames(eq(VIEWER), any())).thenReturn(Map.of("ProviderColour", "#FF00FF"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(contrast("#FFFFFF", "#FF00FF")).isLessThan(4.5);
            assertThat(header.headerText()).isEqualTo("#000000");
            assertThat(contrast(header.headerLink(), "#FF00FF")).isGreaterThanOrEqualTo(4.5);
        }

        @Test
        @DisplayName("should keep header text and links at 4.5:1 or better on every web-safe provider colour")
        void shouldKeepTextAndLinksReadable_onEveryWebSafeColour() {
            int[] steps = {0x00, 0x33, 0x66, 0x99, 0xCC, 0xFF};
            for (int red : steps) {
                for (int green : steps) {
                    for (int blue : steps) {
                        String background = String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
                        when(userPropertyDao.getProviderPropertiesByNames(eq(VIEWER), any()))
                                .thenReturn(Map.of("ProviderColour", background));

                        EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(),
                                EchartDisplayPreferences.defaults(), Locale.CANADA);

                        assertThat(contrast(header.headerText(), background)).as("text on %s", background)
                                .isGreaterThanOrEqualTo(4.5);
                        assertThat(contrast(header.headerLink(), background)).as("links on %s", background)
                                .isGreaterThanOrEqualTo(4.5);
                    }
                }
            }
        }

        @Test
        @DisplayName("should darken OSCAR 19's blue no further than the default colour needs")
        void shouldDarkenOscarBlueMinimally_forDefaultColour() {
            assertThat(contrast(EchartHeaderViewModelAssembler.OSCAR_LINK, "#CCCCFF")).isLessThan(4.5);
            assertThat(EchartHeaderViewModelAssembler.readableLinkColour("#CCCCFF")).isEqualTo("#005F8E");
            assertThat(contrast("#006090", "#CCCCFF")).as("one step lighter fails").isLessThan(4.5);
        }

        @Test
        @DisplayName("should switch to white text and links on a dark provider colour")
        void shouldUseWhiteText_onDarkColour() {
            when(userPropertyDao.getProviderPropertiesByNames(eq(VIEWER), any())).thenReturn(Map.of("ProviderColour", "000080"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.headerBackground()).isEqualTo("#000080");
            assertThat(header.headerText()).isEqualTo("#FFFFFF");
            assertThat(header.headerLink()).isEqualTo("#FFFFFF");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"red", "#FFF", "#GGGGGG", "#123456;background:url(x)", "expression(alert(1))"})
        @DisplayName("should fall back to OSCAR's default for anything but a six-digit hex colour")
        void shouldFallBackToDefault_whenColourInvalid(String colour) {
            assertThat(EchartHeaderViewModelAssembler.normalizeColour(colour)).isEqualTo("#CCCCFF");
        }

        @Test
        @DisplayName("should leave the MRP fields empty when no MRP is assigned")
        void shouldLeaveMrpEmpty_whenNoMrp() {
            Demographic demographic = patient();
            demographic.setMrp(null);

            EchartHeaderViewModel header = assembler.assemble(VIEWER, demographic, EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.mrpName()).isEmpty();
            assertThat(header.mrpUnderline()).isEmpty();
        }
    }

    @Test
    @DisplayName("should make a consented patient's email a mailto candidate")
    void shouldFlagConsentedEmail_forMailtoLink() {
        Demographic demographic = patient();
        demographic.setConsentToUseEmailForCare(Boolean.TRUE);

        EchartHeaderViewModel header = assembler.assemble(VIEWER, demographic, EchartDisplayPreferences.defaults(), Locale.CANADA);

        assertThat(header.emailConsented()).isTrue();
    }

    @Nested
    @DisplayName("click-to-dial phone links")
    class TelLinks {

        @Test
        @DisplayName("should leave phones as copy buttons by default")
        void shouldKeepCopyButtons_withDefaultSettings() {
            assertThat(assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA).phoneTelLinks())
                    .isFalse();
        }

        @Test
        @DisplayName("should make phones tel: links when the provider turns the setting on")
        void shouldMakeTelLinks_whenSettingOn() {
            EchartDisplayPreferences preferences = EchartDisplayPreferences.fromStoredValues(Map.of("echart_phone_tel_links", "true"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), preferences, Locale.CANADA);

            assertThat(header.phoneTelLinks()).isTrue();
            assertThat(header.phones()).extracting(Phone::telHref).containsExactly("tel:9055550100");
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "905-555-0199          | tel:9055550199",
                "(905) 555-0199*       | tel:9055550199",
                "+1 905 555 0199       | tel:+19055550199",
                "905.555.0199 x123     | tel:9055550199;ext=123",
                "905-555-0199 ext. 45  | tel:9055550199;ext=45",
                "905-555-0199 Ext 7*   | tel:9055550199;ext=7",
                "905-555-0199 ext: 123 | tel:9055550199;ext=123",
                "905-555-0199 Ext.:12  | tel:9055550199;ext=12",
                "905-555-0199 extension 456 | tel:9055550199;ext=456",
                "905-555-0199 EXTENSION:7   | tel:9055550199;ext=7",
                "416-555-0123 #22      | tel:4165550123;ext=22",
        })
        @DisplayName("should dial only the digits, keep a leading +, and split off an extension")
        void shouldBuildDialableUri_fromRecordedNumber(String number, String expected) {
            assertThat(new Phone(PhoneKind.HOME, number, null, "").telHref()).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "*", "unknown", "x"})
        @DisplayName("should give no link when there is nothing to dial")
        void shouldReturnEmpty_whenNoDigits(String number) {
            assertThat(new Phone(PhoneKind.CELL, number, null, "").telHref()).isEmpty();
        }

        @Test
        @DisplayName("should give no link for a missing number")
        void shouldReturnEmpty_whenNumberNull() {
            assertThat(new Phone(PhoneKind.WORK, null, null, "").telHref()).isEmpty();
        }
    }

    @Nested
    @DisplayName("mailtoHref()")
    class MailtoHref {

        @Test
        @DisplayName("should keep the @ separator literal")
        void shouldKeepAtSignLiteral_forPlainAddress() {
            assertThat(headerWithEmail("jane.doe@example.test").mailtoHref()).isEqualTo("mailto:jane.doe@example.test");
        }

        @Test
        @DisplayName("should percent-encode characters that would add mailto header fields")
        void shouldPercentEncodeHeaderDelimiters_inLocalPartAndDomain() {
            assertThat(headerWithEmail("a?cc=x&b@ex#ample.test").mailtoHref())
                    .isEqualTo("mailto:a%3Fcc%3Dx%26b@ex%23ample.test");
        }

        @Test
        @DisplayName("should split on the last @ when the local part holds one")
        void shouldSplitOnLastAtSign_forQuotedLocalPart() {
            assertThat(headerWithEmail("\"a@b\"@example.test").mailtoHref())
                    .isEqualTo("mailto:%22a%40b%22@example.test");
        }

        @Test
        @DisplayName("should encode an address without @ as a single component")
        void shouldEncodeWholeValue_whenNoAtSign() {
            assertThat(headerWithEmail("not an address").mailtoHref()).isEqualTo("mailto:not%20an%20address");
        }

        @Test
        @DisplayName("should be empty when no email is shown")
        void shouldReturnEmpty_whenNoEmail() {
            assertThat(headerWithEmail("").mailtoHref()).isEmpty();
        }

        private EchartHeaderViewModel headerWithEmail(String email) {
            Demographic demographic = patient();
            demographic.setEmail(email);
            demographic.setConsentToUseEmailForCare(Boolean.TRUE);
            return assembler.assemble(VIEWER, demographic, EchartDisplayPreferences.defaults(), Locale.CANADA);
        }
    }

    private static EchartDisplayPreferences off(String propertyName) {
        return EchartDisplayPreferences.fromStoredValues(Map.of(propertyName, "false"));
    }

    private static Demographic patient() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(42);
        demographic.setLastName("FAKE-Doe");
        demographic.setFirstName("FAKE-Jane");
        demographic.setAlias("Janie");
        demographic.setSex("F");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setHin("9876543225");
        demographic.setVer("AB");
        demographic.setHcType("ON");
        demographic.setPhone("905-555-0199");
        demographic.setPhone2("416-555-0123");
        demographic.setCellPhone("905-555-0100");
        demographic.setEmail("jane@example.test");
        demographic.setNextAppointment("2026-11-02");
        Provider mrp = new Provider();
        mrp.setProviderNo(MRP);
        mrp.setFirstName("FAKE-Ann");
        mrp.setLastName("FAKE-Smith");
        demographic.setMrp(mrp);
        return demographic;
    }

    /** WCAG 2.1 contrast ratio, computed here independently of the assembler. */
    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        double[] weights = {0.2126, 0.7152, 0.0722};
        double total = 0;
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            total += weights[i] * (value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4));
        }
        return total;
    }
}
