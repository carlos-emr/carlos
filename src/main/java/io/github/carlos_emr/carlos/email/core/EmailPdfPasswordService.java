/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Generates server-assigned passphrases for password-protected patient email PDFs.
 *
 * <p>The wordlist is intentionally public. Security comes from uniformly selecting
 * enough words and digits with {@link SecureRandom}, not from wordlist secrecy.
 * The 4096-word list contributes 12 bits per word, and the six random digits add
 * nearly 20 bits, so the default two-word, three-digit, two-word, three-digit
 * passphrase provides about 68 bits of entropy.</p>
 *
 * @since 2026-07-14
 */
@Service
public class EmailPdfPasswordService {
    static final String WORDLIST_RESOURCE = "/email/patient_pdf_passphrase_wordlist.txt";
    static final int MIN_WORDLIST_SIZE = 4096;
    static final int DEFAULT_WORD_COUNT = 4;
    static final int WORD_GROUP_COUNT = 2;
    static final int DEFAULT_DIGIT_COUNT = 6;
    static final int DIGIT_GROUP_COUNT = 3;
    static final int DIGIT_BOUND = 10;
    static final String SEPARATOR = "-";
    static final Pattern WORD_PATTERN = Pattern.compile("[a-z]+");

    public static final String DELIVERY_INSTRUCTION =
            "Deliver this password to the patient separately. It is not included in the email.";

    private final List<String> words;
    private final SecureRandom secureRandom;

    /**
     * Creates a service using the bundled patient PDF passphrase wordlist.
     *
     * @since 2026-07-14
     */
    public EmailPdfPasswordService() {
        this(loadWordsFromResource(WORDLIST_RESOURCE), new SecureRandom());
    }

    EmailPdfPasswordService(List<String> words, SecureRandom secureRandom) {
        this.words = List.copyOf(validateWords(words));
        this.secureRandom = secureRandom;
    }

    /**
     * Generates a random PDF passphrase from the validated wordlist and two three-digit groups.
     *
     * @return passphrase formatted as {@code word-word-###-word-word-###}
     * @since 2026-07-14
     */
    public String generatePassphrase() {
        List<String> parts = new ArrayList<>(DEFAULT_WORD_COUNT + DEFAULT_DIGIT_COUNT / DIGIT_GROUP_COUNT);
        addRandomWords(parts, WORD_GROUP_COUNT);
        parts.add(generateDigitGroup(DIGIT_GROUP_COUNT));
        addRandomWords(parts, WORD_GROUP_COUNT);
        parts.add(generateDigitGroup(DIGIT_GROUP_COUNT));
        return String.join(SEPARATOR, parts);
    }

    private void addRandomWords(List<String> parts, int count) {
        for (int i = 0; i < count; i++) {
            parts.add(words.get(secureRandom.nextInt(words.size())));
        }
    }

    private String generateDigitGroup(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append(secureRandom.nextInt(DIGIT_BOUND));
        }
        return digits.toString();
    }

    /**
     * Gets the number of words available for passphrase generation.
     *
     * @return validated wordlist size
     * @since 2026-07-14
     */
    public int getWordListSize() {
        return words.size();
    }

    /**
     * Gets the entropy for the default generated passphrase format.
     *
     * @return entropy bits for the configured wordlist size and default word count
     * @since 2026-07-14
     */
    public double getEntropyBits() {
        return calculateEntropyBits(words.size(), DEFAULT_WORD_COUNT, DEFAULT_DIGIT_COUNT);
    }

    /**
     * Calculates entropy for uniformly selected words.
     *
     * @param wordListSize number of candidate words
     * @param wordCount number of words selected for the passphrase
     * @return entropy bits for the given wordlist size and word count
     * @since 2026-07-14
     */
    public static double calculateEntropyBits(int wordListSize, int wordCount) {
        if (wordListSize < 1 || wordCount < 1) {
            throw new IllegalArgumentException("Word list size and word count must be positive");
        }
        return wordCount * (Math.log(wordListSize) / Math.log(2));
    }

    /**
     * Calculates entropy for uniformly selected words plus uniformly selected decimal digits.
     *
     * @param wordListSize number of candidate words
     * @param wordCount number of words selected for the passphrase
     * @param digitCount number of decimal digits appended to the passphrase
     * @return entropy bits for the given wordlist size, word count, and digit count
     * @since 2026-07-21
     */
    public static double calculateEntropyBits(int wordListSize, int wordCount, int digitCount) {
        if (digitCount < 0) {
            throw new IllegalArgumentException("Digit count must not be negative");
        }
        return calculateEntropyBits(wordListSize, wordCount)
                + digitCount * (Math.log(DIGIT_BOUND) / Math.log(2));
    }

    private static List<String> loadWordsFromResource(String resourcePath) {
        InputStream stream = EmailPdfPasswordService.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing email PDF password wordlist resource: " + resourcePath);
        }

        List<String> loadedWords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmedLine.split("\\s+");
                loadedWords.add(parts[parts.length - 1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read email PDF password wordlist resource: " + resourcePath, e);
        }
        return loadedWords;
    }

    private static List<String> validateWords(List<String> words) {
        if (words == null || words.size() < MIN_WORDLIST_SIZE) {
            throw new IllegalStateException("Email PDF password wordlist must contain at least " + MIN_WORDLIST_SIZE + " words");
        }

        Set<String> uniqueWords = new LinkedHashSet<>();
        for (String word : words) {
            if (word == null || !WORD_PATTERN.matcher(word).matches()) {
                throw new IllegalStateException("Email PDF password wordlist contains an invalid word: " + word);
            }
            if (!uniqueWords.add(word)) {
                throw new IllegalStateException("Email PDF password wordlist contains a duplicate word: " + word);
            }
        }
        return new ArrayList<>(uniqueWords);
    }
}
