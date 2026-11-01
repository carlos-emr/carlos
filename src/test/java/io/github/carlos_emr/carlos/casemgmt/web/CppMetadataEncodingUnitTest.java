/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    @ValueSource(strings = {"cat", "50% + follow-up; reviewed", "O'Brien \"quoted\" \\x74",
            "日本語 😀", "</script><img id=injected>", "literal%3Bvalue + 100%", ""})
    void shouldPreserveEditableMetadata_throughTheActualPrepareAndSaveFunctions(String value) throws Exception {
        Path root = Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        Path script = root.resolve("src/main/webapp/js/newCaseManagementView.js.jsp");
        String jsp = Files.readString(root.resolve("src/main/webapp/WEB-INF/jsp/casemgmt/viewNotes.jsp"));
        assertThat(jsp).contains("val = SafeEncode.forUriComponent(cme.getValue());")
                .contains("value='<%= strNoteExts %>' context=\"javaScriptAttribute\"");
        String relationship = "sibling; 50% + review";
        String fields = "Treatment;" + SafeEncode.forUriComponent(value)
                + ";Relationship;" + SafeEncode.forUriComponent(relationship) + ";Start Date;2024-05";
        String encodedArgument = SafeEncode.forJavaScriptAttribute(fields);
        Process process = new ProcessBuilder("node",
                root.resolve("src/test/resources/scripts/cpp-edit-round-trip.cjs").toString(),
                script.toString()).start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(new ObjectMapper().writeValueAsBytes(encodedArgument));
            }
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("CPP edit and save finished").isTrue();
            String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("CPP client code must execute: %s", errors).isZero();
            var posted = new ObjectMapper().readTree(process.getInputStream().readAllBytes());
            assertThat(posted.get("treatment").textValue()).isEqualTo(value);
            assertThat(posted.get("relationship").textValue()).isEqualTo(relationship);
            assertThat(posted.get("startdate").textValue()).isEqualTo("2024-05");
        } finally {
            process.destroyForcibly();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Reviewed with family", "O'Brien - follow-up", "日本語", "<img id=injected>"})
    void shouldPreserveMetadataText_untilTheHtmlOutputBoundary(String value) {
        CaseManagementNoteExt extra = new CaseManagementNoteExt();
        extra.setNoteId(1L);
        extra.setKeyVal("Treatment");
        extra.setValue(value);

        String text = CaseManagementViewAction.getNoteExt(1L, "Treatment", List.of(extra));
        assertThat(text).isEqualTo(value);
        var html = Jsoup.parse("<p>" + SafeEncode.forHtml(text) + "</p>");
        assertThat(html.selectFirst("p").wholeText()).isEqualTo(value);
        assertThat(html.select("#injected")).isEmpty();
    }
}
