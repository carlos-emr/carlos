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
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Issue #5897: 4xx refusals carry a user-facing reason the error page renders.
 *
 * @since 2026-11-19
 */
@Tag("unit")
@DisplayName("Refusal notice")
class RefusalNoticeUnitTest {

    @Test
    void shouldPublishKeyAndSendStatus_forClientError() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        RefusalNotice.send(request, response, 410, "error.refusal.legacyEncounterEditorRetired", "retired");

        assertThat(request.getAttribute(RefusalNotice.KEY_ATTRIBUTE)).isEqualTo("error.refusal.legacyEncounterEditorRetired");
        assertThat(response.getStatus()).isEqualTo(410);
        assertThat(response.getErrorMessage()).isEqualTo("retired");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 302, 399, 500, 503})
    void shouldRefuseToPublish_forStatusOutsideClientErrors(int status) {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> RefusalNotice.send(request, response, status, "error.refusal.reportAccess", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getAttribute(RefusalNotice.KEY_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldHaveEveryPublishedKey_inEachBundle() throws Exception {
        // Every key passed to RefusalNotice.send in production code must resolve in each locale.
        Pattern sent = Pattern.compile("\"(error\\.refusal\\.[A-Za-z]+)\"");
        java.util.Set<String> keys = new java.util.TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                if (!text.contains("RefusalNotice.send(")) {
                    continue;
                }
                Matcher matcher = sent.matcher(text);
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        assertThat(keys).isNotEmpty();
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.of("pl"), Locale.of("pt", "BR")}) {
            ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);
            for (String key : keys) {
                assertThat(bundle.containsKey(key)).as("%s in %s", key, locale).isTrue();
            }
        }
    }

    @Test
    void shouldRenderTheReason_onlyForClientErrorsOnTheErrorPage() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/error/errorpage.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp).contains("${not empty carlosRefusalKey and _responseStatus >= 400 and _responseStatus < 500}")
                .contains("<fmt:message key=\"${carlosRefusalKey}\"/>");
    }
}
