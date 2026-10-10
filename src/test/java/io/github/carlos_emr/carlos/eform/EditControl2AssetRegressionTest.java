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
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Rich Text Letter editor asset against regressions that would force
 * the editor iframe onto an opaque origin and break toolbar commands.
 *
 * @since 2026-04-21
 */
@DisplayName("Rich Text Letter editor asset regressions")
@Tag("unit")
@Tag("eform")
class EditControl2AssetRegressionTest {

    private static final Path EDIT_CONTROL_2_JS =
            Path.of("src", "main", "webapp", "WEB-INF", "eform-assets", "editControl2.js");
    private static final Path RELEASE_EDIT_CONTROL_2_JS =
            Path.of("release", "editControl2.js");

    @Test
    @DisplayName("should keep the blank template fallback same-origin when the RTL editor bootstraps")
    void shouldKeepBlankTemplateSameOrigin_whenEditorBootstraps() throws IOException {
        String packagedScript = Files.readString(EDIT_CONTROL_2_JS, StandardCharsets.UTF_8);
        String releaseScript = Files.readString(RELEASE_EDIT_CONTROL_2_JS, StandardCharsets.UTF_8);

        assertThat(releaseScript).isEqualTo(packagedScript);

        assertBlankTemplateSameOriginInvariant(packagedScript);
        assertBlankTemplateSameOriginInvariant(releaseScript);
    }

    @Test
    @DisplayName("should stamp a letter with the signing provider's own signature file")
    void shouldStampWithProviderSignature_whenPickingAStamp() throws IOException {
        String packagedScript = Files.readString(EDIT_CONTROL_2_JS, StandardCharsets.UTF_8);
        String releaseScript = Files.readString(RELEASE_EDIT_CONTROL_2_JS, StandardCharsets.UTF_8);

        assertThat(releaseScript).as("release copy must track the packaged asset").isEqualTo(packagedScript);

        // Before this, pickStamp() only knew ImgArray (the hand-maintained stamps.js list) and a
        // single shared stamp.png, so a multi-provider clinic without a stamps.js signed every
        // letter with the same image. CARLOS already stores a per-provider signature under this
        // name; the letter has to ask for it.
        assertThat(packagedScript).contains("\"consult_sig_\" + providerNumber + \".png\"");

        // The delegation rule, mirroring sign() in visualEformEditor.jsp: a billing practitioner
        // signs their own letters, anyone below the threshold is writing under the direction of
        // the patient's MRP and stamps with the MRP's signature.
        assertThat(packagedScript)
                .contains("var MIN_BILLING_PROVIDER_OHIP_NO = 1000;")
                .contains("userOhipNo > MIN_BILLING_PROVIDER_OHIP_NO");

        // The identity comes from the hidden inputs the eForm framework populates server-side,
        // with the matching AP keys as the fallback for a stored form_html that predates them.
        assertThat(packagedScript)
                .contains("inputId: \"user_id\"")
                .contains("inputId: \"user_ohip_no\"")
                .contains("inputId: \"doctor_provider_no\"")
                .contains("\"current_user_id\", \"current_user_ohip_no\", \"doctor_provider_no\"");

        // stamps.js and stamp.png stay reachable: an install with no per-provider signatures on
        // file must keep working exactly as it did.
        assertThat(packagedScript)
                .contains("function legacyStampFile()")
                .contains("var fileName = \"stamp.png\";");
    }

    private void assertBlankTemplateSameOriginInvariant(String script) {
        // Ensure the option points to the blank.rtl template
        assertThat(script).contains("<option value=\"blank.rtl\">blank</option>");
        // Ensure the iframe srcdoc uses the same-origin blank template
        assertThat(script).contains(".srcdoc = blankTemplate;");
        assertThat(script).doesNotContain("data:text/html;charset=utf-8,");

        // Ensure the runtime cfg_template default is aligned with the blank.rtl template
        assertThat(script).contains("var cfg_template = 'blank.rtl';");
        // Guard against reintroducing the old 'blank' default
        assertThat(script).doesNotContain("var cfg_template = 'blank';");
    }
}
