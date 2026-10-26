/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.utility.SafeEncode;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Execute the actual JSP output block with the production encoder and DatabaseAP API. */
@Tag("unit")
@Tag("fast")
class ApCacheLookupLiteralOutputUnitTest {
    @TempDir
    static Path scratch;
    static URLClassLoader loader;
    static Method render;

    @BeforeAll
    static void compileActualJspOutputBlock() throws Exception {
        Path jsp = Path.of(System.getProperty("carlos.apcache.lookup.jsp",
                "src/main/webapp/WEB-INF/jsp/eform/efmformapconfig_lookup.jsp"));
        String source = Files.readString(jsp);
        int mismatch = source.indexOf("} else if (values.size() != names.size()) {");
        assertThat(mismatch).isGreaterThan(0);
        int start = source.indexOf("                    } else {", mismatch);
        int end = source.indexOf("\n                    }\n                }\n%><input", start);
        assertThat(start).isGreaterThan(mismatch);
        assertThat(end).isGreaterThan(start);
        String body = source.substring(start + "                    } else {".length(), end);
        // The JSP's existing attribute-encoding boundary stays independently enforced.
        assertThat(source.substring(end)).startsWith("\n                    }\n                }\n%><input")
                .contains("value='<%= output %>' context=\"htmlAttribute\"");
        String wrapper = "import java.util.*;\n"
                + "import io.github.carlos_emr.carlos.eform.data.DatabaseAP;\n"
                + "import io.github.carlos_emr.carlos.utility.SafeEncode;\n"
                + "public class ApCacheLookupFragment {public static String render(String output,ArrayList<String> names,ArrayList<String> values) {"
                + body + "\nreturn output;}}\n";
        Path java = scratch.resolve("ApCacheLookupFragment.java");
        Files.writeString(java, wrapper);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assertThat(ToolProvider.getSystemJavaCompiler()).isNotNull();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8", "-classpath",
                classpath, "-d", scratch.toString(), java.toString())).isZero();
        loader = new URLClassLoader(new java.net.URL[]{scratch.toUri().toURL()}, ApCacheLookupLiteralOutputUnitTest.class.getClassLoader());
        render = loader.loadClass("ApCacheLookupFragment").getMethod("render", String.class, ArrayList.class, ArrayList.class);
    }

    @AfterAll
    static void closeRenderer() throws Exception {
        if (loader != null) loader.close();
    }

    static String renderValue(String template, List<String> names, List<String> values) throws Exception {
        String output = (String) render.invoke(null, template, new ArrayList<>(names), new ArrayList<>(values));
        var document = Jsoup.parseBodyFragment("<input id='actual' type='hidden' value=\""
                + SafeEncode.forHtmlAttribute(output) + "\">");
        assertThat(document.select("input")).hasSize(1);
        assertThat(document.select("script,img,textarea")).isEmpty();
        return document.selectFirst("#actual").val();
    }

    @Test
    void crossAliasClinicalDataRemainsLiteralInBothDirections() throws Exception {
        assertThat(renderValue("${first}|${second}", List.of("first", "second"), List.of("${second}", "actual result")))
                .isEqualTo("${second}|actual result");
        assertThat(renderValue("${first}|${second}", List.of("first", "second"), List.of("actual result", "${first}")))
                .isEqualTo("actual result|${first}");
    }

    @Test
    void duplicateAuthoredMarkersNeverReparseEarlierInsertedData() throws Exception {
        assertThat(renderValue("${first}|${second}|${first}", List.of("first", "second", "first"),
                List.of("${second}", "${first}", "later value")))
                .isEqualTo("${second}|${first}|${second}");
    }

    @Test
    void ordinarySignedThresholdExponentAndUnicodeValuesAreUnchanged() throws Exception {
        for (String value : List.of("-4.25", "<5", ">=7.0", "1.2e-3", "≤5 Δ Na+", "O'Neil & \"quoted\"")) {
            assertThat(renderValue("Result: ${value}", List.of("value"), List.of(value)))
                    .isEqualTo("Result: " + SafeEncode.forHtml(value));
        }
    }

    @Test
    void nullAndEmptyDataStayEmptyAndFirstDuplicateNullWins() throws Exception {
        assertThat(renderValue("a${first}b${second}c${first}", List.of("first", "second", "first"),
                Arrays.asList(null, "", "later value"))).isEqualTo("abc");
    }

    @Test
    void selfMarkersMalformedMarkersAndUnknownMarkersRemainLiteral() throws Exception {
        for (String value : List.of("${value}", "${unfinished", "$1\\tail", "${unknown}")) {
            assertThat(renderValue("${value}|${value}", List.of("value", "value"), List.of(value, "later")))
                    .isEqualTo(SafeEncode.forHtml(value) + "|" + SafeEncode.forHtml(value));
        }
    }

    @Test
    void markupAndCrossAliasClinicalDataRemainEncodedWithoutNewDomNodes() throws Exception {
        String value = "\"><img id='injected' src=x onerror='alert(1)'><script>alert(2)</script>${second}";
        assertThat(renderValue("${first}|${second}", List.of("first", "second"), List.of(value, "safe")))
                .isEqualTo(SafeEncode.forHtml(value) + "|safe");
    }
}
