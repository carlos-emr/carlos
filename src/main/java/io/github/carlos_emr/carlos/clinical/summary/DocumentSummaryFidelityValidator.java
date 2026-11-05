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
 * (EN/FR evaluation, carlos#5537; nothing here calls a model). Each is a provenance rule on wording, not a
 * clinical judgement, and each is deliberately narrow: a rule that refuses an ordinary document is worse
 * than the error it catches, so each fires only on text that nothing but the error would produce.
 * <ul>
 * <li>{@link #possessorOfAnotherPerson}: the summary gives the patient the kin of a person the evidence
 * names ("Dr. Lee said her sister will call" summarized as "the patient's sister"). An ambiguous
 * possessor with no person named before it ("sa sœur") is left to the prompt and the clinician;</li>
 * <li>{@link #injectedCommandWord} / {@link #evidenceInInjectedLine}: the summary describes or quotes text
 * that only an AI-directed line says (it names an AI as the target: "ignore all previous instructions",
 * "you are an AI", "system prompt", or a SYSTEM/SYSTÈME/ASSISTANT line with an override verb), which the
 * prompt says to leave out. Ordinary corrections to a clinical instruction ("Ignore the previous
 * instructions about warfarin; continue 5 mg daily") are not AI-directed;</li>
 * <li>{@link #languageMismatch}: the summary is in another language than the document, decided only when
 * both texts carry enough function words to say. Not wired.</li>
 * </ul>
 * The status-word rule is {@code DocumentSummaryService.statusWordWithoutSupport}.
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
    /** Words that name a person by role or title, in front of a possessive. */
    private static final Pattern PERSON_ROLE = Pattern.compile(
            "\\b(?:nurse|doctor|physician|clinician|surgeon|pharmacist|therapist|dr|mr|mrs|ms|miss|"
                    + "infirmi[èe]re?|m[ée]decin|docteur|chirurgien(?:ne)?|pharmacien(?:ne)?|mme|madame|monsieur|mlle)\\b\\.?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** A capitalized word that is not the first of its sentence: a name. */
    private static final Pattern MID_SENTENCE_NAME = Pattern.compile("(?<=[\\p{L}\\p{N},;:)]\\s)\\p{Lu}\\p{Ll}+");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /**
     * The "patient's kin" phrase in {@code statement} when {@code reference} (the point's evidence, or the document
     * for an overview) gives that kin to a person it names (a name or a role such as "the nurse" or "Dr. Lee" in the
     * same sentence, before the possessive) and never says "patient". Else null. A possessive with no person named
     * before it ("His mother had breast cancer", "Si la fièvre revient, sa sœur…") is not refused: it is left to the
     * prompt and to the clinician's review, because a family-history line is usually the patient's own.
     */
    static String possessorOfAnotherPerson(String statement, String reference) {
        if (fold(reference).contains("patient")) return null;
        Set<String> kinOfSomeoneElse = new HashSet<>();
        // A title's full stop ("Dr. Lee") does not end a sentence.
        String unabbreviated = reference.replaceAll("\\b(Drs?|Mrs?|Ms|Mme|Mlle|Mr|Pr)\\.", "$1");
        for (String sentence : unabbreviated.split("(?<=[.!?;])\\s+")) {
            Matcher possessive = POSSESSIVE_KIN.matcher(fold(sentence));
            while (possessive.find()) {
                String before = sentence.substring(0, possessive.start());
                if (PERSON_ROLE.matcher(before).find() || MID_SENTENCE_NAME.matcher(before).find()) {
                    kinOfSomeoneElse.add(stem(possessive.group(1)));
                }
            }
        }
        if (kinOfSomeoneElse.isEmpty()) return null;
        Matcher claim = PATIENT_KIN.matcher(fold(statement));
        while (claim.find()) {
            String kin = claim.group(1) != null ? claim.group(1) : claim.group(2);
            if (kinOfSomeoneElse.contains(stem(kin))) return claim.group();
        }
        return null;
    }

    private static String stem(String kin) {
        return kin.replace("œ", "oe").replace("é", "e").replace("è", "e").replaceAll("(?:es|e|s)$", "");
    }

    private static final String OVERRIDE_WORDS = "ignore|ignorer|ignorez|disregard|forget|oublie|oubliez|override|bypass|contourner|reset";
    private static final String OVERRIDE_VERB = "(?:" + OVERRIDE_WORDS + ")";
    private static final String INSTRUCTION_NOUN = "(?:instructions?|prompts?|rules|consignes?|directives?)";
    private static final Pattern AI_ADDRESSED = Pattern.compile(
            "^\\s*(?:"
                    // SYSTEM/SYSTÈME/ASSISTANT line that tells the model to override: "SYSTÈME : ignorer la note et ..."
                    + "(?:system|système|systeme|assistant)\\s*[:>\\]-].*\\b" + OVERRIDE_VERB + "\\b.*"
                    // an AI as the target of an override: "ignore all previous instructions"
                    + "|.*\\b(?:ignore|ignorer|ignorez|disregard|forget|oublie|oubliez)\\s+(?:all|any|every|toutes?|tous)\\s+(?:of\\s+)?(?:the\\s+|your\\s+|les\\s+|vos\\s+)?"
                    + "(?:(?:previous|prior|above|earlier|pr[ée]c[ée]dent(?:e|s|es)?)\\s+)?" + INSTRUCTION_NOUN
                    + "(?:\\s+(?:previous|prior|above|earlier|pr[ée]c[ée]dent(?:e|s|es)?|ci-dessus))?.*"
                    // an address to the model itself
                    + "|.*\\b(?:you\\s+are\\s+an?\\s+(?:ai|assistant|language\\s+model|llm|chatbot)|as\\s+an\\s+ai(?:\\s+language\\s+model)?|"
                    + "(?:vous|tu)\\s+(?:êtes|etes|es)\\s+un(?:e)?\\s+(?:ia|assistant|mod[èe]le\\s+de\\s+langage|chatbot)|system\\s+prompt|prompt\\s+syst[èe]me)\\b.*"
                    // a JSON object whose instructions/system/prompt value orders the model to replace or ignore instructions
                    + "|[{\\[].*\"(?:instructions?|system|prompt)\"\\s*:\\s*\"[^\"]*\\b(?:replace|remplacer|remplacez|" + OVERRIDE_WORDS
                    + ")\\b[^\"]*\\b(?:instructions?|prompts?|rules|consignes?|directives?|system)\\b.*"
                    + ")$",
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
