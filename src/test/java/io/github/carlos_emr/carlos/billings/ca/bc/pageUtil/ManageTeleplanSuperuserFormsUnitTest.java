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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manage Teleplan's page offers the writes that {@link ManageTeleplan2Action} keeps to the billing superuser only
 * inside its BILLING_SUPERUSER check, so no other login sees a form the action refuses.
 *
 * @since 2026-10-19
 */
@DisplayName("Manage Teleplan superuser forms")
@Tag("unit")
@Tag("billing")
class ManageTeleplanSuperuserFormsUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/teleplan/ManageTeleplan.jsp");
    /** The methods ManageTeleplan2Action refuses to any login but the billing superuser. */
    private static final List<String> SUPERUSER_METHODS =
            List.of("setSequenceNumber", "getSequenceNumber", "setUserName", "setPass");
    private static final String CHECK_OPEN = "<oscar:oscarPropertiesCheck property=\"BILLING_SUPERUSER\"";
    private static final String CHECK_CLOSE = "</oscar:oscarPropertiesCheck>";

    @Test
    @DisplayName("should offer each superuser write only inside the page's billing superuser check")
    void shouldOfferSuperuserWrites_onlyInsideTheSuperuserCheck() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>|<!--.*?-->", "");
        List<String> found = new ArrayList<>();
        List<String> outside = new ArrayList<>();
        Matcher method = Pattern.compile("<input type=\"hidden\" name=\"method\" value=\"(\\w+)\"").matcher(page);
        while (method.find()) {
            if (!SUPERUSER_METHODS.contains(method.group(1))) {
                continue;
            }
            found.add(method.group(1));
            String before = page.substring(0, method.start());
            if (before.lastIndexOf(CHECK_OPEN) <= before.lastIndexOf(CHECK_CLOSE)) {
                outside.add(method.group(1));
            }
        }
        assertThat(found).as("the superuser writes the page offers").containsExactlyInAnyOrderElementsOf(SUPERUSER_METHODS);
        assertThat(outside).as("superuser writes offered outside the BILLING_SUPERUSER check").isEmpty();
    }
}
