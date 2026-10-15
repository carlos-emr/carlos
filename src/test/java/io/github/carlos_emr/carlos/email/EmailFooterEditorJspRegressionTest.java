/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared Edit footer window (issue #3981): its browser-side cleaning matches the server's
 * allow-list, nothing reaches {@code innerHTML} uncleaned, and the clinic logo card on Configure
 * Email offers its upload only to administrators with write rights.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Email footer editor and logo card")
class EmailFooterEditorJspRegressionTest {

    private static final Path MODAL = Path.of("src/main/webapp/WEB-INF/jsp/email/footerEditorModal.jspf");
    private static final Path SCRIPT = Path.of("src/main/webapp/share/javascript/email/footerEditor.js");
    private static final Path CONFIGURE = Path.of("src/main/webapp/WEB-INF/jsp/admin/configureEmail.jsp");
    private static final List<String> LOCALES = List.of("en", "es", "fr", "pl", "pt_BR");

    @Test
    @DisplayName("should load DOMPurify before the editor and take the limit from the constant the server enforces")
    void shouldLoadSanitizerFirst_andUseServerLimit() throws IOException {
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);

        int purify = modal.indexOf("/library/dompurify/purify.min.js");
        assertThat(purify).isGreaterThanOrEqualTo(0).isLessThan(modal.indexOf("/share/javascript/email/footerEditor.js"));
        assertThat(Path.of("src/main/webapp/library/dompurify/purify.min.js")).exists();
        assertThat(modal)
                .contains("data-max-length=\"<%= io.github.carlos_emr.carlos.email.core.EmailData.FOOTER_MAX_LENGTH %>\"")
                .contains("id=\"footerEditorText\" class=\"form-control\" contenteditable=\"true\"")
                .contains("<fmt:message key=\"${footerEditorScopeKey}\"/>")
                .contains("<fmt:message key=\"${footerEditorApplyKey}\"/>");
    }

    @Test
    @DisplayName("should clean in the browser with the server's tags and link schemes")
    void shouldMirrorServerAllowList_inBrowserCleaning() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ALLOWED_TAGS: ['b', 'strong', 'i', 'em', 'br', 'p', 'div', 'a']")
                .contains("ALLOWED_ATTR: ['href']")
                .contains("ALLOW_DATA_ATTR: false")
                .contains("ALLOW_ARIA_ATTR: false")
                .contains("ALLOWED_URI_REGEXP: /^(?:https:|mailto:)/i")
                // Link addresses with hidden characters lose their address, as on the server, and the
                // link box refuses them with its usual message.
                .contains("var HIDDEN_CHARACTERS = /[\\p{Cc}\\p{Cf}\\uFFFD]/u;")
                .contains("window.DOMPurify.addHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);")
                .contains("window.DOMPurify.removeHook('uponSanitizeAttribute', dropHiddenCharacterAddresses);")
                .contains("|| HIDDEN_CHARACTERS.test(address)) {")
                // Empty means what the server and the Patient Portal mean: no character outside the
                // Unicode C* and Z* categories in the footer's own text (link addresses do not count).
                .contains("var VISIBLE_CHARACTER = /[^\\p{C}\\p{Z}]/u;")
                .contains("return VISIBLE_CHARACTER.test(textFromCleanedHtml(value, false)) ? value : '';")
                // Without DOMPurify nothing is put into the page as HTML, and editing is switched off.
                .contains("if (!window.DOMPurify || !html) {")
                .contains("opener.disabled = true;")
                // A footer link in a preview never navigates away from an unsent email.
                .contains("event.target.closest('.footer-editor-mail a')")
                .contains("String(html || '').replace(/<[^>]*>/g, ' ')")
                .doesNotContain("parseFromString")
                // Pasted text keeps no formatting.
                .contains("getData('text/plain')")
                .contains("document.execCommand('insertText', false, text)");
    }

    @Test
    @DisplayName("should put only cleaned HTML into innerHTML")
    void shouldAssignOnlyCleanedHtml_toInnerHtml() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        Matcher assignment = Pattern.compile("\\.innerHTML = ([^;]+);").matcher(script);
        int count = 0;
        while (assignment.find()) {
            count++;
            String value = assignment.group(1).strip();
            // Either a clean(...) call, or the local "html" that the line above set from clean(...).
            assertThat(value).as(assignment.group()).matches("clean\\(.*\\)|html");
        }
        assertThat(count).isEqualTo(5);
        assertThat(script).containsSubsequence("var html = clean(value);", "preview.innerHTML = html;")
                .containsSubsequence("var html = clean(editor.innerHTML);", "previewFooter.innerHTML = html;")
                // The saved footer read from the server is cleaned like any other.
                .contains("editor.innerHTML = clean(data.footer);");
    }

    @Test
    @DisplayName("should take the logo address from the page's own markup, never from text read out of the page")
    void shouldCloneLogoFromTemplate_withoutCopyingPageText() throws IOException {
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        // Nothing inside a template loads, so the logo is still fetched only when the window opens.
        assertThat(modal)
                .contains("<template id=\"footerEditorPreviewLogoTemplate\"><img id=\"footerEditorPreviewLogo\"")
                // Firefox fetches a template's images early unless they are lazy.
                .contains("loading=\"lazy\" src=\"${carlos:forHtmlAttribute(ctx)}/email/clinicEmailLogo\"></template>")
                .doesNotContain("data-logo-url");
        assertThat(script)
                // Listeners first, then into the page, where the image starts loading.
                .containsSubsequence("source.cloneNode(true)", "previewLogo.loading = 'eager';", "addEventListener('load'",
                        "addEventListener('error'", "previewLogoTemplate.replaceWith(previewLogo)")
                .containsSubsequence("addEventListener('show.bs.modal'", "loadLogo();")
                .doesNotContain(".src =")
                .doesNotContain("setAttribute('src'")
                .doesNotContain("data-logo-url");
    }

    @Test
    @DisplayName("should offer the logo upload only inside the _admin write check, as a multipart POST")
    void shouldGuardLogoForm_withAdminWriteCheck() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);

        int guard = jsp.indexOf("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\"_admin\" rights=\"w\"",
                jsp.indexOf("id=\"clinicLogoCard\""));
        int form = jsp.indexOf("<form action=\"${ctx}/admin/saveClinicEmailLogo\" method=\"post\" enctype=\"multipart/form-data\"");
        assertThat(guard).isGreaterThanOrEqualTo(0);
        assertThat(form).isGreaterThan(guard).isLessThan(jsp.indexOf("</security:oscarSec>", guard));
        assertThat(jsp)
                .contains("accept=\"image/png,image/jpeg\"")
                .contains("<img src=\"${ctx}/email/clinicEmailLogo\"")
                .doesNotContain("${param.logoError}");
    }

    @Test
    @DisplayName("should show a translated message for every reason a logo is refused")
    void shouldShowMessage_forEveryRejection() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);

        for (EmailFooterLogoService.Rejection reason : EmailFooterLogoService.Rejection.values()) {
            assertThat(jsp).as(reason.name()).contains("<c:when test=\"${param.logoError eq '" + reason.name() + "'}\">");
        }
        List<String> keys = List.of("admin.configureEmail.logo.errorEmpty", "admin.configureEmail.logo.errorTooBig",
                "admin.configureEmail.logo.errorNotImage", "admin.configureEmail.logo.errorTooLarge",
                "admin.configureEmail.logo.errorCopyTooBig", "admin.configureEmail.logo.errorUploadFailed");
        Properties english = bundle("en");
        for (String key : keys) {
            assertThat(jsp).contains("<fmt:message key=\"" + key + "\"/>");
            for (String locale : LOCALES) {
                String text = bundle(locale).getProperty(key);
                assertThat(text).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(text).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    @Test
    @DisplayName("should check a logo before it is sent and carry unsaved footer text with it")
    void shouldCheckLogoFirst_andCarryUnsavedFooterText() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);
        Path script = Path.of("src/main/webapp/share/javascript/email/clinicLogoForm.js");

        assertThat(script).exists();
        assertThat(jsp)
                .contains("<script src=\"${ctx}/share/javascript/email/clinicLogoForm.js\"></script>")
                // The browser uses the limits the server enforces.
                .contains("data-max-bytes=\"<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_BYTES %>\"")
                .contains("data-max-width=\"<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_WIDTH %>\"")
                .contains("data-max-height=\"<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_HEIGHT %>\"")
                .contains("data-too-big=\"${carlos:forHtmlAttribute(logoCheckTooBig)}\"")
                .contains("data-too-large=\"${carlos:forHtmlAttribute(logoCheckTooLarge)}\"")
                .contains("data-not-image=\"${carlos:forHtmlAttribute(logoCheckNotImage)}\"")
                .contains("<input type=\"hidden\" name=\"" + io.github.carlos_emr.carlos.email.admin.ClinicFooterDraft.HTML_PARAM + "\" value=\"\"/>")
                .contains("<input type=\"hidden\" name=\"" + io.github.carlos_emr.carlos.email.admin.ClinicFooterDraft.FINGERPRINT_PARAM + "\" value=\"\"/>")
                // Text put back is shown encoded; text beside a newer footer is plain text.
                .contains("<fmt:message key=\"admin.configureEmail.footer.draftKept\"/>")
                .contains("<carlos:encode value=\"${clinicFooterAttempted}\"/>");
        int form = jsp.indexOf("id=\"clinicLogoForm\"");
        int draft = jsp.indexOf("name=\"clinicFooterDraft\"");
        assertThat(draft).isGreaterThan(form).isLessThan(jsp.indexOf("</form>", form));
    }

    @Test
    @DisplayName("should translate the new footer and logo messages, keeping the numbers the script fills in")
    void shouldTranslateKeptTextAndCheckMessages_inEveryLocale() throws IOException {
        Properties english = bundle("en");
        for (String key : List.of("admin.configureEmail.footer.draftKept", "admin.configureEmail.footer.attempted",
                "admin.configureEmail.logo.checkTooBig", "admin.configureEmail.logo.checkTooLarge")) {
            for (String locale : LOCALES) {
                String text = bundle(locale).getProperty(key);
                assertThat(text).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(text).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
        for (String locale : LOCALES) {
            Properties texts = bundle(locale);
            assertThat(texts.getProperty("admin.configureEmail.logo.checkTooBig")).as(locale).contains("{0}");
            assertThat(texts.getProperty("admin.configureEmail.logo.checkTooLarge")).as(locale).contains("{0}", "{1}");
            // Both refusals name both limits.
            for (String key : List.of("admin.configureEmail.logo.errorTooBig", "admin.configureEmail.logo.errorTooLarge",
                    "admin.configureEmail.logo.checkTooBig", "admin.configureEmail.logo.checkTooLarge")) {
                assertThat(texts.getProperty(key)).as(locale + " " + key).contains("100", "600 \u00d7 200");
            }
        }
    }

    private static final Path MY_FOOTER = Path.of("src/main/webapp/WEB-INF/jsp/email/myEmailFooter.jsp");
    private static final Path COMPOSE = Path.of("src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp");

    @Test
    @DisplayName("should offer Required or Off and the logo switch in the clinic editor window, sent with the clinic form")
    void shouldOfferClinicSettings_inTheEditorWindow() throws IOException {
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);
        int block = modal.indexOf("<c:if test=\"${footerEditorClinicSettings}\">");
        int blockEnd = modal.indexOf("</c:if>", modal.indexOf("id=\"logoShown\"", block));
        assertThat(block).isGreaterThan(0);
        for (var mode : io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService.Mode.values()) {
            int radio = modal.indexOf("form=\"clinicFooterForm\" value=\"" + mode.value() + "\" ${clinicFooterModeChosen eq 'off' ?", block);
            assertThat(radio).as(mode.name()).isGreaterThan(block).isLessThan(blockEnd);
        }
        // The switch posts a hidden "false" first and a checked "true": absent means not posted.
        int hidden = modal.indexOf("<input type=\"hidden\" name=\"logoShown\" value=\"false\" form=\"clinicFooterForm\"/>", block);
        int toggle = modal.indexOf("id=\"logoShown\" name=\"logoShown\"", block);
        assertThat(hidden).isGreaterThan(block).isLessThan(toggle);
        assertThat(modal.indexOf("form=\"clinicFooterForm\" value=\"true\" ${clinicLogoShownChosen ? 'checked' : ''}/>", toggle))
                .isGreaterThan(toggle).isLessThan(blockEnd);
        // The preview asks for the logo only while emails carry one: switched off, it answers 404.
        assertThat(modal).contains("<c:if test=\"${not footerEditorPersonalOnly and clinicLogoShown}\"><template id=\"footerEditorPreviewLogoTemplate\">");

        String configure = Files.readString(CONFIGURE, StandardCharsets.UTF_8);
        assertThat(configure)
                .contains("<c:set var=\"footerEditorClinicSettings\" value=\"${true}\"/>")
                .contains("<c:when test=\"${param.clinicFooterOutcome eq 'settingsSaved'}\">")
                .contains("<fmt:message key=\"admin.configureEmail.footer.offNote\"/>")
                .contains("id=\"clinicFooterModeShown\"")
                .doesNotContain("name=\"clinicFooterMode\"")
                .doesNotContain("value=\"switch\"")
                .doesNotContain("param.logoSwitch");
        for (Path page : List.of(COMPOSE, MY_FOOTER)) {
            String jsp = Files.readString(page, StandardCharsets.UTF_8);
            int off = jsp.indexOf("<c:when test=\"${clinicFooterOff}\">");
            assertThat(off).as(page.toString()).isGreaterThan(0);
            assertThat(jsp.indexOf("<fmt:message key=\"email.compose.footer.clinicOff\"/>", off)).as(page.toString())
                    .isGreaterThan(off).isLessThan(jsp.indexOf("data-footer-html=\"${carlos:forHtmlAttribute(clinicFooter)}\"", off));
        }
    }

    @Test
    @DisplayName("should save from the editor window where a footer is saved as it is edited, with no second Save button")
    void shouldSaveFromTheEditorWindow_withoutASecondSaveButton() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("var saveForm = opener ? document.getElementById(opener.getAttribute('data-footer-editor-save') || '') : null;")
                .contains("saveForm.requestSubmit();")
                .contains("saveForm.submit();");

        String configure = Files.readString(CONFIGURE, StandardCharsets.UTF_8);
        int clinicForm = configure.indexOf("id=\"clinicFooterForm\"");
        String clinic = configure.substring(clinicForm, configure.indexOf("</form>", clinicForm));
        assertThat(clinic)
                .contains("data-footer-editor-target=\"clinicFooter\"")
                .contains("data-footer-editor-save=\"clinicFooterForm\"")
                .doesNotContain("type=\"submit\"");
        assertThat(configure).contains("<c:set var=\"footerEditorApplyKey\" value=\"email.footerEditor.save\"/>");

        String mine = Files.readString(MY_FOOTER, StandardCharsets.UTF_8);
        assertThat(mine)
                .contains("data-footer-editor-save=\"myFooterForm\" data-footer-editor-save-name=\"footerAction\"")
                .contains("data-footer-editor-save-value=\"save\"")
                .contains("<c:set var=\"footerEditorApplyKey\" value=\"email.footerEditor.save\"/>")
                .contains("name=\"footerAction\" value=\"clear\"")
                .doesNotContain("name=\"footerAction\" value=\"save\"");

        // Compose applies the footer to that one email; it saves nothing, so it keeps its own button.
        String compose = Files.readString(COMPOSE, StandardCharsets.UTF_8);
        assertThat(compose).doesNotContain("data-footer-editor-save=");
    }

    @Test
    @DisplayName("should show the logo emails carry, and point to the switch in Edit footer when it is off")
    void shouldShowCarriedLogo_andPointToTheSwitch() throws IOException {
        String jsp = Files.readString(CONFIGURE, StandardCharsets.UTF_8);
        assertThat(jsp)
                .contains("<c:when test=\"${not clinicLogoShown}\">")
                .contains("<fmt:message key=\"admin.configureEmail.logo.hidden\"/>")
                .contains("<fmt:message key=\"admin.configureEmail.logo.carlosDefault\"/>")
                .doesNotContain("admin.configureEmail.logo.none");
    }

    @Test
    @DisplayName("should translate the footer settings text, and drop the buttons' old text")
    void shouldTranslateSettingsText_andDropOldButtonText() throws IOException {
        Properties english = bundle("en");
        for (String key : List.of("admin.configureEmail.footer.modeLegend", "admin.configureEmail.footer.modeRequired",
                "admin.configureEmail.footer.modeOff", "admin.configureEmail.footer.modePortalNote",
                "admin.configureEmail.footer.settingsSaved", "admin.configureEmail.footer.offNote",
                "admin.configureEmail.footer.policyHelp", "admin.configureEmail.footer.draftKept",
                "email.compose.footer.clinicOff", "email.footerEditor.save", "email.myFooter.intro",
                "admin.configureEmail.logo.carlosDefault", "admin.configureEmail.logo.hidden",
                "admin.configureEmail.logo.switch", "admin.configureEmail.logo.removed")) {
            for (String locale : LOCALES) {
                String text = bundle(locale).getProperty(key);
                assertThat(text).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(text).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
        for (String gone : List.of("admin.configureEmail.logo.none", "admin.configureEmail.footer.policySave",
                "email.footerEditor.applyClinic", "email.footerEditor.applyMine", "email.myFooter.save",
                "admin.configureEmail.logo.switchSave", "admin.configureEmail.logo.switchedOn",
                "admin.configureEmail.logo.switchedOff", "admin.configureEmail.footer.modeSaved")) {
            for (String locale : LOCALES) {
                assertThat(bundle(locale).getProperty(gone)).as(locale + " " + gone).isNull();
            }
        }
    }

    private static final Path PREFERENCES = Path.of("src/main/webapp/WEB-INF/jsp/provider/providerpreference.jsp");

    @Test
    @DisplayName("should open the personal footer window from Preferences, for _email writers, with My Email Footer as the fallback")
    void shouldOpenPersonalFooterWindow_fromPreferences() throws IOException {
        String jsp = Files.readString(PREFERENCES, StandardCharsets.UTF_8);
        int link = jsp.indexOf("id=\"myEmailFooterLink\"");
        int guard = jsp.lastIndexOf("objectName=\"_email\" rights=\"w\"", link);
        assertThat(guard).isGreaterThan(0);
        assertThat(jsp.indexOf("</security:oscarSec>", guard)).isGreaterThan(link);
        String opener = jsp.substring(jsp.lastIndexOf("<a ", link), jsp.indexOf(">", link));
        assertThat(opener)
                .contains("href=\"${pageContext.request.contextPath}/email/myEmailFooter\"")
                .contains("data-bs-toggle=\"modal\" data-bs-target=\"#footerEditorModal\"")
                .contains("data-footer-editor-load=\"${carlos:forHtmlAttribute(ctx)}/email/myEmailFooter\"")
                .contains("data-footer-editor-remote-save=\"${carlos:forHtmlAttribute(ctx)}/email/saveMyEmailFooter\"")
                .contains("data-footer-editor-status=\"myEmailFooterSaved\"");
        assertThat(jsp).contains("id=\"myEmailFooterSaved\"");
        // The window itself, for _email writers only, last on the page.
        int include = jsp.indexOf("<%@ include file=\"/WEB-INF/jsp/email/footerEditorModal.jspf\" %>");
        int includeGuard = jsp.lastIndexOf("objectName=\"_email\" rights=\"w\"", include);
        assertThat(includeGuard).isGreaterThan(link);
        assertThat(jsp.substring(includeGuard, include))
                .contains("<c:set var=\"footerEditorPersonalOnly\" value=\"${true}\"/>")
                .contains("<c:set var=\"footerEditorScopeKey\" value=\"email.footerEditor.scopeMine\"/>")
                .contains("<c:set var=\"footerEditorApplyKey\" value=\"email.footerEditor.save\"/>");
        assertThat(jsp.indexOf("</security:oscarSec>", include)).isLessThan(jsp.indexOf("</body>", include));
        // The page brings the Bootstrap version the window is built for.
        assertThat(jsp).contains("<%@ include file=\"/WEB-INF/jsp/includes/global-head.jspf\" %>");
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/includes/global-head.jspf"), StandardCharsets.UTF_8))
                .contains("/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js")
                .contains("/library/bootstrap/5.3.8/css/bootstrap.min.css")
                .contains("/csrfguard");
    }

    @Test
    @DisplayName("should open the saved personal footer window from both compose links, keeping the compose revision current")
    void shouldOpenPersonalFooterWindow_fromCompose() throws IOException {
        String jsp = Files.readString(COMPOSE, StandardCharsets.UTF_8);
        for (String id : List.of("myFooterEdit", "myFooterEditAfterSend")) {
            int link = jsp.indexOf("id=\"" + id + "\"");
            assertThat(link).as(id).isGreaterThan(0);
            String opener = jsp.substring(jsp.lastIndexOf("<a ", link), jsp.indexOf(">", link));
            assertThat(opener).as(id)
                    .contains("href=\"${ctx}/email/myEmailFooter\"")
                    .contains("data-footer-editor-load=\"${carlos:forHtmlAttribute(ctx)}/email/myEmailFooter\"")
                    .contains("data-footer-editor-remote-save=\"${carlos:forHtmlAttribute(ctx)}/email/saveMyEmailFooter\"")
                    .contains("data-footer-editor-scope-text=\"${carlos:forHtmlAttribute(myFooterScopeText)}\"")
                    .contains("data-footer-editor-apply-text=\"${carlos:forHtmlAttribute(myFooterApplyText)}\"");
        }
        String first = jsp.substring(jsp.indexOf("id=\"myFooterEdit\""), jsp.indexOf(">", jsp.indexOf("id=\"myFooterEdit\"")));
        assertThat(first)
                .contains("data-footer-editor-revision-field=\"personalFooterRevision\"")
                .contains("data-footer-editor-saved-text=\"${carlos:forHtmlAttribute(myFooterSavedText)}\"");
        // This email's own footer keeps its own opener and button.
        assertThat(jsp).contains("<c:set var=\"footerEditorApplyKey\" value=\"email.footerEditor.applyThisEmail\"/>");
    }

    @Test
    @DisplayName("should read and save the saved footer with the page's CSRF token, as AJAX, and keep a link a link without the window")
    void shouldSaveRemotely_withTheCsrfToken() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
        assertThat(script)
                .contains("headers: {'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest'}")
                .contains("var token = document.querySelector('input[name=\"CSRF-TOKEN\"]');")
                .contains("if (!token || !token.value) {")
                .contains("'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': token.value}")
                .contains("body.set('personalFooterRevision', remote.revision);")
                .contains("document.querySelectorAll('a[href][data-footer-editor-load]').forEach(function (link) {");
        String modal = Files.readString(MODAL, StandardCharsets.UTF_8);
        assertThat(modal)
                .contains("id=\"footerEditorMessage\"")
                .contains("id=\"footerEditorScope\"")
                .contains("data-remote-failed-text=\"${carlos:forHtmlAttribute(footerEditorRemoteFailed)}\"")
                .contains("data-remote-stale-text=\"${carlos:forHtmlAttribute(footerEditorRemoteStale)}\"");
        Properties english = bundle("en");
        for (String key : List.of("email.footerEditor.remoteStale", "email.footerEditor.remoteFailed",
                "email.footerEditor.remoteLoadFailed", "email.compose.footer.savedMineThisEmailKept",
                "email.myFooter.saved", "email.myFooter.tooLong", "email.myFooter.saveConflict", "email.footerEditor.scopeMine")) {
            for (String locale : LOCALES) {
                String text = bundle(locale).getProperty(key);
                assertThat(text).as(locale + " " + key).isNotBlank();
                if (!"en".equals(locale)) {
                    assertThat(text).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
