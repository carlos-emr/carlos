/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bill-to address book's Add/Edit Address page, BC and Ontario (#4962): Search and Save run no check written for
 * another page, Save asks to confirm in the user's language, and Save sends the value the action saves on whatever
 * its label says.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("Bill-to Add/Edit Address page")
class ThirdPartyAddressEditJspRegressionTest {

    private static final String CONFIRM_KEY = "billing.billingOnFavourite.msgConfirmSave";

    private static String page(String province) throws IOException {
        return Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/" + province + "/onAddEdit3rdAddr.jsp"),
                StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BC", "ON"})
    @DisplayName("should not run the service-code checks, which read fields the page doesn't have")
    void shouldNotRunServiceCodeChecks_onSearchOrSave(String province) throws IOException {
        assertThat(page(province))
                .doesNotContain("checkServiceCode")
                .doesNotContain("checkAllFields")
                .doesNotContain("forms[0].service_code")
                .doesNotContain("forms[1].serviceCode");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BC", "ON"})
    @DisplayName("should ask to confirm Save in the user's language")
    void shouldConfirmSave_inUserLanguage(String province) throws IOException {
        assertThat(page(province))
                .contains("<fmt:message key=\"" + CONFIRM_KEY + "\" var=\"confirmSaveMessage\"/>")
                .contains("return confirm('${carlos:forJavaScript(confirmSaveMessage)}');")
                .doesNotContain("confirm(\"Are you sure you want to save?\")");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BC", "ON"})
    @DisplayName("should send the value Save whatever the button's translated label")
    void shouldSendSaveValue_withTranslatedLabel(String province) throws IOException {
        assertThat(page(province))
                .contains("<button\n                        type=\"submit\" name=\"submit\" value=\"Save\"\n"
                        + "                        onclick=\"javascript:return onSave();\">"
                        + "<fmt:message key=\"admin.resourcebaseurl.btnSave\"/></button>")
                .doesNotContain("value=\"<fmt:message key=\"admin.resourcebaseurl.btnSave\"/>\"");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should have the save confirmation in every locale")
    void shouldDefineSaveConfirmation_forEveryLocale(String locale) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }

        assertThat(bundle.getProperty(CONFIRM_KEY)).isNotBlank().endsWith("?");
    }
}
