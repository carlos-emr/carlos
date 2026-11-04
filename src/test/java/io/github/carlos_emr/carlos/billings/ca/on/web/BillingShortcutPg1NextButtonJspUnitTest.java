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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hospital billing page 1's Next must post {@code submit=Next} in every locale (#5055).
 *
 * <p>{@code BillingShortcutPg2Service} calculates and saves only for {@code submit} values
 * Next, Save and Save and Back, and the Confirmation page echoes page 1's value back. A submit
 * {@code <input>} posts its label, so a French, Spanish, Polish or Portuguese page posted
 * "Suivant", "Siguiente", "Dalej" or "Próximo" and no bill was ever priced or saved.</p>
 *
 * @since 2026-10-23
 */
@Tag("unit")
@Tag("billing")
@DisplayName("Hospital billing page 1 Next button")
class BillingShortcutPg1NextButtonJspUnitTest {

    private static final Pattern SUBMIT_CONTROL = Pattern.compile(
            "(?s)<(input|button)\\b(?=[^>]*\\bname=\"submit\")[^>]*>");

    private static String pageOne() throws IOException, URISyntaxException {
        Path current = Path.of(BillingShortcutPg1NextButtonJspUnitTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        while (current != null && !Files.isDirectory(current.resolve("src/main/webapp"))) {
            current = current.getParent();
        }
        assertThat(current).as("project root with src/main/webapp").isNotNull();
        return Files.readString(current.resolve("src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingShortcutPg1.jsp"),
                StandardCharsets.UTF_8);
    }

    @Test
    void shouldPostAStableNextValue_withATranslatedLabel() throws Exception {
        String jsp = pageOne();
        Matcher control = SUBMIT_CONTROL.matcher(jsp);

        assertThat(control.find()).as("page 1's submit control").isTrue();
        assertThat(control.group(1)).as("a button, whose posted value is not its label").isEqualTo("button");
        assertThat(control.group()).contains("type=\"submit\"").contains("value=\"Next\"");
        assertThat(jsp.substring(control.end()))
                .startsWith("<fmt:message key=\"billing.hospitalBilling.btnNext\"/></button>");
        assertThat(control.find()).as("only one control posts submit").isFalse();
    }
}
