/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.owasp.encoder.Encode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("form")
class FrmAlphaRenderingUnitTest {
    private static final String PAYLOAD = "FAKE </textarea><script>alert('owned')</script> \" & clinical suffix";

    @ParameterizedTest
    @ValueSource(strings = {"socialSupport", "lifeEvents", "coupleRelationship", "prenatalCare", "prenatalEducation",
            "feelingsRePregnancy", "relationshipParents", "selfEsteem", "psychHistory", "depression",
            "alcoholDrugAbuse", "abuse", "womanAbuse", "childAbuse", "childDiscipline", "comments"})
    void shouldRedisplayAllClinicalProseLiterallyWithoutCreatingMarkup(String field) throws Exception {
        String source = source();
        Matcher area = Pattern.compile("(<textarea\\b[^>]*name=\"" + field + "\"[^>]*>)([\\s\\S]*?)(</textarea>)").matcher(source);
        assertThat(area.find()).as(field).isTrue();
        var document = Jsoup.parse(area.group(1) + renderProperties(area.group(2)) + area.group(3));
        assertThat(document.select("textarea")).hasSize(1);
        assertThat(document.selectFirst("textarea").val()).isEqualTo(PAYLOAD);
        assertThat(document.select("script")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pName", "other1Name", "other2Name", "other3Name", "other4Name"})
    void shouldKeepStoredNamesInsideTheirInputValues(String field) throws Exception {
        String source = source();
        int at = source.indexOf("name=\"" + field + "\"");
        assertThat(at).isGreaterThan(0);
        int end = source.indexOf("<input", at);
        String fragment = source.substring(source.lastIndexOf("<input", at), end < 0 ? source.length() : end);
        var document = Jsoup.parse(renderProperties(fragment));
        var input = document.selectFirst("input[name=" + field + "]");
        assertThat(input.val()).isEqualTo(PAYLOAD);
        assertThat(input.hasAttr("autofocus") || input.hasAttr("onfocus")).isFalse();
        assertThat(document.select("script")).isEmpty();
    }

    private String source() throws Exception {
        Path root = Path.of(System.getProperty("form.source.root", "src/main/webapp"));
        return Files.readString(root.resolve("WEB-INF/jsp/form/formalpha.jsp"));
    }

    private String renderProperties(String fragment) {
        Pattern tags = Pattern.compile("<carlos:encode value='<%=\\s*props.getProperty\\(\"[^\"]+\",\\s*\"[^\"]*\"\\)\\s*%>' context=\"(html|htmlAttribute)\"/>");
        Matcher found = tags.matcher(fragment);
        StringBuffer result = new StringBuffer();
        while (found.find()) found.appendReplacement(result, Matcher.quoteReplacement(found.group(1).equals("html")
                ? Encode.forHtml(PAYLOAD) : Encode.forHtmlAttribute(PAYLOAD)));
        found.appendTail(result);
        // Model the same stored value at a legacy unencoded expression to make this fail on original JSP markup.
        return result.toString().replaceAll("<%=\\s*props.getProperty\\(\"[^\"]+\",\\s*\"[^\"]*\"\\)\\s*%>", Matcher.quoteReplacement(PAYLOAD));
    }
}
