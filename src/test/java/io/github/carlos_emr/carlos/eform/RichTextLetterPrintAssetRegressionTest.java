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
 * Pins the browser-side half of the Rich Text Letter print / PDF fix.
 *
 * <p>Two assets are involved. {@code printControl.js} injects the "PDF" / "Submit &amp; PDF" buttons and
 * posts {@code print=true}, which {@code AddEForm2Action} treats as the save-and-download workflow.
 * It used to guard its hidden inputs with {@code if (printHolder == null || !printHolder)} on a
 * jQuery object, which is never falsy, so the flag was never posted and the buttons were a plain
 * Save; it must also serialize the letter through {@code saveRTL()} so the stored value carries the
 * same escaping as a plain Save. {@code editControl2.js} must re-register its dirty-flag listener after every
 * template load, because the load navigates the editor iframe and drops listeners on the old
 * Window — without it, typing into a new letter never set {@code needToConfirm}, so the toolbar's
 * Print printed without saving. These are text assertions on the shipped assets; the behaviour
 * itself was verified in a browser against the assembled page.</p>
 *
 * @since 2026-09-02
 */
@DisplayName("Rich Text Letter print/PDF asset regressions")
@Tag("unit")
@Tag("eform")
class RichTextLetterPrintAssetRegressionTest {

    private static final Path PRINT_CONTROL_JS =
            Path.of("src", "main", "webapp", "library", "eforms", "printControl.js");
    private static final Path EDIT_CONTROL_2_JS =
            Path.of("src", "main", "webapp", "WEB-INF", "eform-assets", "editControl2.js");
    private static final Path RELEASE_EDIT_CONTROL_2_JS =
            Path.of("release", "editControl2.js");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should post the legacy print flag and serialize the letter through saveRTL when present")
    void shouldSerializeThroughSaveRtl_whenPostingPrintFlag() throws IOException {
        String script = read(PRINT_CONTROL_JS);

        assertThat(script).contains("name='print' value='true'");
        // The hidden inputs must be created on an emptiness test, not on a jQuery object's truthiness.
        assertThat(script).contains("jQuery('#printHolder').length === 0");
        assertThat(script).doesNotContain("if (printHolder == null || !printHolder) {");
        assertThat(script).contains("typeof saveRTL === \"function\"");
        // Raw fallback stays for non-letter forms that declare #Letter without the RTL serializer.
        assertThat(script).contains("document.getElementById('Letter').value = editControlContents('edit');");
    }

    @Test
    @DisplayName("should not rely on string-form timers under the eForm CSP")
    void shouldAvoidStringTimers_underEformCsp() throws IOException {
        String script = read(PRINT_CONTROL_JS);

        assertThat(script).doesNotContain("setTimeout(\"");
        assertThat(script).doesNotContain("setTimeout('");
    }

    @Test
    @DisplayName("should re-attach the dirty-flag listener after every editor template load")
    void shouldReattachDirtyFlagListener_afterTemplateLoad() throws IOException {
        String packaged = read(EDIT_CONTROL_2_JS);
        String release = read(RELEASE_EDIT_CONTROL_2_JS);

        assertThat(release).as("release copy must track the packaged asset").isEqualTo(packaged);

        assertThat(packaged).contains("function attachDirtyFlagListener()");
        // Start() registers once; loadDefaultTemplate (both branches) and loadTemplate re-register.
        assertThat(packaged.split("attachDirtyFlagListener\\(\\);", -1).length - 1)
                .as("one initial registration plus one per template-load path")
                .isGreaterThanOrEqualTo(4);
        assertThat(packaged).contains("obj.onload = function() { parseTemplate(); attachDirtyFlagListener(); };");
        // The old single registration on the pre-template window must be gone.
        assertThat(packaged).doesNotContain(".addEventListener('keypress', setDirtyFlag, true);\n\t\t\t}");
    }
}
