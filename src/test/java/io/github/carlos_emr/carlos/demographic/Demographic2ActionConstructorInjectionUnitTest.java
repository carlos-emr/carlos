/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit coverage for demographic 2Action constructor injection wiring.
 *
 * @since 2026-06-01
 */
@DisplayName("Demographic 2Action constructor injection")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class Demographic2ActionConstructorInjectionUnitTest extends CarlosWebTestBase {

    private static final Path DEMOGRAPHIC_SOURCE_DIR =
            Path.of("src/main/java/io/github/carlos_emr/carlos/demographic");
    private static final String DEMOGRAPHIC_PACKAGE = "io.github.carlos_emr.carlos.demographic";

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructorInjectedActions")
    @DisplayName("should instantiate actions with constructor dependencies")
    void shouldInstantiate_withConstructorDependencies(Class<?> actionClass) throws Exception {
        Constructor<?> constructor = injectionConstructor(actionClass);
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(Demographic2ActionConstructorInjectionUnitTest::testDependency)
                .toArray();

        Object action = constructor.newInstance(dependencies);

        assertThat(action).isInstanceOf(actionClass);
    }

    /**
     * Returns the dependency-injection entry point: the public constructor with the most
     * parameters. Actions also expose a no-arg constructor that delegates to this one via
     * {@code SpringUtils.getBean(...)} so Struts (default {@code name} autowire) can build
     * them, while tests and Spring constructor wiring use the parameterized constructor.
     */
    private static Constructor<?> injectionConstructor(Class<?> actionClass) {
        Constructor<?> injection = Arrays.stream(actionClass.getConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        assertThat(injection.getParameterCount())
                .as("%s should expose a constructor-injection entry point with dependencies", actionClass.getName())
                .isPositive();
        return injection;
    }

    private static Object testDependency(Class<?> dependencyType) {
        if (dependencyType == String.class) {
            return "";
        }
        if (dependencyType == Integer.class || dependencyType == int.class) {
            return 0;
        }
        if (dependencyType == Long.class || dependencyType == long.class) {
            return 0L;
        }
        if (dependencyType == Boolean.class || dependencyType == boolean.class) {
            return false;
        }
        return mock(dependencyType);
    }

    private static Stream<Class<?>> constructorInjectedActions() throws IOException {
        try (Stream<Path> sourceFiles = Files.walk(DEMOGRAPHIC_SOURCE_DIR)) {
            return sourceFiles
                    .filter(path -> path.getFileName().toString().endsWith("2Action.java"))
                    .filter(Demographic2ActionConstructorInjectionUnitTest::declaresConstructorInjectedDependencies)
                    .map(Demographic2ActionConstructorInjectionUnitTest::sourcePathToClass)
                    .filter(Demographic2ActionConstructorInjectionUnitTest::isConcreteClass)
                    .sorted(Comparator.comparing(Class::getName))
                    .toList()
                    .stream();
        }
    }

    private static boolean declaresConstructorInjectedDependencies(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains("private final transient ");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + path, e);
        }
    }

    private static Class<?> sourcePathToClass(Path path) {
        String relativeClassName = DEMOGRAPHIC_SOURCE_DIR.relativize(path).toString()
                .replace('/', '.')
                .replace('\\', '.')
                .replaceAll("\\.java$", "");
        try {
            return Class.forName(DEMOGRAPHIC_PACKAGE + "." + relativeClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load action class for " + path, e);
        }
    }

    private static boolean isConcreteClass(Class<?> actionClass) {
        int modifiers = actionClass.getModifiers();
        return !actionClass.isInterface() && !Modifier.isAbstract(modifiers);
    }
}
