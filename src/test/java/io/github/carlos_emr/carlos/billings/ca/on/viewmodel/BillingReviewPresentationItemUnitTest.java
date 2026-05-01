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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Billing review presentation items")
@Tag("unit")
@Tag("billing")
class BillingReviewPresentationItemUnitTest {

    @Test
    void shouldUseImmutableRecordsForBillingReviewItems() {
        assertThat(BillingReviewCodeItem.class.isRecord()).isTrue();
        assertThat(BillingReviewPercentageItem.class.isRecord()).isTrue();
        assertThat(hasSetter(BillingReviewCodeItem.class)).isFalse();
        assertThat(hasSetter(BillingReviewPercentageItem.class)).isFalse();
    }

    @Test
    void shouldTypePercentageFeeListsAsStringLists() {
        RecordComponent feeComponent = recordComponent(BillingReviewPercentageItem.class, "codeFees");
        RecordComponent totalComponent = recordComponent(BillingReviewPercentageItem.class, "codeTotals");

        assertThat(feeComponent.getGenericType().getTypeName()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(totalComponent.getGenericType().getTypeName()).isEqualTo("java.util.List<java.lang.String>");
    }

    private static boolean hasSetter(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("set"));
    }

    private static RecordComponent recordComponent(Class<?> type, String name) {
        return Arrays.stream(type.getRecordComponents())
                .filter(component -> component.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
