/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.eform.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Pins that a saved eForm keeps the <code>${fdid}</code> marker an author put in a control's value.
 *
 * <p>A new form has no instance id yet, so the page renders the marker as an empty value and the
 * browser posts the control empty. Since an explicitly empty submission clears a template default,
 * that empty value was written over the marker, and the reopened form no longer learned its id.
 * The marker is not a default the user cleared: it stays until the saved copy is rendered.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eForm fdid marker save")
class EFormFdidMarkerSaveUnitTest extends CarlosUnitTestBase {

    private static final String MARKER = "${fdid}";

    @BeforeEach
    void registerDependencies() {
        registerMock(NioFileManager.class, mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should keep the marker in a hidden input that a new form posts empty")
    void shouldKeepMarkerInHiddenInput_whenNewFormPostsItEmpty() {
        EForm form = formOf("<input type=\"hidden\" name=\"m\" id=\"m\" value=\"" + MARKER + "\">"
                + "<span id=\"shown\">" + MARKER + "</span>");

        form.setValues(list("m"), list(""));

        assertThat(Jsoup.parse(form.getFormHtml()).selectFirst("input[name=m]").attr("value")).isEqualTo(MARKER);

        form.setFdid("72");

        Document reopened = Jsoup.parse(form.getFormHtml());
        assertThat(reopened.selectFirst("input[name=m]").attr("value")).isEqualTo("72");
        assertThat(reopened.selectFirst("#shown").text()).isEqualTo("72");
    }

    @Test
    @DisplayName("should keep the marker in a text input, a date input and a textarea posted blank")
    void shouldKeepMarkerInOtherControls_whenPostedBlank() {
        EForm form = formOf("<input type=\"text\" name=\"t\" value=\"form-" + MARKER + "\">"
                + "<input type=\"date\" name=\"d\" value=\"" + MARKER + "\">"
                + "<textarea name=\"a\">" + MARKER + "</textarea>");

        form.setValues(list("t", "d", "a"), list("", "  ", ""));

        Document saved = Jsoup.parse(form.getFormHtml());
        assertThat(saved.selectFirst("input[name=t]").attr("value")).isEqualTo("form-" + MARKER);
        assertThat(saved.selectFirst("input[name=d]").attr("value")).isEqualTo(MARKER);
        assertThat(saved.selectFirst("textarea[name=a]").text()).isEqualTo(MARKER);
    }

    @Test
    @DisplayName("should write a submitted value over the marker")
    void shouldWriteSubmittedValue_whenMarkerControlPostsText() {
        EForm form = formOf("<input type=\"hidden\" name=\"m\" value=\"" + MARKER + "\">"
                + "<textarea name=\"a\">" + MARKER + "</textarea>");

        form.setValues(list("m", "a"), list("41", "typed"));

        Document saved = Jsoup.parse(form.getFormHtml());
        assertThat(saved.selectFirst("input[name=m]").attr("value")).isEqualTo("41");
        assertThat(saved.selectFirst("textarea[name=a]").text()).isEqualTo("typed");
    }

    @Test
    @DisplayName("should still clear an ordinary default that was submitted empty")
    void shouldClearOrdinaryDefault_whenSubmittedEmpty() {
        EForm form = formOf("<input type=\"hidden\" name=\"h\" value=\"default\">"
                + "<input type=\"text\" name=\"t\" value=\"$" + "{other}\">"
                + "<textarea name=\"a\">default</textarea>");

        form.setValues(list("h", "t", "a"), list("", "", ""));

        Document saved = Jsoup.parse(form.getFormHtml());
        assertThat(saved.selectFirst("input[name=h]").attr("value")).isEmpty();
        assertThat(saved.selectFirst("input[name=t]").attr("value")).isEmpty();
        assertThat(saved.selectFirst("textarea[name=a]").text()).isEmpty();
    }

    private static EForm formOf(String controls) {
        EForm form = new EForm();
        form.setFormHtml("<html><head><title>FAKE form</title></head><body><form method=\"post\">"
                + controls + "</form></body></html>");
        return form;
    }

    private static ArrayList<String> list(String... values) {
        return new ArrayList<>(List.of(values));
    }
}
