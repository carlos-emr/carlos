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
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.commn.model.converter.EmailLogStatusConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Manage Emails status tags and card text: every word comes from the bundle.
 *
 * <p>{@code SUCCESS} is written when the SMTP relay or email API accepts the message, which is
 * not delivery: a later bounce is never recorded. Manage Emails must therefore show it as
 * "ACCEPTED BY MAIL SERVER" (issue #3834), in a neutral grey ({@code status-tag-accepted}) rather
 * than the green of RESOLVED. Every other status tag, the status filter, and the card's labels and
 * buttons are worded from the bundle too, so the page reads fully in French and the other shipped
 * languages. The enum constants, the stored values and the filter option values stay the raw
 * status, and so do the CSS classes, except that SUCCESS's tag uses {@code status-tag-accepted}.</p>
 *
 * @since 2026-09-29
 */
@DisplayName("Manage emails status labels and card text")
@Tag("unit")
@Tag("email")
class ManageEmailsStatusLabelJspRegressionTest {

    private static final Path EMAIL_STATUS_RESULTS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/emailStatusResults.jspf");
    private static final Path MANAGE_EMAILS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/manageEmails.jsp");
    private static final String ACCEPTED_LABEL_KEY = "admin.manageEmails.acceptedByMailServer";
    private static final String RESOLVED_LABEL_KEY = "admin.manageEmails.resolved";
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pt_BR", "pl");
    /** Legitimately the same word as English in these languages. */
    private static final Map<String, Set<String>> SAME_AS_ENGLISH = Map.of(
            "admin.manageEmails.status", Set.of("pl", "pt_BR"));
    private static final Pattern MESSAGE_KEY = Pattern.compile("key=[\"']([A-Za-z0-9_.]+)[\"']");

    @Test
    @DisplayName("status tag should be worded from each status's bundle key, keeping the raw status for the CSS class")
    void shouldWordStatusTagFromBundle_inStatusResultsTag() throws IOException {
        String jsp = Files.readString(EMAIL_STATUS_RESULTS_JSP, StandardCharsets.UTF_8);

        // One block: the label from the status's key, the class from the raw status, and the grey
        // class ONLY inside the SUCCESS test. Separate contains() checks would still pass if the
        // class override moved outside the c:if and every row turned grey.
        assertThat(jsp).containsPattern(Pattern.compile(
                "<c:set var=\"emailStatusLabel\" value=\"\"/>\\s*"
                        + "<c:if test=\"\\$\\{not empty emailStatusResult\\.status}\">\\s*"
                        + "<fmt:message key=\"\\$\\{emailStatusResult\\.status\\.messageKey}\" var=\"emailStatusLabel\"/>\\s*"
                        + "</c:if>\\s*"
                        + "<c:set var=\"emailStatusClass\" value=\"status-tag-\\$\\{fn:toLowerCase\\(emailStatusResult\\.status\\)}\"/>\\s*"
                        + "<c:if test=\"\\$\\{emailStatusResult\\.status eq 'SUCCESS'}\">\\s*"
                        + "<c:set var=\"emailStatusClass\" value=\"status-tag-accepted\"/>\\s*"
                        + "</c:if>\\s*<!-- Email Card -->"));
        assertThat(jsp)
                .contains("${carlos:forHtml(emailStatusLabel)}")
                .contains("class=\"status-tag ${emailStatusClass}\"")
                .contains("vertical-status-divider-${fn:toLowerCase(emailStatusResult.status)}")
                .doesNotContain("${carlos:forHtml(emailStatusResult.status)}")
                .doesNotContain("<c:set var=\"emailStatusLabel\" value=\"${emailStatusResult.status}\"/>");
    }

    @Test
    @DisplayName("status filter should be worded from each status's bundle key but submit the raw status")
    void shouldWordStatusFilterFromBundle_butSubmitRawStatus() throws IOException {
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern(Pattern.compile(
                "<fmt:message key=\"\\$\\{status\\.messageKey}\" var=\"statusLabel\"/>\\s*"
                        + "<option value=\"\\$\\{ status }\">\\s*"
                        + "\\$\\{carlos:forHtml\\(statusLabel\\)}\\s*</option>"));
        assertThat(jsp)
                .doesNotContain("<c:set var=\"statusLabel\" value=\"${status}\"/>")
                .doesNotContain("${carlos:forHtml(status)}");
    }

    @Test
    @DisplayName("each status should have its own tag key, RESOLVED the same one the page's Resolve uses")
    void shouldMapEveryStatusToItsTagKey_resolvedMatchingResolveScript() throws IOException {
        assertThat(EmailStatus.SUCCESS.getMessageKey()).isEqualTo(ACCEPTED_LABEL_KEY);
        assertThat(EmailStatus.RESOLVED.getMessageKey()).isEqualTo(RESOLVED_LABEL_KEY);
        assertThat(EmailStatus.PENDING.getMessageKey()).isEqualTo("admin.manageEmails.pending");
        assertThat(EmailStatus.FAILED.getMessageKey()).isEqualTo("admin.manageEmails.failed");
        assertThat(EmailStatus.BLOCKED.getMessageKey()).isEqualTo("admin.manageEmails.blocked");
        Set<String> keys = new LinkedHashSet<>();
        for (EmailStatus status : EmailStatus.values()) {
            keys.add(status.getMessageKey());
        }
        assertThat(keys).as("one key per status").hasSize(EmailStatus.values().length);

        // A first-load RESOLVED tag must read as the tag the page's own Resolve writes ("R\u00c9SOLU").
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);
        assertThat(jsp).contains("const manageEmailsResolved = \"<fmt:message key='" + RESOLVED_LABEL_KEY + "'/>\";");
        assertThat(loadBundle("fr").getProperty(RESOLVED_LABEL_KEY)).isEqualTo("R\u00c9SOLU");
    }

    @Test
    @DisplayName("every status tag should be translated, in capitals, in every shipped locale")
    void shouldTranslateEveryStatusTag_inCapitals_forEveryLocale() throws IOException {
        Properties english = loadBundle("en");
        assertThat(english.getProperty(ACCEPTED_LABEL_KEY)).isEqualTo("ACCEPTED BY MAIL SERVER");
        // Exact capitals where accents could be lost when upper-casing by hand.
        assertThat(loadBundle("fr").getProperty(ACCEPTED_LABEL_KEY)).isEqualTo("ACCEPT\u00c9 PAR LE SERVEUR DE COURRIEL");
        assertThat(loadBundle("pl").getProperty(ACCEPTED_LABEL_KEY)).isEqualTo("PRZYJ\u0118TY PRZEZ SERWER POCZTOWY");
        assertThat(loadBundle("fr").getProperty("admin.manageEmails.failed")).isEqualTo("\u00c9CHEC");
        assertThat(loadBundle("pl").getProperty("admin.manageEmails.pending")).isEqualTo("OCZEKUJ\u0104CY");
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            for (EmailStatus status : EmailStatus.values()) {
                String key = status.getMessageKey();
                String label = bundle.getProperty(key);
                assertThat(label).as("oscarResources_%s.properties should define %s", locale, key).isNotBlank();
                assertThat(label).as("oscarResources_%s.properties writes %s in capitals", locale, key)
                        .isEqualTo(label.toUpperCase(Locale.forLanguageTag(locale.replace('_', '-'))));
                if (!"en".equals(locale)) {
                    // Translated, not an English placeholder (the bundles' translation rule).
                    assertThat(label).as("oscarResources_%s.properties translates %s", locale, key)
                            .isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    @Test
    @DisplayName("email card should have no hard-coded words in its text, visible attributes or EL: all from the bundle")
    void shouldHaveNoHardCodedWords_onEmailCard() throws IOException {
        String jsp = Files.readString(EMAIL_STATUS_RESULTS_JSP, StandardCharsets.UTF_8);

        assertThat(wordsOutsideMarkup(jsp)).as("visible text written into emailStatusResults.jspf").isEmpty();
        assertThat(jsp)
                .contains("<fmt:message key=\"admin.manageEmails.card.subject\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.from\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.to\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.encryption\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.encrypted\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.unencrypted\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.resolve\"/>")
                .contains("<fmt:message key=\"admin.manageEmails.card.copyAsNewEmail\"/>")
                // fmt:message does not encode a parameter, so the provider's name must be encoded.
                .containsPattern(Pattern.compile("<fmt:message key=\"admin\\.manageEmails\\.card\\.sentBy\">\\s*"
                        + "<fmt:param value=\"\\$\\{carlos:forHtml\\(emailStatusResult\\.providerFullName\\)}\"/>\\s*"
                        + "</fmt:message>"));
    }

    @Test
    @DisplayName("hard-coded word check should catch text, visible attributes and EL words, but not keys or enum names")
    void shouldCatchPlantedWords_butNotKeysOrEnumNames_inHardCodedWordCheck() {
        assertThat(wordsOutsideMarkup("<th>Subject</th>")).containsExactly("Subject");
        assertThat(wordsOutsideMarkup("<button title=\"Resolve this email\"><i></i></button>"))
                .containsExactly("Resolve", "this", "email");
        assertThat(wordsOutsideMarkup("<input type=\"text\" placeholder=\"Search\"/>")).containsExactly("Search");
        assertThat(wordsOutsideMarkup("<td>${enc ? 'Encrypted' : ''}</td>")).containsExactly("Encrypted");
        assertThat(wordsOutsideMarkup("<%-- Subject --%><!-- Subject --><th><fmt:message key=\"a.b\"/></th>"
                + "<input placeholder=\"<fmt:message key='a.c'/>\"/>"
                + "<c:if test=\"${status eq 'SUCCESS'}\"><c:set var=\"x\" value=\"status-tag-accepted\"/></c:if>"
                + "<input type=\"hidden\" name=\"method\" value=\"searchEmails\"/>&lt;&#x2022;"))
                .isEmpty();
    }

    @Test
    @DisplayName("Manage Emails page body should have no hard-coded words in its text, visible attributes or EL")
    void shouldHaveNoHardCodedWords_onManageEmailsPageBody() throws IOException {
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);
        int bodyStart = jsp.indexOf("<body>");
        int bodyEnd = jsp.indexOf("</body>");
        assertThat(bodyStart).as("manageEmails.jsp has a plain <body> tag").isNotNegative();
        assertThat(bodyEnd).as("manageEmails.jsp has a </body> tag").isGreaterThan(bodyStart);
        String body = jsp.substring(bodyStart, bodyEnd);

        assertThat(wordsOutsideMarkup(body)).as("visible text written into the manageEmails.jsp body").isEmpty();
    }

    @Test
    @DisplayName("every key the page and card show should be defined, and translated, in every shipped locale")
    void shouldTranslateEveryShownKey_forEveryLocale() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(literalMessageKeys(Files.readString(EMAIL_STATUS_RESULTS_JSP, StandardCharsets.UTF_8)));
        keys.addAll(literalMessageKeys(Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8)));
        // Keys the JSPs reach through an expression rather than a literal.
        for (EmailStatus status : EmailStatus.values()) {
            keys.add(status.getMessageKey());
        }
        for (EmailConsentStatus status : EmailConsentStatus.values()) {
            keys.add(status.getMessageKey());
        }
        keys.add("email.consent.status.notRecorded");

        Properties english = loadBundle("en");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            for (String key : keys) {
                String value = bundle.getProperty(key);
                if (value == null || value.isBlank()) {
                    problems.add(locale + " lacks " + key);
                } else if (!"en".equals(locale) && value.equals(english.getProperty(key))
                        && !SAME_AS_ENGLISH.getOrDefault(key, Set.of()).contains(locale)) {
                    problems.add(locale + " leaves " + key + " in English");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("consent record number should show without a thousands separator in every locale")
    void shouldShowConsentNumberWithoutGrouping_forEveryLocale() throws IOException {
        for (String locale : LOCALES) {
            Properties bundle = loadBundle(locale);
            Locale javaLocale = Locale.forLanguageTag(locale.replace('_', '-'));
            String record = new MessageFormat(bundle.getProperty("email.admin.consentRecord"), javaLocale)
                    .format(new Object[] {12345});
            String recordAsOf = new MessageFormat(bundle.getProperty("email.admin.consentRecordAsOf"), javaLocale)
                    .format(new Object[] {12345, "2026-10-08"});
            assertThat(record).as("%s consentRecord", locale).contains("12345");
            assertThat(recordAsOf).as("%s consentRecordAsOf", locale).contains("12345").contains("2026-10-08");
        }
    }

    @Test
    @DisplayName("accepted tag should be a neutral grey, not the green of a resolved email")
    void shouldStyleAcceptedTagNeutral_notAsResolvedGreen() throws IOException {
        String jsp = Files.readString(MANAGE_EMAILS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .containsPattern(Pattern.compile("\\.status-tag-accepted \\{\\s*"
                        + "background-color: #e9ecef !important;\\s*color: #343a40 !important;\\s*}"))
                .containsPattern(Pattern.compile("\\.status-tag-accepted:hover \\{\\s*"
                        + "background-color: #dee2e6 !important;"))
                .containsPattern(Pattern.compile("\\.status-tag-resolved \\{\\s*"
                        + "background-color: #cefad0 !important;"))
                // The card's left stripe matches the tag: grey for SUCCESS, green only for RESOLVED.
                .containsPattern(Pattern.compile("\\.vertical-status-divider-success \\{\\s*"
                        + "border-left: 3px solid #6c757d !important;"))
                .containsPattern(Pattern.compile("\\.vertical-status-divider-resolved \\{\\s*"
                        + "border-left: 3px solid #008631 !important;"))
                .doesNotContain(".status-tag-success");
    }

    @Test
    @DisplayName("SUCCESS should still be the enum constant and stored value the JSPs compare against")
    void shouldKeepStoredSuccessValue_forDisplayOnlyRelabel() {
        EmailLogStatusConverter converter = new EmailLogStatusConverter();

        assertThat(EmailStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(converter.convertToDatabaseColumn(EmailStatus.SUCCESS)).isEqualTo("SUCCESS");
        assertThat(converter.convertToEntityAttribute("SUCCESS")).isEqualTo(EmailStatus.SUCCESS);
    }

    /**
     * The words a reader would see that are written into the markup itself, outside scripts and
     * styles: text between tags; the values of the attributes a browser shows ({@code title},
     * {@code alt}, {@code aria-label}, {@code placeholder}, {@code value}); and capitalised words
     * quoted inside EL, such as {@code ${enc ? 'Encrypted' : ''}}. Comments, {@code fmt:message}
     * tags (also when nested in an attribute) and character entities are not words. Enum names
     * compared in EL ({@code 'SUCCESS'}) and lower-case attribute fragments are not caught, by design.
     */
    private static List<String> wordsOutsideMarkup(String markup) {
        String source = markup
                .replaceAll("(?s)<%--.*?--%>", " ")
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?s)<script\\b.*?</script>", " ")
                .replaceAll("(?s)<style\\b.*?</style>", " ")
                .replaceAll("<fmt:message\\b[^>]*/>", " ");
        List<String> words = new ArrayList<>();
        Matcher literal = Pattern.compile("\\$\\{[^}]*}").matcher(source);
        while (literal.find()) {
            Matcher quoted = Pattern.compile("['\"]([^'\"]*)['\"]").matcher(literal.group());
            while (quoted.find()) {
                collectWords(quoted.group(1), "\\b\\p{Lu}\\p{Ll}+", words);
            }
        }
        String withoutEl = source.replaceAll("\\$\\{[^}]*}", " ");
        // Only plain HTML elements: a prefixed tag (c:set, fmt:param, jsp:include) is not shown.
        Matcher element = Pattern.compile("<([A-Za-z][A-Za-z0-9]*)(\\s[^>]*)?>").matcher(withoutEl);
        while (element.find()) {
            String attributes = element.group(2) == null ? "" : element.group(2);
            boolean hiddenInput = "input".equalsIgnoreCase(element.group(1))
                    && attributes.matches("(?is).*\\btype\\s*=\\s*[\"']hidden[\"'].*");
            Matcher attribute = Pattern.compile("(?i)\\b(title|alt|aria-label|placeholder|value)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
                    .matcher(attributes);
            while (attribute.find()) {
                if (!(hiddenInput && "value".equalsIgnoreCase(attribute.group(1)))) {
                    String quoted = attribute.group(2);
                    collectWords(quoted.substring(1, quoted.length() - 1), "\\S*\\p{L}\\S*", words);
                }
            }
        }
        String text = withoutEl
                .replaceAll("(?s)<[^>]*>", " ")
                .replaceAll("&[#a-zA-Z0-9]+;", " ");
        collectWords(text, "\\S*\\p{L}\\S*", words);
        return words;
    }

    private static void collectWords(String text, String wordPattern, List<String> words) {
        Matcher matcher = Pattern.compile(wordPattern).matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
    }

    private static Set<String> literalMessageKeys(String markup) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = MESSAGE_KEY.matcher(markup);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Properties loadBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        Path bundlePath = Path.of("src/main/resources/oscarResources_" + locale + ".properties");
        try (var reader = Files.newBufferedReader(bundlePath, StandardCharsets.UTF_8)) {
            bundle.load(reader);
        }
        return bundle;
    }
}
