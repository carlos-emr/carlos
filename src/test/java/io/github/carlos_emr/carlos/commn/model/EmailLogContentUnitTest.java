/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Nullable historical email text must remain readable when copying or recovering a send. */
@Tag("unit")
class EmailLogContentUnitTest {

    static Stream<Arguments> contentFields() {
        return Stream.of(
                Arguments.of("body", (Function<EmailLog, String>) EmailLog::getBody,
                        (BiConsumer<EmailLog, String>) EmailLog::setBody),
                Arguments.of("encryptedMessage", (Function<EmailLog, String>) EmailLog::getEncryptedMessage,
                        (BiConsumer<EmailLog, String>) EmailLog::setEncryptedMessage),
                Arguments.of("internalComment", (Function<EmailLog, String>) EmailLog::getInternalComment,
                        (BiConsumer<EmailLog, String>) EmailLog::setInternalComment));
    }

    @ParameterizedTest(name = "{0}: nullable stored content and UTF-8 compatibility")
    @MethodSource("contentFields")
    void shouldReadNullableAndEncodedContent_forSupportedFields(String field, Function<EmailLog, String> read,
            BiConsumer<EmailLog, String> write) {
        EmailLog log = new EmailLog();
        // Simulate JPA hydration from the nullable BLOB column, independently of setters.
        ReflectionTestUtils.setField(log, field, null);
        assertThat(read.apply(log)).isEmpty();
        ReflectionTestUtils.setField(log, field, new byte[0]);
        assertThat(read.apply(log)).isEmpty();
        // Existing logs store Base64 of UTF-8; keep that persisted format unchanged.
        ReflectionTestUtils.setField(log, field, "Y2Fmw6k=".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(read.apply(log)).isEqualTo("café");
        write.accept(log, "Synthetic résumé 日本語");
        assertThat(read.apply(log)).isEqualTo("Synthetic résumé 日本語");
        write.accept(log, null);
        assertThat(read.apply(log)).isEmpty();
        assertThat(ReflectionTestUtils.getField(log, field)).isNull();
    }
}
