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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the write-side contract of the cached reference-data DAOs (issue #5304).
 *
 * <p>Each DAO below serves reads from a shared cache through {@code CacheManager} and returns
 * copies. That is only safe while every write that the DAO inherits from
 * {@link AbstractDaoImpl} is overridden at the proxied boundary with an all-entries
 * {@link CacheEvict} for the DAO's own cache: the inherited batch methods call
 * {@code persist}/{@code remove} on a separate {@code EntityManager}, bypassing the Spring
 * proxy, so an override that is missing (for example a new {@code AbstractDaoImpl} write
 * overload) would leave readers on stale data until the TTL expires. The non-atomic batch
 * writes commit sub-batches as they go, so they must evict before they run.</p>
 *
 * <p>The cache-hit/miss copy behaviour and the after-commit eviction itself are exercised
 * against a database by the {@code *DaoCacheIntegrationTest} classes.</p>
 */
@DisplayName("Reference-data DAO cache eviction contract")
@Tag("unit")
@Tag("dao")
@Tag("fast")
@Tag("cache")
class ReferenceDataCacheEvictionContractUnitTest {

    static Stream<Arguments> cachedDaos() {
        return Stream.of(
                Arguments.of(AppointmentStatusDaoImpl.class, CacheConfig.APPOINTMENT_STATUSES),
                Arguments.of(MeasurementTypeDaoImpl.class, CacheConfig.MEASUREMENT_TYPES),
                Arguments.of(LookupListDaoImpl.class, CacheConfig.LOOKUP_LISTS),
                Arguments.of(AppointmentTypeDaoImpl.class, CacheConfig.APPOINTMENT_TYPES),
                Arguments.of(ScheduleTemplateCodeDaoImpl.class, CacheConfig.SCHEDULE_TEMPLATE_CODES),
                Arguments.of(FacilityDaoImpl.class, CacheConfig.FACILITIES));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachedDaos")
    @DisplayName("should evict the DAO's own cache from every inherited write overload")
    void shouldEvictOwnCache_fromEveryInheritedWriteOverload(Class<?> daoClass, String cacheName) {
        Class<?> modelClass = modelClassOf(daoClass);
        List<Method> writes = inheritedWriteMethods();
        assertThat(writes)
                .as("AbstractDaoImpl write methods discovered")
                .hasSizeGreaterThanOrEqualTo(17);

        for (Method inherited : writes) {
            Method override = overrideIn(daoClass, inherited, modelClass);
            assertThat(override)
                    .as("%s must override %s so its eviction runs at the proxied boundary",
                            daoClass.getSimpleName(), describe(inherited))
                    .isNotNull();

            CacheEvict evict = AnnotatedElementUtils.findMergedAnnotation(override, CacheEvict.class);
            assertThat(evict)
                    .as("%s.%s must be annotated @CacheEvict", daoClass.getSimpleName(), describe(inherited))
                    .isNotNull();
            assertThat(evict.cacheNames())
                    .as("%s.%s must evict %s", daoClass.getSimpleName(), describe(inherited), cacheName)
                    .containsExactly(cacheName);
            assertThat(evict.allEntries())
                    .as("%s.%s must evict all entries: list and single-row keys both go stale",
                            daoClass.getSimpleName(), describe(inherited))
                    .isTrue();
            if (commitsAsItGoes(inherited)) {
                assertThat(evict.beforeInvocation())
                        .as("%s.%s commits sub-batches as it goes, so it must evict before it runs",
                                daoClass.getSimpleName(), describe(inherited))
                        .isTrue();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cachedDaos")
    @DisplayName("should not use @Cacheable, which would hand every caller the cached instances")
    void shouldNotUseCacheable_forCopyOnReadDaos(Class<?> daoClass, String cacheName) {
        for (Method method : daoClass.getDeclaredMethods()) {
            assertThat(AnnotatedElementUtils.hasAnnotation(method, Cacheable.class))
                    .as("%s.%s must read %s through CacheManager and return copies, not @Cacheable",
                            daoClass.getSimpleName(), method.getName(), cacheName)
                    .isFalse();
        }
    }

    private static List<Method> inheritedWriteMethods() {
        List<Method> writes = new ArrayList<>();
        for (Method method : AbstractDaoImpl.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic() && isWrite(method.getName())) {
                writes.add(method);
            }
        }
        return writes;
    }

    private static boolean isWrite(String name) {
        return name.equals("persist") || name.equals("merge") || name.equals("remove")
                || name.equals("saveEntity") || name.startsWith("batch");
    }

    /*
     * The plain batchPersist/batchRemove and the *WithIndependentCommits variants commit as they
     * go; only the *Atomically variants roll back as a whole.
     */
    private static boolean commitsAsItGoes(Method method) {
        return method.getName().startsWith("batch") && !method.getName().endsWith("Atomically");
    }

    private static Method overrideIn(Class<?> daoClass, Method inherited, Class<?> modelClass) {
        Type[] genericTypes = inherited.getGenericParameterTypes();
        Class<?>[] parameterTypes = new Class<?>[genericTypes.length];
        for (int i = 0; i < genericTypes.length; i++) {
            parameterTypes[i] = erasureFor(genericTypes[i], inherited.getParameterTypes()[i], modelClass);
        }
        try {
            Method override = daoClass.getDeclaredMethod(inherited.getName(), parameterTypes);
            return override.isBridge() ? null : override;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /*
     * AbstractDaoImpl<T>.saveEntity(T) is overridden as saveEntity(<Model>), so a bare type
     * variable resolves to the DAO's model class; List<T> stays List.
     */
    private static Class<?> erasureFor(Type genericType, Class<?> rawType, Class<?> modelClass) {
        if (genericType instanceof TypeVariable<?>) {
            return modelClass;
        }
        return rawType;
    }

    private static Class<?> modelClassOf(Class<?> daoClass) {
        Type superclass = daoClass.getGenericSuperclass();
        assertThat(superclass)
                .as("%s must extend AbstractDaoImpl<Model>", daoClass.getSimpleName())
                .isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterized = (ParameterizedType) superclass;
        assertThat(parameterized.getRawType()).isEqualTo(AbstractDaoImpl.class);
        return (Class<?>) parameterized.getActualTypeArguments()[0];
    }

    private static String describe(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types[i].getSimpleName());
        }
        return sb.append(')').toString();
    }
}
