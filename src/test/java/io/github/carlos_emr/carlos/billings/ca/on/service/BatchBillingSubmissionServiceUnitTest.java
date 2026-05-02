/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code BatchBillingSubmissionService} batch-submit orchestration and validation paths. */
@DisplayName("BatchBillingSubmissionService")
@Tag("unit")
@Tag("billing")
class BatchBillingSubmissionServiceUnitTest {

    private BillingOnHeaderCreationService headerCreationService;
    private BatchBillingDAO batchBillingDAO;
    private BatchBillingSubmissionService service;
    private Date billingDate;

    @BeforeEach
    void setUp() {
        headerCreationService = mock(BillingOnHeaderCreationService.class);
        batchBillingDAO = mock(BatchBillingDAO.class);
        service = new BatchBillingSubmissionService(headerCreationService, batchBillingDAO);
        billingDate = new Date(1_777_430_400_000L);
    }

    @Test
    void shouldSubmitEveryRowAndUpdateBatchBillingAmounts() {
        BatchBilling first = new BatchBilling();
        first.setId(1);
        BatchBilling second = new BatchBilling();
        second.setId(2);
        when(headerCreationService.createBill("999998", 42, "A007A", "250", "clinic-a", billingDate, "999998"))
                .thenReturn("12.34");
        when(headerCreationService.createBill("999999", 43, "K005A", "401", "clinic-a", billingDate, "999998"))
                .thenReturn("56.78");
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of(first));
        when(batchBillingDAO.find(43, "K005A")).thenReturn(List.of(second));

        BatchBillingSubmissionService.SubmitResult result = service.submitAll(List.of(
                new BatchBillingSubmissionService.Row("A007A", "250", 42, "999998"),
                new BatchBillingSubmissionService.Row("K005A", "401", 43, "999999")),
                "clinic-a", billingDate, "999998");

        assertThat(result.submittedCount()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        assertThat(first.getBillingAmount()).isEqualTo("12.34");
        assertThat(first.getLastBilledDate()).isEqualTo(billingDate);
        assertThat(second.getBillingAmount()).isEqualTo("56.78");
        assertThat(second.getLastBilledDate()).isEqualTo(billingDate);
        verify(batchBillingDAO).merge(first);
        verify(batchBillingDAO).merge(second);
    }

    @Test
    void shouldPropagateFailureAndStopProcessingLaterRows() {
        BatchBilling first = new BatchBilling();
        first.setId(1);
        when(headerCreationService.createBill("999998", 42, "A007A", "250", "clinic-a", billingDate, "999998"))
                .thenReturn("12.34");
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of(first));
        when(headerCreationService.createBill("999999", 43, "K005A", "401", "clinic-a", billingDate, "999998"))
                .thenThrow(new IllegalStateException("simulated create failure"));

        assertThatThrownBy(() -> service.submitAll(List.of(
                new BatchBillingSubmissionService.Row("A007A", "250", 42, "999998"),
                new BatchBillingSubmissionService.Row("K005A", "401", 43, "999999"),
                new BatchBillingSubmissionService.Row("G489A", "272", 44, "999997")),
                "clinic-a", billingDate, "999998"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated create failure");

        verify(batchBillingDAO).merge(first);
        verify(batchBillingDAO, never()).find(43, "K005A");
        verify(headerCreationService, never())
                .createBill(eq("999997"), eq(44), eq("G489A"), eq("272"), any(), any(), any());
    }

    @Test
    void shouldThrowBillingValidationException_whenBatchRowDisappearsAfterValidation() {
        when(headerCreationService.createBill("999998", 42, "A007A", "250", "clinic-a", billingDate, "999998"))
                .thenReturn("12.34");
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitAll(List.of(
                new BatchBillingSubmissionService.Row("A007A", "250", 42, "999998")),
                "clinic-a", billingDate, "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("42")
                .hasMessageContaining("A007A");

        verify(batchBillingDAO, never()).merge(any());
    }

    @Test
    void shouldRejectMissingCurrentUserBeforeAnyWrites() {
        assertThatThrownBy(() -> service.submitAll(
                List.of(new BatchBillingSubmissionService.Row("A007A", "250", 42, "999998")),
                "clinic-a", billingDate, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("current user");

        verifyNoInteractions(headerCreationService, batchBillingDAO);
    }

    @Test
    void shouldReturnRowFailureAndNotMarkAnyRowsBilled_whenDemographicIsUnbillable() {
        BatchBilling first = new BatchBilling();
        first.setId(1);
        when(batchBillingDAO.find(42, "A007A")).thenReturn(List.of(first));
        org.mockito.Mockito.doNothing()
                .when(headerCreationService).validateBillableDemographic(42);
        org.mockito.Mockito.doThrow(new BillingValidationException("Missing demographic for demographicNo=43"))
                .when(headerCreationService).validateBillableDemographic(43);

        BatchBillingSubmissionService.SubmitResult result = service.submitAll(List.of(
                new BatchBillingSubmissionService.Row("A007A", "250", 42, "999998"),
                new BatchBillingSubmissionService.Row("K005A", "401", 43, "999999")),
                "clinic-a", billingDate, "999998");

        assertThat(result.submittedCount()).isZero();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).rowIndex()).isEqualTo(1);
        assertThat(result.failures().get(0).message()).contains("demographicNo=43");
        verify(headerCreationService, never()).createBill(any(), any(), any(), any(), any(), any(), any());
        verify(batchBillingDAO, never()).merge(any());
        assertThat(first.getLastBilledDate()).isNull();
    }
}
