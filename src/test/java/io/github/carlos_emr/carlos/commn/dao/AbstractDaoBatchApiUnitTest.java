/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Explicit DAO batch transaction APIs")
@Tag("unit")
@Tag("fast")
class AbstractDaoBatchApiUnitTest {

    @Test
    @DisplayName("should make atomic and independent-commit behavior explicit in the API")
    void shouldDeclareUnambiguousTransactionPropagation() throws Exception {
        assertPropagation("batchPersistAtomically", Propagation.REQUIRED);
        assertPropagation("batchPersistWithIndependentCommits", Propagation.NEVER);
        assertPropagation("batchRemoveAtomically", Propagation.REQUIRED);
        assertPropagation("batchRemoveWithIndependentCommits", Propagation.NEVER);
    }

    @Test
    @DisplayName("should retain legacy methods only as deprecated compatibility APIs")
    void shouldDeprecateAmbiguousLegacyMethods() throws Exception {
        assertThat(AbstractDao.class.getMethod("batchPersist", List.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(AbstractDao.class.getMethod("batchRemove", List.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
    }

    private static void assertPropagation(String methodName, Propagation expected) throws Exception {
        Method method = AbstractDaoImpl.class.getMethod(methodName, List.class);
        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(expected);
        Method sizedMethod = AbstractDaoImpl.class.getMethod(methodName, List.class, int.class);
        assertThat(sizedMethod.getAnnotation(Transactional.class).propagation()).isEqualTo(expected);
    }
}
