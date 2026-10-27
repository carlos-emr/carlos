/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.www;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The System Message and Facility Message editors' Expiry Day calendar. Its link opens
 * {@code calendar/oscarCalendarPopup?type=caisi} with the editor's form and field names, and picking a day writes the
 * date into that field. The editors' forms lost those names when the Struts 1 tags went, so the pick wrote nothing.
 */
@DisplayName("System and Facility Message editors' Expiry Day calendar")
@Tag("unit")
@Tag("regression")
class MessageExpiryCalendarJspRegressionTest {

    private static final Path POPUP = Path.of("src/main/webapp/WEB-INF/jsp/calendar/oscarCalendarPopup.jsp");
    private static final Pattern CALENDAR_LINK = Pattern.compile("openerForm=([A-Za-z]+)&amp;openerElement=([A-Za-z_.]+)");

    @ParameterizedTest
    @ValueSource(strings = {"SystemMessageForm", "FacilityMessageForm"})
    @DisplayName("should give the editor's form the name its calendar link passes to the popup")
    void shouldNameForm_asCalendarLinkPassesIt(String editor) throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/caisicore/" + editor + ".jsp"), StandardCharsets.UTF_8);
        int start = jsp.indexOf("<form");
        String form = jsp.substring(start, jsp.indexOf("</form>", start));
        String tag = form.substring(0, form.indexOf('>'));
        Matcher link = CALENDAR_LINK.matcher(form);

        assertThat(link.find()).as("%s has a calendar link", editor).isTrue();
        assertThat(tag).as("%s's form tag", editor).contains("name=\"" + link.group(1) + "\"");
        assertThat(form).as("%s's form has the field the popup fills", editor).contains("name=\"" + link.group(2) + "\"");
    }

    @Test
    @DisplayName("should look the opener's field up and set its value in the popup")
    void shouldSetFieldValue_inCalendarPopup() throws IOException {
        String popup = Files.readString(POPUP, StandardCharsets.UTF_8);
        int start = popup.indexOf("function typeCaisiDate(");
        String function = popup.substring(start, popup.indexOf("//-->", start));

        assertThat(function).contains("doc.forms.namedItem(form_name)")
                .contains("form.elements.namedItem(element_name)")
                .contains("field.value = year1 + \"-\" + month1 + \"-\" + day1;")
                .doesNotContain("eval(");
    }
}
