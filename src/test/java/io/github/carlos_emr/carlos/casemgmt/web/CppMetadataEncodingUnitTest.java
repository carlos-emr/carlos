/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.List;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies note metadata remains text until the view encodes the complete note. */
@Tag("unit")
class CppMetadataEncodingUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"Reviewed with family", "O'Brien - follow-up", "日本語", "<img id=injected>"})
    void shouldPreserveMetadataText_untilTheHtmlOutputBoundary(String value) {
        CaseManagementNoteExt extra = new CaseManagementNoteExt();
        extra.setNoteId(1L);
        extra.setKeyVal("Treatment");
        extra.setValue(value);

        for (String text : new String[]{
                CaseManagementViewAction.getNoteExt(1L, "Treatment", List.of(extra)),
                CaseManagementView2Action.getNoteExt(1L, "Treatment", List.of(extra))}) {
            assertThat(text).isEqualTo(value);
            var html = Jsoup.parse("<p>" + SafeEncode.forHtml(text) + "</p>");
            assertThat(html.selectFirst("p").wholeText()).isEqualTo(value);
            assertThat(html.select("#injected")).isEmpty();
        }
    }
}
