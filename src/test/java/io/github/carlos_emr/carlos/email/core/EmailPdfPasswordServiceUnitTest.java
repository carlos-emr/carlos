/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("should generate six lowercase hyphen-separated words")
    void shouldGenerateSixLowercaseHyphenSeparatedWords() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).matches("^[a-z]+(-[a-z]+){5}$");
        assertThat(passphrase.split("-")).hasSize(6);
    }

    @Test
    @DisplayName("should use secure random indexes to select words")
    void shouldUseSecureRandomIndexesToSelectWords() {
        EmailPdfPasswordService service = new EmailPdfPasswordService(testWords(4096), new FixedSecureRandom(0, 1, 2, 3, 4, 5));

        String passphrase = service.generatePassphrase();

        assertThat(passphrase).isEqualTo("worda-wordb-wordc-wordd-worde-wordf");
    }

    @Test
    @DisplayName("should load a large enough resource wordlist with more than 77 bits of entropy")
    void shouldLoadLargeEnoughResourceWordlistWithExpectedEntropy() {
        EmailPdfPasswordService service = new EmailPdfPasswordService();

        assertThat(service.getWordListSize()).isGreaterThanOrEqualTo(EmailPdfPasswordService.MIN_WORDLIST_SIZE);
        assertThat(service.getEntropyBits()).isGreaterThan(77.0);
    }

    @Test
    @DisplayName("should calculate entropy from wordlist size and word count")
    void shouldCalculateEntropyFromWordlistSizeAndWordCount() {
        double entropy = EmailPdfPasswordService.calculateEntropyBits(7776, 6);

        assertThat(entropy).isCloseTo(77.55, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("should reject a wordlist below the minimum size")
    void shouldRejectWordlistBelowMinimumSize() {
        assertThatThrownBy(() -> new EmailPdfPasswordService(testWords(4095), new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 4096");
    }

    @Test
    @DisplayName("should reject non-lowercase ASCII words")
    void shouldRejectInvalidWords() {
        List<String> words = testWords(4096);
        words.set(100, "two-words");

        assertThatThrownBy(() -> new EmailPdfPasswordService(words, new FixedSecureRandom()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid word");
    }

    @Test
    @DisplayName("should reject duplicate words")
    void shouldRejectDuplicateWords() {
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
