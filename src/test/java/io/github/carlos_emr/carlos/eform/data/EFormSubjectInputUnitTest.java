/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
@Tag("fast")
class EFormSubjectInputUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    void shouldPreserveStoredSubject_whenTemplateHasNoControl() {
        EForm form = new EForm();
        form.setFormHtml("<html><body><form id='letter'></form></body></html>");
        String subject = "Follow-up & \"results\" <script>alert(1)</script>";
        form.setFormSubject(subject);
        form.ensureSubjectInput();
        form.ensureSubjectInput();
        var document = Jsoup.parse(form.getFormHtml());
        assertThat(document.select("form [name=subject]")).hasSize(1);
        assertThat(document.selectFirst("form [name=subject]").val()).isEqualTo(subject);
        assertThat(document.select("script")).isEmpty();
    }

    @Test
    void shouldPreserveTemplateControl_whenValueIsIntentionallyEmpty() {
        EForm form = new EForm();
        form.setFormHtml("<form><input name='subject' value=''></form>");
        form.setFormSubject("Old subject");
        form.ensureSubjectInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("[name=subject]");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEmpty();
        assertThat(fields.first().attr("type")).isEmpty();
    }

    @Test
    void shouldInitializeNewForm_withoutUndefinedSubject() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureSubjectInput();
        assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("[name=subject]").val()).isEmpty();
    }

    @Test
    void shouldPreserveAuthoredNewFormFlag_whenTemplateUsesCaseSensitiveInitialization() {
        EForm form = new EForm();
        form.setFormHtml("<form><input type='hidden' id='newForm' name='newForm' value='True'></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("#newForm");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEqualTo("True");
        assertThat(fields.first().attr("name")).isEqualTo("newForm");
        assertThat(fields.first().parent().tagName()).isEqualTo("form");
    }

    @Test
    void shouldPreserveAuthoredNewFormFlag_whenTemplateDeclaresItByNameOnly() {
        EForm form = new EForm();
        form.setFormHtml("<form><input type='hidden' name='newForm' value='True'></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        var fields = document.select("[name=newForm]");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEqualTo("True");
        assertThat(document.select("#newForm")).isEmpty();
    }

    @Test
    void shouldSupplyNewFormFlag_whenOnlyDifferentlyCasedNameOrIdExists() {
        EForm form = new EForm();
        form.setFormHtml("<form><input type='hidden' name='newform' value='x'>"
                + "<input type='hidden' id='NEWFORM' value='y'></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        var fields = document.select("form > input").stream()
                .filter(input -> "newForm".equals(input.attr("name"))).toList();
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).id()).isEqualTo("newForm");
        assertThat(fields.get(0).val()).isEqualTo("true");
        assertThat(document.select("form > input")).hasSize(3);
    }

    @Test
    void shouldSupplyNewFormFlag_whenOnlyNonSubmittingElementsUseTheName() {
        EForm form = new EForm();
        form.setFormHtml("<form><a name='newForm'></a><img name='newForm'>"
                + "<input type='button' name='newForm' value='Start'></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        var fields = document.select("input[type=hidden]").stream()
                .filter(input -> "newForm".equals(input.attr("name"))).toList();
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).id()).isEqualTo("newForm");
        assertThat(fields.get(0).val()).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<input type='hidden' name='newForm' value='True' disabled>",
            "<input type='checkbox' name='newForm' value='True'>",
            "<input type='radio' name='newForm' value='True'>",
            "<select name='newForm' disabled><option value='True' selected>True</option></select>",
            "<textarea name='newForm' disabled>True</textarea>",
            "<fieldset disabled><input type='hidden' name='newForm' value='True'></fieldset>",
            "<fieldset disabled><legend>Flags</legend><div><input type='hidden' name='newForm' value='True'></div></fieldset>",
            "<fieldset disabled><legend>A</legend><legend><input type='hidden' name='newForm' value='True'></legend></fieldset>",
            "<fieldset disabled><legend><fieldset disabled><legend></legend>"
                    + "<input type='hidden' name='newForm' value='True'></fieldset></legend></fieldset>",
            "<fieldset disabled><fieldset disabled><legend>"
                    + "<input type='hidden' name='newForm' value='True'></legend></fieldset></fieldset>",
            "<select name='newForm'></select>",
            "<select name='newForm'><option value='True' selected disabled>True</option></select>",
            "<select name='newForm'><optgroup disabled><option value='True'>True</option></optgroup></select>",
            "<select name='newForm' multiple><option value='True'>True</option></select>",
            "<select name='newForm' size='2'><option value='True'>True</option></select>",
            "<select name='newForm' size='0000000002'><option value='True'>True</option></select>",
            "<select name='newForm' size='99999999999'><option value='True'>True</option></select>",
            "<button type='button' name='newForm' value='False'>Toggle</button>",
            "<button type='submit' name='newForm' value='False' disabled>Save</button>",
            "<select name='newForm'><option value='True' selected>True</option><option value='x' selected disabled>x</option></select>"})
    void shouldSupplyNewFormFlag_whenNewFormControlWouldNotBeSubmitted(String control) {
        EForm form = new EForm();
        form.setFormHtml("<form>" + control + "</form>");
        form.ensureNewFormInput();
        var fallback = Jsoup.parse(form.getFormHtml()).getElementById("newForm");
        assertThat(fallback).isNotNull();
        assertThat(fallback.attr("type")).isEqualTo("hidden");
        assertThat(fallback.val()).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<input type='checkbox' name='newForm' value='True' checked>",
            "<input type='radio' name='newForm' value='True' checked>",
            "<input type='hidden' name='newForm' value='True'>",
            "<select name='newForm'><option value='True' selected>True</option></select>",
            "<fieldset disabled><legend><input type='hidden' name='newForm' value='True'></legend></fieldset>",
            "<fieldset disabled><legend><span><input type='hidden' name='newForm' value='True'></span></legend></fieldset>",
            "<fieldset disabled><legend><fieldset disabled><legend>"
                    + "<input type='hidden' name='newForm' value='True'></legend></fieldset></legend></fieldset>",
            "<select name='newForm'><option value='x' disabled>x</option><option value='True'>True</option></select>",
            "<select name='newForm' multiple><option value='True' selected>True</option></select>",
            "<select name='newForm' size='1'><option value='True'>True</option></select>",
            "<select name='newForm' size='0'><option value='True'>True</option></select>",
            "<select name='newForm' size='00000001'><option value='True'>True</option></select>",
            "<select name='newForm' size='2'><option value='True' selected>True</option></select>",
            "<button name='newForm' value='False'>Save</button>",
            "<button type='submit' name='newForm' value='False'>Save</button>",
            "<input type='submit' name='newForm' value='False'>"})
    void shouldKeepTemplateNewFormFlag_whenNewFormControlIsSubmitted(String control) {
        EForm form = new EForm();
        form.setFormHtml("<form>" + control + "</form>");
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        assertThat(document.getElementById("newForm")).isNull();
        assertThat(document.select("[name=newForm]")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<form><input type='hidden' id='newForm' value='True'></form>",
            "<div id='newForm'>True</div><form></form>",
            "<form><input type='hidden' id='newForm' name='newForm' value='True' disabled></form>"})
    void shouldSupplyNewFormByNameOnly_whenIdBelongsToElementThatSubmitsNothing(String html) {
        EForm form = new EForm();
        form.setFormHtml(html);
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        var fallbacks = document.select("form input").stream()
                .filter(input -> "newForm".equals(input.attr("name")) && !input.hasAttr("disabled"))
                .toList();
        assertThat(fallbacks).hasSize(1);
        assertThat(fallbacks.get(0).val()).isEqualTo("true");
        assertThat(fallbacks.get(0).hasAttr("id")).isFalse();
        // The template keeps the only element with that id.
        assertThat(document.select("[id=newForm]")).hasSize(1);
        assertThat(document.getElementById("newForm").val() + document.getElementById("newForm").text())
                .isEqualTo("True");
    }

    @Test
    void shouldKeepTemplateNewFormFlag_whenControlOutsideFormNamesItsOwner() {
        EForm form = new EForm();
        form.setFormHtml("<form id='saveEForm'></form>"
                + "<input id='newForm' name='newForm' form='saveEForm' value='False'>");
        form.ensureNewFormInput();
        var document = Jsoup.parse(form.getFormHtml());
        assertThat(document.select("[name=newForm]")).hasSize(1);
        assertThat(document.selectFirst("[name=newForm]").val()).isEqualTo("False");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<form id='saveEForm'><input name='newForm' form='other' value='True'></form><form id='other'></form>",
            "<form id='saveEForm'><input name='newForm' form='missing' value='True'></form>",
            "<form id='saveEForm'><input name='newForm' form='box' value='True'></form><div id='box'></div>"})
    void shouldSupplyNewFormFlag_whenDescendantIsOwnedByNoOrAnotherForm(String html) {
        EForm form = new EForm();
        form.setFormHtml(html);
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var fallback = Jsoup.parse(form.getFormHtml()).getElementById("newForm");
        assertThat(fallback).isNotNull();
        assertThat(fallback.parent().id()).isEqualTo("saveEForm");
        assertThat(fallback.val()).isEqualTo("true");
        assertThat(fallback.hasAttr("form")).isFalse();
    }

    @Test
    void shouldSupplyNewFormFlag_whenTemplateHasNone() {
        EForm form = new EForm();
        form.setFormHtml("<form></form>");
        form.ensureNewFormInput();
        form.ensureNewFormInput();
        var fields = Jsoup.parse(form.getFormHtml()).select("#newForm");
        assertThat(fields).hasSize(1);
        assertThat(fields.first().val()).isEqualTo("true");
        assertThat(fields.first().attr("type")).isEqualTo("hidden");
        assertThat(fields.first().attr("name")).isEqualTo("newForm");
        assertThat(fields.first().parent().tagName()).isEqualTo("form");
    }
}
