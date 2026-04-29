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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("genRADesc.jsp encoding")
@Tag("unit")
@Tag("billing")
class GenerateRaDescriptionJspEncodingUnitTest {

    @Test
    void shouldRenderRaRowsWithEncodingInsteadOfRawHtmlModelFields() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/ON/genRADesc.jsp"));

        assertThat(jsp)
                .doesNotContain("balanceForwardHtml")
                .doesNotContain("transactionHtml")
                .doesNotContain("${raDescModel.balanceForwardHtml}")
                .doesNotContain("${raDescModel.transactionHtml}");

        assertThat(jsp)
                .contains("${raDescModel.balanceForwardRow.claimsAdjustment}")
                .contains("<c:forEach var=\"__txn\" items=\"${raDescModel.transactionRows}\">")
                .contains("value=\"${__txn.message}\" context=\"html\"");
    }
}
