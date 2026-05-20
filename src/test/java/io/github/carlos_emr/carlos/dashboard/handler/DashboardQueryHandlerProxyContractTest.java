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
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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

    private static final List<Class<? extends AbstractQueryHandler>> PROXIED_QUERY_HANDLERS = List.of(
            AbstractQueryHandler.class,
            DrilldownQueryHandler.class,
            ExportQueryHandler.class,
            IndicatorQueryHandler.class);

    @Test
    @DisplayName("should not declare final methods visible to CGLIB proxies")
    void shouldNotDeclareFinalMethods_forCglibProxiedHandlers() {
        List<String> finalMethods = PROXIED_QUERY_HANDLERS.stream()
                .flatMap(handlerType -> Arrays.stream(handlerType.getDeclaredMethods())
                        .filter(DashboardQueryHandlerProxyContractTest::isCglibVisibleFinalMethod)
                        .map(method -> handlerType.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(finalMethods)
                .as("CGLIB cannot advise non-private final handler methods")
                .isEmpty();
    }

    private static boolean isCglibVisibleFinalMethod(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isFinal(modifiers)
                && !Modifier.isStatic(modifiers)
                && !Modifier.isPrivate(modifiers);
    }
}
