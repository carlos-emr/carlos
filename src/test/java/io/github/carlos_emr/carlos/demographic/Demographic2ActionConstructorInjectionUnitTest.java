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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.struts2.spring.SpringObjectFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AnnotatedElementUtils;
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
    private static final Path STRUTS_CONFIG_DIR = Path.of("src/main/webapp/WEB-INF/classes");
    private static final Set<String> STRUTS_ACTION_CLASSES = configuredActionClasses();

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("constructorInjectedActions")
    @DisplayName("should instantiate through the configured Struts Spring factory")
    void shouldBuildThroughConfiguredStrutsFactory_forEachConstructorInjectedAction(Class<?> actionClass) throws Exception {
        Component component = AnnotatedElementUtils.findMergedAnnotation(actionClass, Component.class);
        if (component != null && !component.value().isBlank() && STRUTS_ACTION_CLASSES.contains(component.value())) {
            // The real Struts 7.4 factory resolves an explicitly named bean before
            // falling back to class construction. Spring owns these constructors.
            assertThat(STRUTS_ACTION_CLASSES).doesNotContain(actionClass.getName());
            Scope scope = AnnotatedElementUtils.findMergedAnnotation(actionClass, Scope.class);
            assertThat(scope).as("%s must have request-independent action instances", actionClass.getName()).isNotNull();
            assertThat(scope.value()).isEqualTo("prototype");
            try (var context = new AnnotationConfigApplicationContext()) {
                for (Class<?> dependency : injectionConstructor(actionClass).getParameterTypes()) {
                    if (!dependency.isPrimitive()) {
                        context.getBeanFactory().registerResolvableDependency(dependency, testDependency(dependency));
                    }
                }
                context.register(actionClass);
                context.refresh();
                assertThat(context.isPrototype(component.value())).isTrue();
                var factory = new SpringObjectFactory();
                factory.setApplicationContext(context);
                assertThat(factory.getAutowireStrategy()).isEqualTo(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);
                Object first = factory.buildBean(component.value(), Map.of(), true);
                Object second = factory.buildBean(component.value(), Map.of(), true);
                assertThat(first).isInstanceOf(actionClass).isNotSameAs(second);
                assertThat(second).isInstanceOf(actionClass);
            }
            return;
        }
        // Legacy actions referenced by class name still take the default name-
        // autowire path. Keep the original public no-arg requirement for them.
        assertThat(hasPublicNoArgConstructor(actionClass))
                .as("%s must keep a public no-arg constructor when Struts constructs it by class name", actionClass.getName())
                .isTrue();
    }

    /** Follow only the actual included application configuration; comments cannot invent mappings. */
    private static Set<String> configuredActionClasses() {
        var factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External Struts configuration resources are forbidden in this test");
        });
        Path root = STRUTS_CONFIG_DIR.toAbsolutePath().normalize();
        var pending = new ArrayDeque<Path>();
        var visited = new HashSet<Path>();
        var classes = new HashSet<String>();
        pending.add(root.resolve("struts.xml"));
        try {
            while (!pending.isEmpty()) {
                Path file = pending.removeFirst();
                if (!visited.add(file)) {
                    continue;
                }
                if (!file.startsWith(root) || visited.size() > 100) {
                    throw new IllegalStateException("Unexpected Struts configuration include scope");
                }
                try (var input = Files.newInputStream(file)) {
                    var xml = factory.createXMLStreamReader(input, StandardCharsets.UTF_8.name());
                    try {
                        while (xml.hasNext()) {
                            if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                                continue;
                            }
                            if (xml.getLocalName().equals("include")) {
                                String include = xml.getAttributeValue(null, "file");
                                if (include == null) {
                                    throw new IllegalStateException("Struts include has no file");
                                }
                                pending.add(root.resolve(include).normalize());
                            } else if (xml.getLocalName().equals("action")) {
                                String action = xml.getAttributeValue(null, "class");
                                if (action != null) {
                                    classes.add(action);
                                }
                            }
                        }
                    } finally {
                        xml.close();
                    }
                }
            }
            if (classes.isEmpty()) {
                throw new IllegalStateException("No configured Struts actions were found");
            }
            return Set.copyOf(classes);
        } catch (IOException | XMLStreamException failure) {
            throw new IllegalStateException("Cannot verify the actual included Struts action mappings", failure);
        }
    }

    private static boolean hasPublicNoArgConstructor(Class<?> actionClass) {
        return Arrays.stream(actionClass.getConstructors())
                .anyMatch(constructor -> constructor.getParameterCount() == 0);
    }

    /**
     * Returns the dependency-injection entry point: the public constructor with the most
     * parameters. Legacy class-mapped actions retain their delegating no-arg constructor.
     * Explicitly named prototype beans use Spring's constructor injection through the
     * actual Struts factory, without changing its global autowire strategy.
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
