/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prevention;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Executes the actual JSP dose scriptlet for initial edits and refused-submission redisplays. */
@Tag("unit")
@Tag("prevention")
class PreventionDoseRedisplayUnitTest {
    @TempDir static Path directory;
    private static URLClassLoader loader;
    private static Method render;

    @BeforeAll
    static void compileProductionDoseScriptlet() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/prevention/AddPreventionData.jsp"));
        int start = jsp.indexOf("String dose = Objects.toString(extraData.get(\"dose\"), \"\");");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = jsp.indexOf("%>", start);
        assertThat(end).isGreaterThan(start);
        String source = """
                import java.util.*;
                import org.springframework.mock.web.MockHttpServletRequest;
                import io.github.carlos_emr.carlos.prevention.PreventionFormCodes;
                public class DoseFragmentFixture {
                    public static String[] render(String stored, String submitted, String submittedUnit, boolean existing) {
                        Map<String,String> extraData = new HashMap<>();
                        extraData.put("dose", stored);
                        Object existingPrevention = existing ? new Object() : null;
                        var request = new MockHttpServletRequest();
                        if (submitted != null) request.setParameter("dose", submitted);
                        if (submittedUnit != null) request.setParameter("doseUnit", submittedUnit);
                        String pDose = Objects.toString(submitted, "");
                        String pUnit = Objects.toString(submittedUnit, "");
                """ + jsp.substring(start, end) + "\nreturn new String[]{d1,d2};\n}\n}\n";
        Path file = directory.resolve("DoseFragmentFixture.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("The release test lane requires a JDK").isNotNull();
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            boolean compiled = compiler.getTask(null, manager, null,
                    List.of("--release", "25", "-classpath", classpath, "-d", directory.toString()), null,
                    manager.getJavaFileObjects(file)).call();
            assertThat(compiled).isTrue();
        }
        loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, PreventionDoseRedisplayUnitTest.class.getClassLoader());
        render = loader.loadClass("DoseFragmentFixture").getMethod("render", String.class, String.class, String.class, boolean.class);
    }

    @AfterAll
    static void closeFixtureLoader() throws Exception {
        if (loader != null) loader.close();
    }

    static Stream<Arguments> doses() {
        return Stream.of(
                Arguments.of("1 mL", "2.75", "vial", true, "2.75", "vial"),
                Arguments.of("1 mL", "", "", true, "", ""),
                Arguments.of("2 mg", "0", "mg", true, "0", "mg"),
                Arguments.of("0.5", null, null, true, "0.5", ""),
                Arguments.of("1 mL", null, null, true, "1", "mL"),
                Arguments.of("0.5 mL", null, null, true, "0.5", "mL"),
                Arguments.of(".5 mL", null, null, true, ".5", "mL"),
                Arguments.of("half a vial", null, null, true, "half a vial", ""),
                Arguments.of("FAKE & \"dose\"", null, null, true, "FAKE & \"dose\"", ""),
                Arguments.of(null, null, null, false, "", "mL"),
                Arguments.of(null, "0.5", "VIAL", false, "0.5", "vial"));
    }

    @ParameterizedTest
    @MethodSource("doses")
    void shouldKeepSubmittedValues_onRedisplayAndStoredValues_onInitialEdit(
            String stored, String submitted, String unit, boolean existing, String expectedDose, String expectedUnit) throws Exception {
        assertThat((String[]) render.invoke(null, stored, submitted, unit, existing))
                .containsExactly(expectedDose, expectedUnit);
    }
}
