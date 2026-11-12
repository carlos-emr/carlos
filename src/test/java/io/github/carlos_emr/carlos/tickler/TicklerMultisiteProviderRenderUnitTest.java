/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.tickler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Render the real JSP control flow, rather than discarding its loops before parsing JavaScript. */
@Tag("unit")
@Tag("fast")
class TicklerMultisiteProviderRenderUnitTest {
    @TempDir
    static Path scratch;
    static URLClassLoader loader;
    static Method render;

    @BeforeAll
    static void compileActualJspFragment() throws Exception {
        Path jsp = Path.of(System.getProperty("carlos.tickler.jsp",
                "src/main/webapp/WEB-INF/jsp/tickler/ticklerMain.jsp"));
        String source = Files.readString(jsp);
        int start = source.indexOf("let _providers = {};");
        assertThat(start).isGreaterThan(0);
        int end = source.indexOf("</script>", start);
        assertThat(end).isGreaterThan(start);
        String fragment = source.substring(start, end);
        // These are the existing independent HTML and script-context boundaries.
        assertThat(fragment).contains("SafeEncode.forJavaScript(SafeEncode.forHtmlAttribute(p.getProviderNo()))",
                "SafeEncode.forJavaScript(SafeEncode.forHtml(p.getLastName()))",
                "SafeEncode.forJavaScript(SafeEncode.forHtml(p.getFirstName()))");
        StringBuilder java = new StringBuilder("import java.util.*;\n"
                + "import io.github.carlos_emr.carlos.commn.model.Site;\n"
                + "import io.github.carlos_emr.carlos.commn.model.Provider;\n"
                + "import io.github.carlos_emr.carlos.utility.SafeEncode;\n"
                + "public class TicklerProviderFragment {\n"
                + "public static String render(List<Site> sites) {StringBuilder out=new StringBuilder();\n");
        var tags = Pattern.compile("<%(.*?)%>", Pattern.DOTALL).matcher(fragment);
        int cursor = 0;
        while (tags.find()) {
            appendLiteral(java, fragment.substring(cursor, tags.start()));
            String body = tags.group(1);
            if (body.startsWith("=")) java.append("out.append(String.valueOf(").append(body.substring(1)).append("));\n");
            else java.append(body).append('\n');
            cursor = tags.end();
        }
        appendLiteral(java, fragment.substring(cursor));
        java.append("return out.toString();}}\n");
        Path wrapper = scratch.resolve("TicklerProviderFragment.java");
        Files.writeString(wrapper, java.toString());
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assertThat(ToolProvider.getSystemJavaCompiler()).as("A JDK is required for the actual JSP fragment").isNotNull();
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8",
                "-classpath", classpath, "-d", scratch.toString(), wrapper.toString());
        assertThat(exit).as("The JSP fragment's actual Java control flow compiles").isZero();
        loader = new URLClassLoader(new java.net.URL[]{scratch.toUri().toURL()}, TicklerMultisiteProviderRenderUnitTest.class.getClassLoader());
        render = loader.loadClass("TicklerProviderFragment").getMethod("render", List.class);
    }

    private static void appendLiteral(StringBuilder target, String literal) {
        target.append("out.append(\"").append(literal.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")).append("\");\n");
    }

    @AfterAll
    static void closeRenderer() throws Exception {
        if (loader != null) loader.close();
    }

    static Provider provider(String number, String first, String last, String status) {
        Provider provider = new Provider(number);
        provider.setFirstName(first);
        provider.setLastName(last);
        provider.setStatus(status);
        return provider;
    }

    static Site site(int number, Provider... providers) {
        Site site = new Site();
        site.setSiteId(number);
        site.setProviders(new LinkedHashSet<>(List.of(providers)));
        return site;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> renderAndExecute(Site... sites) throws Exception {
        String javascript = (String) render.invoke(null, List.of(sites));
        String executor = "const fs=require('node:fs'),vm=require('node:vm');"
                + "const source=fs.readFileSync(0,'utf8');"
                + "const context={};vm.createContext(context);"
                + "vm.runInContext(source,context,{timeout:1000});"
                + "const values=vm.runInContext('_providers',context);"
                + "for(const key of Object.keys(values)){const input={value:key,form:{assignedTo:{innerHTML:'unchanged'}}};"
                + "context.changeSite(input);if(input.form.assignedTo.innerHTML!==values[key])throw Error('site selection changed options');}"
                + "const none={value:'none',form:{assignedTo:{innerHTML:'unchanged'}}};context.changeSite(none);"
                + "if(none.form.assignedTo.innerHTML!=='')throw Error('none selection retained options');"
                + "process.stdout.write(JSON.stringify(values));";
        Process process = new ProcessBuilder("node", "-e", executor).start();
        try (var input = process.getOutputStream()) { input.write(javascript.getBytes(StandardCharsets.UTF_8)); }
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("The emitted inline JavaScript parsed and executed within the budget").isTrue();
        String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("The JSP-rendered script must parse: %s", errors).isZero();
        return new ObjectMapper().readValue(process.getInputStream().readAllBytes(), Map.class);
    }

    @Test
    void zeroProvidersStillProduceAnEmptySelectableSite() throws Exception {
        assertThat(renderAndExecute(site(1))).containsExactly(Map.entry("1", ""));
    }

    @Test
    void oneActiveProviderPreservesItsValueAndLabel() throws Exception {
        assertThat(renderAndExecute(site(1, provider("1", "One", "First", "1"))).get("1"))
                .isEqualTo("<option value='1'>First, One</option>");
    }

    @Test
    void twoActiveProvidersStayInsideOneSiteString() throws Exception {
        assertThat(renderAndExecute(site(1, provider("1", "One", "First", "1"), provider("2", "Two", "Second", "1"))).get("1"))
                .isEqualTo("<option value='1'>First, One</option><option value='2'>Second, Two</option>");
    }

    @Test
    void inactiveOnlySiteRemainsEmpty() throws Exception {
        assertThat(renderAndExecute(site(1, provider("1", "Inactive", "First", "0"))).get("1")).isEmpty();
    }

    @Test
    void mixedInactiveActiveAndNullStatusProvidersOnlyIncludeActiveValues() throws Exception {
        assertThat(renderAndExecute(site(1, provider("1", "Inactive", "First", "0"),
                provider("2", "Active", "Second", "1"), provider("3", "Unknown", "Third", null))).get("1"))
                .isEqualTo("<option value='2'>Second, Active</option>");
    }

    @Test
    void emptyAndPopulatedSitesKeepIndependentOptions() throws Exception {
        Map<String, String> result = renderAndExecute(site(10), site(20, provider("1", "One", "First", "1"),
                provider("2", "Two", "Second", "1")), site(30));
        assertThat(result).containsKeys("10", "20", "30");
        assertThat(result.get("10")).isEmpty();
        assertThat(result.get("30")).isEmpty();
        assertThat(Jsoup.parseBodyFragment(result.get("20")).select("option")).hasSize(2);
    }

    @Test
    void encodedQuotesUnicodeAndMarkupRemainLiteralProviderLabels() throws Exception {
        String first = "O'Neil \"雪\" &\nsecond line";
        String last = "</option><script id=\"ticklerInjected\">x</script><img onerror=\"x\">";
        Map<String, String> result = renderAndExecute(site(1, provider("1'\"", first, last, "1"),
                provider("2", "Safe", "Second", "1")));
        var document = Jsoup.parseBodyFragment(result.get("1"));
        assertThat(document.select("script,img")).isEmpty();
        assertThat(document.select("option")).hasSize(2);
        assertThat(document.select("option").first().attr("value")).isEqualTo("1'\"");
        assertThat(document.select("option").first().wholeText()).isEqualTo(last + ", " + first);
    }
}
