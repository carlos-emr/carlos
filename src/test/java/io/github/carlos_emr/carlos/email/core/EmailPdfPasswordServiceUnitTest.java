/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("security")
@DisplayName("EmailPdfPasswordService")
class EmailPdfPasswordServiceUnitTest {
    private static final String PATIENT_UNFRIENDLY_REVIEW_WORDS_RESOURCE =
            "/email/patient_unfriendly_review_words.txt";

    @Test
    @DisplayName("should generate two words, three digits, two words, and three digits")
    void shouldGeneratePassphrase_withGroupedWordsAndDigits() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).matches("^[a-z]+-[a-z]+-\\d{3}-[a-z]+-[a-z]+-\\d{3}$");
        assertThat(passphrase.split("-")).hasSize(6);
    }

    @Test
    @DisplayName("should use secure random values to select words and digits")
    void shouldUseSecureRandomValues_toSelectWordsAndDigits() {
        EmailPdfPasswordService service = new EmailPdfPasswordService(
                testWords(4096),
                new FixedSecureRandom(0, 1, 0, 1, 2, 2, 3, 3, 4, 5));

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).isEqualTo("worda-wordb-012-wordc-wordd-345");
    }

    @Test
    @DisplayName("should load a 4096 word resource wordlist with about 68 bits of entropy")
    void shouldLoadLargeEnoughResourceWordlist_withExpectedEntropy() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        assertThat(service.getWordListSize()).isEqualTo(4096);
        assertThat(service.getEntropyBits()).isCloseTo(67.93, within(0.01));
    }

    @Test
    @DisplayName("should not include patient-facing sensitive review words")
    void shouldExcludeSensitiveWords_fromWordlist() throws Exception {
        String[] blockedWords = resourceText(PATIENT_UNFRIENDLY_REVIEW_WORDS_RESOURCE).trim().split("\\s+");

        assertThat(resourceWords()).doesNotContain(blockedWords);
    }

    @Test
    @DisplayName("should calculate entropy from wordlist size, word count, and digit count")
    void shouldCalculateEntropy_fromWordlistSizeWordCountAndDigitCount() {
        double entropy = EmailPdfPasswordService.calculateEntropyBits(4096, 4, 6);

        assertThat(entropy).isCloseTo(67.93, within(0.01));
    }

    @Test
    @DisplayName("should reject a wordlist below the minimum size")
    void shouldRejectWordlist_belowMinimumSize() {
        assertThatThrownBy(() -> new EmailPdfPasswordService(testWords(4095), new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 4096");
    }

    @Test
    @DisplayName("should reject non-lowercase ASCII words")
    void shouldRejectInvalidWords_whenLoading() {
        List<String> words = testWords(4096);
        words.set(100, "two-words");

        assertThatThrownBy(() -> new EmailPdfPasswordService(words, new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid word");
    }

    @Test
    @DisplayName("should reject duplicate words")
    void shouldRejectDuplicateWords_whenLoading() {
        List<String> words = testWords(4096);
        words.set(100, words.get(99));

        assertThatThrownBy(() -> new EmailPdfPasswordService(words, new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate word");
    }

    private static List<String> testWords(int count) {
        List<String> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            words.add("word" + toLetters(i));
        }
        return words;
    }

    private static String toLetters(int value) {
        StringBuilder builder = new StringBuilder();
        do {
            builder.append((char) ('a' + (value % 26)));
            value = value / 26;
        } while (value > 0);
        return builder.toString();
    }

    private static List<String> resourceWords() throws Exception {
        InputStream stream = EmailPdfPasswordService.class.getResourceAsStream(
                EmailPdfPasswordService.WORDLIST_RESOURCE);
        assertThat(stream).isNotNull();

        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmedLine.split("\\t");
                assertThat(parts).hasSize(2);
                assertThat(parts[0]).matches("\\d{4}");
                assertThat(parts[1]).matches("[a-z]+");
                words.add(parts[1]);
            }
        }
        return words;
    }

    private static String resourceText(String resource) throws Exception {
        InputStream stream = EmailPdfPasswordServiceUnitTest.class.getResourceAsStream(resource);
        assertThat(stream).isNotNull();
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private final int[] values;
        private int index;

        private FixedSecureRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            if (values.length == 0) {
                return 0;
            }
            int value = values[index % values.length];
            index++;
            return value % bound;
        }
    }
}
