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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class EchartHeaderViewModelAssemblerUnitTest {

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
        @DisplayName("should show the name, sex, HIN with version and province, email, and next appointment")
        void shouldShowIdentityItems_withDefaultSettings() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.patientName()).isEqualTo("FAKE-Doe, FAKE-Jane (Janie)");
            assertThat(header.sex()).isEqualTo("F");
            assertThat(header.hin()).isEqualTo("9876543225");
            assertThat(header.hinVersion()).isEqualTo("AB");
            assertThat(header.hcType()).isEqualTo("ON");
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
        @DisplayName("should hide the HIN and its version and province")
        void shouldHideHin_whenHinSettingOff() {
            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), off("echart_show_HIN"), Locale.CANADA);

            assertThat(header.hin()).isEmpty();
            assertThat(header.hinVersion()).isEmpty();
            assertThat(header.hcType()).isEmpty();
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
        @DisplayName("should use the viewer's provider colour with readable black text and blue links")
        void shouldUseViewerColour_withReadableText() {
            when(userPropertyDao.getProviderPropertiesByNames(eq(VIEWER), any())).thenReturn(Map.of("ProviderColour", "#FFCC99"));

            EchartHeaderViewModel header = assembler.assemble(VIEWER, patient(), EchartDisplayPreferences.defaults(), Locale.CANADA);

            assertThat(header.headerBackground()).isEqualTo("#FFCC99");
            assertThat(header.headerText()).isEqualTo("#000000");
            assertThat(header.headerLink()).isEqualTo("#0000CC");
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
}
