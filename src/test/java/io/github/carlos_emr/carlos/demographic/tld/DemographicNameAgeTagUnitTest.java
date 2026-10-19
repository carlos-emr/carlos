/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.tld;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/** Exercises the HTML output sink used by patient labels, including stored markup. */
@Tag("unit")
@Tag("release-review")
class DemographicNameAgeTagUnitTest {
    @Test
    void shouldEncodeStoredPatientMarkup_whenRenderingNameAgeLabel() throws Exception {
        String label = "<img src=x onerror=alert(1)>, FAKE & SYNTHETIC F 25 years";
        String rendered = render(label);
        org.assertj.core.api.Assertions.assertThat(rendered).isEqualTo(SafeEncode.forHtmlContent(label));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FAKE-Élodie, FAKE-Test F 25 years", "FAKE-O’Connor & Sons", "FAKE &amp; literal"})
    void shouldPreserveStoredLabelText_whenRenderedAsHtml(String label) throws Exception {
        var body = org.jsoup.Jsoup.parseBodyFragment(render(label)).body();
        org.assertj.core.api.Assertions.assertThat(body.children()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(body.wholeText()).isEqualTo(label);
    }

    private String render(String label) throws Exception {
        PageContext page = mock(PageContext.class);
        HttpSession session = mock(HttpSession.class);
        JspWriter output = mock(JspWriter.class);
        LoggedInInfo caller = mock(LoggedInInfo.class);
        DemographicNameAgeString names = mock(DemographicNameAgeString.class);
        when(page.getSession()).thenReturn(session);
        when(page.getOut()).thenReturn(output);
        when(names.getNameAgeString(caller, 42)).thenReturn(label);

        try (var lookup = mockStatic(DemographicNameAgeString.class);
             var login = mockStatic(LoggedInInfo.class)) {
            lookup.when(DemographicNameAgeString::getInstance).thenReturn(names);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(session)).thenReturn(caller);
            DemographicNameAgeTag tag = new DemographicNameAgeTag();
            tag.setPageContext(page);
            tag.setDemographicNo("42");
            tag.doStartTag();

            var written = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(output).print(written.capture());
            return written.getValue();
        }
    }
}
