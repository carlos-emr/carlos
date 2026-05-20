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
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Contract tests for dashboard query handlers that are registered as Spring
 * beans and proxied through CGLIB for inherited transaction advice.
 *
 * @since 2026-05-20
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("Dashboard query handler proxy contract")
class DashboardQueryHandlerProxyContractTest {

    private static final String DASHBOARD_HANDLER_PACKAGE =
            "io.github.carlos_emr.carlos.dashboard.handler";

    @Test
    @DisplayName("should not declare final methods visible to CGLIB proxies")
    void shouldHaveNoFinalMethods_forCglibProxiedHandlers() throws ClassNotFoundException {
        List<String> cglibVisibleFinalMethodNames = discoverProxiedQueryHandlers().stream()
                .flatMap(DashboardQueryHandlerProxyContractTest::findCglibVisibleFinalMethods)
                .toList();

        assertThat(cglibVisibleFinalMethodNames)
                .as("CGLIB cannot advise non-private final handler methods")
                .isEmpty();
    }

    private static boolean isCglibVisibleFinalMethod(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isFinal(modifiers)
                && !Modifier.isStatic(modifiers)
                && !Modifier.isPrivate(modifiers)
                && !method.isSynthetic()
                && !method.isBridge();
    }

    private static boolean isCompilerGeneratedMethod(Method method) {
        return method.isSynthetic()
                || method.isBridge()
                || method.getName().startsWith("lambda$");
    }

    private static Stream<String> findCglibVisibleFinalMethods(Class<?> handlerType) {
        return proxiedHandlerHierarchy(handlerType)
                .flatMap(declaringType -> Arrays.stream(declaringType.getDeclaredMethods())
                        .filter(method -> !isCompilerGeneratedMethod(method))
                        .filter(DashboardQueryHandlerProxyContractTest::isCglibVisibleFinalMethod)
                        .map(method -> declaringType.getSimpleName() + "#" + method.getName()
                                + " inherited by " + handlerType.getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends AbstractQueryHandler>> discoverProxiedQueryHandlers()
            throws ClassNotFoundException {
        Set<Class<? extends AbstractQueryHandler>> handlerTypes = new LinkedHashSet<>();
        handlerTypes.add(AbstractQueryHandler.class);

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(AbstractQueryHandler.class));

        for (var beanDefinition : scanner.findCandidateComponents(DASHBOARD_HANDLER_PACKAGE)) {
            Class<?> handlerType = Class.forName(beanDefinition.getBeanClassName());
            if (AbstractQueryHandler.class.isAssignableFrom(handlerType)) {
                handlerTypes.add((Class<? extends AbstractQueryHandler>) handlerType);
            }
        }

        assertThat(handlerTypes)
                .as("Expected to discover at least one concrete AbstractQueryHandler implementation under package '%s', but only the abstract base type was found. Check the scanner package (%s) and handler locations.",
                        DASHBOARD_HANDLER_PACKAGE, DASHBOARD_HANDLER_PACKAGE)
                .hasSizeGreaterThan(1);

        return handlerTypes;
    }

    private static Stream<Class<?>> proxiedHandlerHierarchy(Class<?> handlerType) {
        Stream.Builder<Class<?>> hierarchy = Stream.builder();
        Class<?> currentType = handlerType;
        while (currentType != null && AbstractQueryHandler.class.isAssignableFrom(currentType)) {
            hierarchy.add(currentType);
            currentType = currentType.getSuperclass();
        }
        return hierarchy.build();
    }
}
