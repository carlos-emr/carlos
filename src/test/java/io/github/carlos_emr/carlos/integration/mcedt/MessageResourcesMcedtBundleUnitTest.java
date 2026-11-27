/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the MCEDT update-page messages in {@code MessageResources_mcedt.properties}, added with the
 * update-page refusals and the partial-send outcome (#6326, release 2026.08; part of #6336 gap tests).
 * {@code Update2ActionUploadUnitTest} stubs {@code getText}, so it never proves the bundle carries the
 * keys; a missing or misspelled entry would show its raw key on the update page.
 */
@Tag("unit")
@Tag("mcedt")
@DisplayName("MessageResources_mcedt bundle: MCEDT update messages")
class MessageResourcesMcedtBundleUnitTest {

    private static final Path BUNDLE = Path.of("src/main/resources/MessageResources_mcedt.properties");
    private static final Path ACTION =
            Path.of("src/main/java/io/github/carlos_emr/carlos/integration/mcedt/Update2Action.java");

    private static Properties bundle() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(BUNDLE, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    @DisplayName("should carry non-blank text for every update message the action asks for")
    void shouldCarryNonBlankText_forEveryUpdateMessage() throws IOException {
        Properties bundle = bundle();
        for (String key : List.of(
                "updateAction.addUpdateRequest.missingFile",
                "updateAction.addUpdateRequest.unknownResource",
                "updateAction.sendUpdateRequest.inProgress",
                "updateAction.sendUpdateRequest.partial",
                "updateAction.sendUpdateRequest.emptyUpdates",
                "updateAction.sendUpdateRequest.failure",
                "updateAction.sendUpdateRequest.success")) {
            assertThat(bundle.getProperty(key)).as(key).isNotBlank();
        }
    }

    @Test
    @DisplayName("should fill all three arguments of the partial-send message")
    void shouldFillAllThreeArguments_ofPartialSendMessage() throws IOException {
        String pattern = bundle().getProperty("updateAction.sendUpdateRequest.partial");
        String text = MessageFormat.format(pattern, "1", "2", "detail-FAKE");
        assertThat(text).contains("1", "2", "detail-FAKE");
        assertThat(text).doesNotContain("{").doesNotContain("}");
    }

    @Test
    @DisplayName("should keep the refusal messages free of placeholders")
    void shouldKeepRefusalMessagesPlain_withoutPlaceholders() throws IOException {
        Properties bundle = bundle();
        for (String key : List.of(
                "updateAction.sendUpdateRequest.inProgress",
                "updateAction.addUpdateRequest.missingFile",
                "updateAction.addUpdateRequest.unknownResource")) {
            assertThat(bundle.getProperty(key)).as(key).doesNotContain("{");
        }
    }

    @Test
    @DisplayName("should resolve every getText literal in Update2Action from the bundle")
    void shouldResolveEveryGetTextLiteral_inUpdate2Action() throws IOException {
        Properties bundle = bundle();
        String source = Files.readString(ACTION, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("getText\\(\\s*\"([^\"]+)\"").matcher(source);
        List<String> found = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
            if (bundle.getProperty(matcher.group(1)) == null) {
                unresolved.add(matcher.group(1));
            }
        }
        assertThat(found).as("getText literals found in Update2Action").isNotEmpty();
        assertThat(unresolved).as("keys missing from MessageResources_mcedt.properties").isEmpty();
    }
}
