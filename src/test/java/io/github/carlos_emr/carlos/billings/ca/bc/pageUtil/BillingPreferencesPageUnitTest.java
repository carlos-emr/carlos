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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC Billing Preferences page lost its Struts form tags in a migration: the selects stopped showing the saved
 * values, so every Save stored each select's first option (a billing-provider override and a payee nobody chose), and
 * the payee text, the "display clinic information" and "auto-populate" boxes and the script's warning lost their
 * wiring (issue #4936).
 *
 * @since 2026-10-18
 */
@DisplayName("BC Billing Preferences page")
@Tag("unit")
@Tag("billing")
class BillingPreferencesPageUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingPreferences.jsp");

    private static String page() throws IOException {
        return Files.readString(PAGE, StandardCharsets.UTF_8);
    }

    /** Each select marks the option equal to the preference the view action loaded as selected. */
    @ParameterizedTest
    @CsvSource({
            "defaultBillingProvider, billing.providerNo",
            "defaultBillingForm, billing.formCode",
            "defaultServiceLocation, serviceLocation.visitType",
            "payeeProviderNo, provider.providerNo",
    })
    void shouldShowTheSavedPreference_inEachSelect(String property, String optionValue) throws IOException {
        assertThat(page()).contains("<option value=\"${" + optionValue + "}\" ${" + optionValue + " == " + property
                + " ? 'selected' : ''}>");
    }

    @Test
    void shouldShowTheSavedReferralType_forEachOption() throws IOException {
        for (String value : new String[] {"1", "2", "3"}) {
            assertThat(page()).contains("<option value=\"" + value + "\" ${referral == '" + value + "' ? 'selected' : ''}>");
        }
    }

    @Test
    void shouldFillTheSavedPayeeText_forACustomPayee() throws IOException {
        assertThat(page()).contains("name=\"invoicePayeeInfo\"><carlos:encode value=\"${invoicePayeeInfo}\"/></textarea>")
                .doesNotContain(">&amp;</textarea>");
    }

    @Test
    void shouldOfferDisplayClinicInformation_asARealCheckbox() throws IOException {
        assertThat(page()).contains("<input type=\"checkbox\" class=\"form-check-input\" id=\"invoicePayeeDisplayClinicInfo\"")
                .doesNotContain("<checkbox");
    }

    @Test
    void shouldLetTheProviderTickAutoPopulate_unlessTheClinicWideSettingIsOn() throws IOException {
        // disabled="false" still disables a box, and a disabled box is not posted, so every Save stored it as off.
        assertThat(page()).contains("name=\"autoPopulateRefer\" value=\"true\"")
                .contains("<%= autoPopulateRefer ? \"checked\" : \"\" %> <%= globalAutoPopulateRefer ? \"disabled\" : \"\" %>")
                .doesNotContain("disabled=\"<%=globalAutoPopulateRefer%>\"");
    }

    @Test
    void shouldPostNoProviderNumber_andPointTheScriptAtItsWarning() throws IOException {
        // The save takes the signed-in provider; a posted number is not used, so the page sends none.
        assertThat(page()).doesNotContain("name=\"providerNo\"")
                .contains("id=\"default-provider-alert\"")
                .doesNotContain("default-providers-alert");
    }
}
