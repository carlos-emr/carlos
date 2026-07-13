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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the method-level {@code @Transactional} override on every
 * {@link BillingOnLookupService} write method. The class is annotated
 * {@code @Transactional(readOnly=true)}; without method-level overrides
 * Hibernate sets flush mode {@code MANUAL} and silently drops the
 * subsequent {@code merge}/{@code persist}.
 *
 * <p>This is a structural test — it cannot prove the override actually
 * triggers a write at runtime (that needs a Spring-context test). What it
 * DOES prove is the annotation hasn't drifted out of the source: a future
 * "cleanup" that drops the override falls into the silent-flush bug, and
 * this test catches the regression at compile-time-equivalent.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("BillingOnLookupService write-method @Transactional override")
@Tag("unit")
@Tag("billing")
class BillingOnLookupServiceAnnotationUnitTest {

    private static void assertHasMethodLevelTransactional(String name, Class<?>... params) {
        try {
            Method m = BillingOnLookupService.class.getMethod(name, params);
            Transactional tx = m.getAnnotation(Transactional.class);
            assertThat(tx)
                    .as("%s must carry method-level @Transactional to override class-level readOnly=true", name)
                    .isNotNull();
            // Pin readOnly=false too — presence alone isn't enough. A future
            // refactor that adds @Transactional(readOnly=true) at the method
            // level would re-trigger the silent-flush bug yet pass an
            // isAnnotationPresent-only test.
            assertThat(tx.readOnly())
                    .as("%s @Transactional must have readOnly=false to enable Hibernate flushing", name)
                    .isFalse();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("BillingOnLookupService." + name + " not found", e);
        }
    }

    @Test
    void shouldHaveMethodLevelTransactional_onUpdateApptStatus() {
        assertHasMethodLevelTransactional("updateApptStatus", String.class, String.class, String.class);
    }

    @Test
    void shouldHaveMethodLevelTransactional_onAddBillingFavouriteList() {
        assertHasMethodLevelTransactional("addBillingFavouriteList", String.class, String.class, String.class);
    }

    @Test
    void shouldHaveMethodLevelTransactional_onDelBillingFavouriteList() {
        assertHasMethodLevelTransactional("delBillingFavouriteList", String.class, String.class);
    }

    @Test
    void shouldHaveMethodLevelTransactional_onUpdateBillingFavouriteList() {
        assertHasMethodLevelTransactional("updateBillingFavouriteList", String.class, String.class, String.class);
    }
}
