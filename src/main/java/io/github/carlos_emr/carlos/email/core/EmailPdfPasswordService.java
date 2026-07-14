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
 * enough words with {@link SecureRandom}, not from wordlist secrecy. The 4096-word
 * list contributes 12 bits per word, so the default seven-word passphrase provides
 * 84 bits of entropy.</p>
 */
@Service
public class EmailPdfPasswordService {
    static final String WORDLIST_RESOURCE = "/email/patient_pdf_passphrase_wordlist.txt";
    static final int MIN_WORDLIST_SIZE = 4096;
    static final int DEFAULT_WORD_COUNT = 7;
    static final String SEPARATOR = "-";
    static final Pattern WORD_PATTERN = Pattern.compile("[a-z]+");

    public static final String DELIVERY_INSTRUCTION =
            "Deliver this password to the patient separately. It is not included in the email.";

    private final List<String> words;
    private final SecureRandom secureRandom;

    public EmailPdfPasswordService() {
        this(loadWordsFromResource(WORDLIST_RESOURCE), new SecureRandom());
    }

    EmailPdfPasswordService(List<String> words, SecureRandom secureRandom) {
        this.words = List.copyOf(validateWords(words));
        this.secureRandom = secureRandom;
    }

    public String generatePassphrase() {
        List<String> selectedWords = new ArrayList<>(DEFAULT_WORD_COUNT);
        for (int i = 0; i < DEFAULT_WORD_COUNT; i++) {
            selectedWords.add(words.get(secureRandom.nextInt(words.size())));
        }
        return String.join(SEPARATOR, selectedWords);
    }

    public int getWordListSize() {
        return words.size();
    }

    public double getEntropyBits() {
        return calculateEntropyBits(words.size(), DEFAULT_WORD_COUNT);
    }

    public static double calculateEntropyBits(int wordListSize, int wordCount) {
        if (wordListSize < 1 || wordCount < 1) {
            throw new IllegalArgumentException("Word list size and word count must be positive");
        }
        return wordCount * (Math.log(wordListSize) / Math.log(2));
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
