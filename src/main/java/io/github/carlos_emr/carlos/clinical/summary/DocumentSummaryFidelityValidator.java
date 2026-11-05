/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.clinical.summary;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline fidelity checks on a draft summary, beside the provenance checks in {@link DocumentSummaryService}
 * (prototype for the EN/FR evaluation, carlos#5537; nothing here calls a model, and none of it is wired
 * into {@code validate} yet). Each answers one error class that exact quotes and lexical overlap cannot
 * see, and each is a provenance rule on wording, not a clinical judgement:
 * <ul>
 * <li>{@link #addedPossessor}: the summary resolves an ambiguous possessor ("sa sœur") into the patient's
 * kin ("la sœur du patient") although the evidence never says "patient";</li>
 * <li>{@link #injectedCommandWord} / {@link #evidenceInInjectedLine}: the summary describes or quotes text
 * addressed to an AI that is embedded in the document ("SYSTÈME : ignorer la note..."), which the prompt
 * says to leave out;</li>
 * <li>{@link #languageMismatch}: the summary is written in another language than the document, decided
 * only when both texts carry enough function words to say (a one-line document usually does not).</li>
 * </ul>
 * The status-word rule ("prescribed", "scheduled", "administré") is already {@code DocumentSummaryService.statusWordWithoutSupport}.
 */
final class DocumentSummaryFidelityValidator {
    private DocumentSummaryFidelityValidator() {}

    private static final String KIN = "sisters?|brothers?|mothers?|fathers?|sons?|daughters?|spouses?|wives|wife|husbands?|partners?|parents?|"
            + "children|child|siblings?|aunts?|uncles?|cousins?|grandmothers?|grandfathers?|"
            + "s[oœ]e?urs?|frères?|freres?|mères?|meres?|pères?|peres?|fils|filles?|conjoint(?:e|s|es)?|époux|epoux|épouses?|epouses?|"
            + "enfants?|tantes?|oncles?|cousin(?:e|s|es)?|grand-mères?|grand-pères?";
    /** "the patient's sister", "the sister of the patient", "la sœur du patient / de la patiente". */
    private static final Pattern PATIENT_KIN = Pattern.compile(
            "\\b(?:patient['’]s\\s+(" + KIN + ")|(" + KIN + ")\\s+(?:of\\s+the\\s+patient|d[ue]\\s+patient|de\\s+la\\s+patiente|de\\s+l['’]\\s*patient(?:e)?))\\b");
    /** A possessive pronoun before a kin word: "sa sœur", "his sister". */
    private static final Pattern POSSESSIVE_KIN = Pattern.compile(
            "\\b(?:sa|son|ses|leurs?|ma|mon|mes|his|her|their|my|your)\\s+(" + KIN + ")\\b");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /**
     * The "patient's kin" phrase in {@code statement} that {@code reference} (the evidence, or the document for
     * an overview) does not support, else null. Only when the reference introduces that kin by a possessive
     * pronoun and never says "patient": a bare family-history heading ("Mother: diabetes") does make the
     * kin the patient's, so it is left alone.
     */
    static String addedPossessor(String statement, String reference) {
        String shown = fold(reference);
        if (shown.contains("patient")) return null;
        Matcher possessive = POSSESSIVE_KIN.matcher(shown);
        Set<String> possessed = new HashSet<>();
        while (possessive.find()) possessed.add(stem(possessive.group(1)));
        if (possessed.isEmpty()) return null;
        Matcher claim = PATIENT_KIN.matcher(fold(statement));
        while (claim.find()) {
            String kin = claim.group(1) != null ? claim.group(1) : claim.group(2);
            if (possessed.contains(stem(kin))) return claim.group();
        }
        return null;
    }

    private static String stem(String kin) {
        return kin.replace("œ", "oe").replace("é", "e").replace("è", "e").replaceAll("(?:es|e|s)$", "");
    }

    private static final Pattern AI_ADDRESSED = Pattern.compile(
            "^\\s*(?:(?:system|système|systeme|assistant|developer|developpeur|développeur|ai|ia|llm)\\s*[:>\\]-]"
                    + "|.*\\b(?:ignore|ignorer|ignorez|disregard|oublie|oubliez)\\b.*\\b(?:previous|prior|above|instructions?|consignes?|note|précédent|precedent)\\b.*"
                    + "|[{\\[].*\"(?:instructions?|system|prompt|role|consignes?)\"\\s*:.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** The lines of {@code source} that read as addressed to an AI. */
    static List<String> aiAddressedLines(String source) {
        return source.lines().filter(line -> AI_ADDRESSED.matcher(line).matches()).toList();
    }

    /**
     * A word of four letters or more that {@code statement} uses and the document uses only in AI-addressed
     * lines ("warfarine", "ignorer", "consignes"), else null.
     */
    static String injectedCommandWord(String statement, String source) {
        List<String> injected = aiAddressedLines(source);
        if (injected.isEmpty()) return null;
        Set<String> inInjected = new HashSet<>();
        Set<String> elsewhere = new HashSet<>();
        for (String line : source.lines().toList()) {
            (injected.contains(line) ? inInjected : elsewhere).addAll(words(line));
        }
        inInjected.removeAll(elsewhere);
        for (String word : words(statement)) if (inInjected.contains(word)) return word;
        return null;
    }

    /** True when an evidence excerpt lies inside a line addressed to an AI. */
    static boolean evidenceInInjectedLine(List<String> evidence, String source) {
        List<String> injected = aiAddressedLines(source);
        for (String excerpt : evidence) {
            for (String line : injected) if (line.contains(excerpt)) return true;
        }
        return false;
    }

    private static Set<String> words(String value) {
        var result = new HashSet<String>();
        Matcher matcher = WORD.matcher(fold(value));
        while (matcher.find()) if (matcher.group().length() >= 4) result.add(matcher.group());
        return result;
    }

    private static final Set<String> EN_FUNCTION = Set.of("the", "of", "and", "to", "is", "are", "was", "were", "in", "on", "for", "with",
            "that", "this", "it", "be", "has", "have", "at", "by", "from", "as", "not", "no", "if", "or", "an", "a");
    private static final Set<String> FR_FUNCTION = Set.of("le", "la", "les", "de", "des", "du", "et", "est", "sont", "un", "une", "dans",
            "pour", "avec", "que", "qui", "il", "elle", "au", "aux", "en", "par", "sur", "ne", "pas", "si", "ou", "se", "son", "sa", "ses", "l", "d");
    private static final int MIN_FUNCTION_WORDS = 3;

    /** "en", "fr", or null when the text has too few function words, or the two languages tie closely, to say. */
    static String language(String text) {
        Map<String, Integer> score = new HashMap<>(Map.of("en", 0, "fr", 0));
        Matcher matcher = WORD.matcher(fold(text));
        while (matcher.find()) {
            String word = matcher.group();
            if (EN_FUNCTION.contains(word)) score.merge("en", 1, Integer::sum);
            if (FR_FUNCTION.contains(word)) score.merge("fr", 1, Integer::sum);
        }
        int en = score.get("en");
        int fr = score.get("fr");
        if (Math.max(en, fr) < MIN_FUNCTION_WORDS || Math.max(en, fr) < 2 * Math.min(en, fr)) return null;
        return en > fr ? "en" : "fr";
    }

    /** True only when both texts have a clear language and they differ. */
    static boolean languageMismatch(String source, String summary) {
        String left = language(source);
        String right = language(summary);
        return left != null && right != null && !left.equals(right);
    }
}
