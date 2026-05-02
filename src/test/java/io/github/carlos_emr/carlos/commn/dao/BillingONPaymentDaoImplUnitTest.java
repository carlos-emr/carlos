/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for {@code BillingONPaymentDaoImpl} payment query and aggregation semantics. */
@DisplayName("BillingONPaymentDaoImpl")
@Tag("unit")
@Tag("dao")
class BillingONPaymentDaoImplUnitTest {

    @Test
    void shouldNotInjectOtherDaos() {
        assertThat(Arrays.stream(BillingONPaymentDaoImpl.class.getDeclaredFields())
                .map(Field::getType))
                .doesNotContain(BillingONExtDao.class, BillingONCHeader1Dao.class);
    }

    @Test
    void shouldNotExposeCrossDaoSettersOrGetters() {
        assertThat(Arrays.stream(BillingONPaymentDaoImpl.class.getDeclaredMethods())
                .map(Method::getName))
                .doesNotContain(
                        "setBillingONExtDao",
                        "setBillingONCHeader1Dao",
                        "getBillingONExtDao",
                        "getBillingONCHeader1Dao");
    }
}
