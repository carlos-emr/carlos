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
package io.github.carlos_emr.carlos.demographic;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the read-only patient record passes a consent type's description to the hover popup.
 * <p>
 * The description is free text from the {@code consentType} table (a migration, direct SQL or the consent
 * REST API can set it). It used to be written into the {@code onmouseover} handler bare, which is a
 * JavaScript syntax error for any plain-text description and a script-injection sink for a crafted one.
 * {@code nhpup.popup} assigns its argument to {@code innerHTML}, so the value needs HTML encoding inside
 * the JavaScript-attribute encoding.
 *
 * @since 2026-09-21
 */
@DisplayName("demographic edit-view.jsp consent popup encoding")
@Tag("unit")
@Tag("demographic")
class DemographicConsentPopupJspEncodingUnitTest {
    private static final Path EDIT_VIEW_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp");

    @Test
    @DisplayName("the consent description reaches the popup as an encoded, quoted JavaScript string")
    void shouldEncodeConsentDescription_forInnerHtmlInsideJavaScriptAttribute() throws Exception {
        String jsp = Files.readString(EDIT_VIEW_JSP);

        assertThat(jsp)
                .doesNotContain("nhpup.popup(${ patientConsent.consentType.description }")
                .contains("nhpup.popup('${carlos:forJavaScriptAttribute("
                        + "carlos:forHtmlContent(patientConsent.consentType.description))}',{'width':350} );");
    }
}
