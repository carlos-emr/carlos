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

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the round-7 M1 contract on
 * {@link BillingCorrectionRecordService}'s private {@code getFee}: when
 * the upstream {@code claimQueryService.getCodeFeeResult} returns no
 * value or marks the lookup partial, {@code getFee} must throw a typed
 * exception with service-code/date context instead of surfacing an
 * opaque money-parse failure.
 *
 * <p>Reflection-driven so the test pins the private contract directly.
 * The public callers ({@code addItem}, {@code updateBillingClaimHeader})
 * each pull in 10+ DAO collaborators — far more setup than this contract
 * warrants.</p>
 */
@DisplayName("BillingCorrectionRecordService.getFee null-fee guard")
@Tag("unit")
@Tag("billing")
class BillingCorrectionRecordServiceGetFeeUnitTest {

    private BillingOnClaimLoader claimQueryService;
    private BillingCorrectionRecordService service;
    private Method getFee;

    @BeforeEach
    void setUp() throws Exception {
        BillingOnCorrectionPersister persister = mock(BillingOnCorrectionPersister.class);
        BillingONCHeader1Dao header1Dao = mock(BillingONCHeader1Dao.class);
        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        BillingONExtDao extDao = mock(BillingONExtDao.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);
        BillingThirdPartyService thirdPartyService = mock(BillingThirdPartyService.class);
        ServiceCodeLoader serviceCodeLoader = mock(ServiceCodeLoader.class);
        BillingOnClaimPersister claimPersister = mock(BillingOnClaimPersister.class);
        claimQueryService = mock(BillingOnClaimLoader.class);
        BillingOnTransactionDao transDao = mock(BillingOnTransactionDao.class);
        BillingOnItemPaymentDao itemPaymentDao = mock(BillingOnItemPaymentDao.class);

        java.lang.reflect.Constructor<BillingCorrectionRecordService> ctor =
                BillingCorrectionRecordService.class.getDeclaredConstructor(
                        BillingOnCorrectionPersister.class, BillingONCHeader1Dao.class,
                        BillingONItemDao.class, BillingONExtDao.class,
                        BillingOnLookupService.class, BillingThirdPartyService.class,
                        ServiceCodeLoader.class, BillingOnClaimPersister.class,
                        BillingOnClaimLoader.class, BillingOnTransactionDao.class,
                        BillingOnItemPaymentDao.class);
        ctor.setAccessible(true);
        service = ctor.newInstance(persister, header1Dao, itemDao, extDao,
                lookupService, thirdPartyService, serviceCodeLoader, claimPersister,
                claimQueryService, transDao, itemPaymentDao);

        getFee = BillingCorrectionRecordService.class.getDeclaredMethod(
                "getFee", String.class, String.class, String.class, String.class);
        getFee.setAccessible(true);
    }

    @Test
    void shouldThrowBillingValidationException_whenCodeFeeLookupReturnsNoValue() throws Exception {
        when(claimQueryService.getCodeFeeResult(anyString(), anyString()))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.found(null));

        assertThatThrownBy(() -> invokeGetFee("", "1", "A007A", "2026-04-29"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("A007A")
                .hasMessageContaining("2026-04-29")
                .hasMessageContaining("fee table configuration");
    }

    @Test
    void shouldThrowBillingDataLoadException_whenCodeFeeLookupIsPartial() throws Exception {
        when(claimQueryService.getCodeFeeResult(anyString(), anyString()))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.partial("lookup failed"));

        assertThatThrownBy(() -> invokeGetFee("", "1", "A007A", "2026-04-29"))
                .isInstanceOf(BillingDataLoadException.class)
                .hasMessageContaining("Fee lookup failed")
                .hasMessageContaining("correcting billing item");
    }

    @Test
    void shouldReturnFeeUnchanged_whenIncomingFeeAlreadyComputed() throws Exception {
        // The null-check only runs in the empty-fee branch; if the caller
        // already computed a fee, getFee is a pass-through.
        Object result = invokeGetFee("12.34", "1", "A007A", "2026-04-29");
        assertThat(result).isEqualTo("12.34");
    }

    @Test
    void shouldComputeFee_whenIncomingFeeIsBlankAndCodeFeeResolves() throws Exception {
        // Happy path: blank fee + lookup resolves → unit-multiplied result.
        when(claimQueryService.getCodeFeeResult(any(), any()))
                .thenReturn(BillingOnClaimLoader.FeeLookupResult.found("10.00"));

        Object result = invokeGetFee("", "2", "A007A", "2026-04-29");
        assertThat(result).isEqualTo("20.00");
    }

    private Object invokeGetFee(String fee, String unit, String code, String date) throws Exception {
        try {
            return getFee.invoke(service, fee, unit, code, date);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
