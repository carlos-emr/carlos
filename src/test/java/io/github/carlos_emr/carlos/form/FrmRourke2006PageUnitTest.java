/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("form")
class FrmRourke2006PageUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"p1", "p2", "p3", "p4", "p1/", "p2/", "p3/", "p4/"})
    void shouldReopenTheLastVisitedShippedPageIncludingLegacySlashValues(String page) {
        String resolved = FrmRourke2006Record.resolveLastVisitedPage(page);
        assertThat(resolved).isEqualTo(page.substring(0, 2));
        assertThat(Path.of("src/main/webapp/WEB-INF/jsp/form/formrourke2006" + resolved + ".jsp"))
                .exists();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"p0", "p5", "p1//", "p2/other", "../p4", "p1?other", " p2", "p3 ", "P4"})
    void shouldFallBackToPageOneForAnythingOutsideTheShippedPageSet(String page) {
        assertThat(FrmRourke2006Record.resolveLastVisitedPage(page)).isEqualTo("p1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p1", "p2", "p3", "p4"})
    void shouldSubmitTheExactPageTokenFromEveryActualHiddenInput(String page) throws Exception {
        Path formRoot = Path.of(System.getProperty("form.source.root", "src/main/webapp"));
        String source = Files.readString(formRoot.resolve("WEB-INF/jsp/form/formrourke2006" + page + ".jsp"));
        int at = source.indexOf("name=\"c_lastVisited\"");
        assertThat(at).isGreaterThan(0);
        int end = source.indexOf("<input", at);
        String fragment = source.substring(source.lastIndexOf("<input", at), end);
        fragment = fragment.replaceAll("<carlos:encode[\\s\\S]*?/>", page)
                .replaceAll("<%=[\\s\\S]*?%>", page);
        // Parse as HTML, as the browser does: an unquoted value=p1/> really submits p1/.
        assertThat(Jsoup.parse(fragment).selectFirst("input[name=c_lastVisited]").val()).isEqualTo(page);
    }
}
