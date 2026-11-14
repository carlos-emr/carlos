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
package io.github.carlos_emr.carlos.test.mocks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.mockito.listeners.MockCreationListener;
import org.mockito.mock.MockCreationSettings;

/**
 * Releases the mocks a test class created when the class has finished.
 *
 * <p>Mockito's inline mock maker keeps every mock, with every invocation recorded on it, until
 * the mock is unreachable. A stubbed answer or a recorded argument that refers back to the mock
 * (a lambda that captures the test instance is enough) keeps it reachable for the life of the
 * JVM. Most tests record a few hundred invocations and nobody notices. A test that drives a
 * mocked reader or run control through a chart of thousands of rows records millions, and a
 * lane that runs every CDS test class in one JVM then exhausts its heap in some later class.
 *
 * <p>This extension notes the mocks created on the test thread while the class runs and hands
 * exactly those to {@link org.mockito.MockitoFramework#clearInlineMock(Object)} afterwards. It
 * never clears a mock another class made, so a cached Spring test context that holds mock beans
 * is left alone. It is for plain unit tests: do not put it on a class that loads a Spring test
 * context, because mock beans created while that context starts would be released with the
 * class and a later class reusing the cached context would find them disabled.
 *
 * <p>Usage: {@code @ExtendWith(MockInvocationReleaseExtension.class)} on the top-level test
 * class. Nested test classes share their outer class's collection.
 *
 * @since 2026-11-14
 */
public final class MockInvocationReleaseExtension implements BeforeAllCallback, AfterAllCallback {

    /** The collection of the top-level class running on this thread, if any. */
    private static final ThreadLocal<Collector> ACTIVE = new ThreadLocal<>();

    @Override
    public void beforeAll(ExtensionContext context) {
        // A nested class runs inside its outer class's collection.
        if (ACTIVE.get() != null) return;
        Collector collector = new Collector(context.getRequiredTestClass());
        Mockito.framework().addListener(collector);
        ACTIVE.set(collector);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Collector collector = ACTIVE.get();
        if (collector == null || collector.owner != context.getRequiredTestClass()) return;
        ACTIVE.remove();
        Mockito.framework().removeListener(collector);
        for (Object mock : collector.created) Mockito.framework().clearInlineMock(mock);
        collector.created.clear();
    }

    /** The mocks created on this thread while one top-level test class runs. */
    private static final class Collector implements MockCreationListener {
        private final Class<?> owner;
        private final Set<Object> created = Collections.newSetFromMap(new IdentityHashMap<>());

        private Collector(Class<?> owner) {
            this.owner = owner;
        }

        @Override
        public void onMockCreated(Object mock, MockCreationSettings settings) {
            created.add(mock);
        }
    }
}
