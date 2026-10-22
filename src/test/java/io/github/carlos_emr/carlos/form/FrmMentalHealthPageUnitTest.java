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

/**
 * The page a saved Mental Health record reopens on (finding 236). The chart's entry includes
 * formmh&lt;c_lastVisited&gt;.jsp, so only a shipped page may be named, and the slash that the old
 * unquoted Referral and Assessment inputs saved ("referral/") must still reopen its page.
 */
@Tag("unit")
@Tag("form")
class FrmMentalHealthPageUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"referral", "assessment", "outcome", "referral/", "assessment/", "outcome/"})
    void shouldReopenTheLastVisitedShippedPage_includingLegacySlashValues(String page) {
        String resolved = FrmMentalHealthRecord.resolveLastVisitedPage(page);
        assertThat(resolved).isEqualTo(page.replace("/", ""));
        assertThat(Path.of("src/main/webapp/WEB-INF/jsp/form/formmh" + resolved + ".jsp")).exists();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Referral", "referral//", "outcome/x", "../outcome", "assessment?x", " outcome", "print"})
    void shouldFallBackToReferral_forAnythingOutsideTheShippedPageSet(String page) {
        assertThat(FrmMentalHealthRecord.resolveLastVisitedPage(page)).isEqualTo("referral");
    }

    @ParameterizedTest
    @ValueSource(strings = {"referral", "assessment", "outcome"})
    void shouldSubmitTheExactPageToken_fromEveryHiddenInput(String page) throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/form/formmh" + page + ".jsp"));
        int at = source.indexOf("name=\"c_lastVisited\"");
        assertThat(at).isGreaterThan(0);
        String fragment = source.substring(source.lastIndexOf("<input", at), source.indexOf("<input", at));
        fragment = fragment.replaceAll("<carlos:encode[\\s\\S]*?/>", page).replaceAll("<%=[\\s\\S]*?%>", page);
        // Parsed as the browser does: an unquoted value=referral/> really submits "referral/".
        assertThat(Jsoup.parse(fragment).selectFirst("input[name=c_lastVisited]").val()).isEqualTo(page);
    }
}
