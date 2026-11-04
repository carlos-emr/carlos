/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.utility.SafeEncode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class EpisodeFormEncodingUnitTest {
    @Test
    void shouldEncodeStoredTextBeforeEmbeddingItInEpisodeEditorJavaScript() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/episode/episodeForm.jsp"));
        for (String getter : new String[]{"getCode", "getStatus", "getDescription", "getCodingSystem"}) {
            assertThat(jsp).contains("SafeEncode.forJavaScript(episode." + getter + "())")
                    .doesNotContain("<%=episode." + getter + "()%>");
        }
        String list = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/episode/episodeList.jsp"));
        assertThat(list).contains("SafeEncode.forHtml(episode.getDescription())")
                .doesNotContain("<%=episode.getDescription() %>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"O'Neil follow-up", "');window.__episodePayloadExecuted=1;//",
            "</script><script>window.__episodePayloadExecuted=2</script>", "first\r\nsecond\u2028third\u2029fourth"})
    void shouldKeepStoredEpisodeTextWithinItsJavaScriptString(String value) {
        String encoded = SafeEncode.forJavaScript(value);
        assertThat(encoded).doesNotContain("'", "</script", "\r", "\n", "\u2028", "\u2029");
    }
}
