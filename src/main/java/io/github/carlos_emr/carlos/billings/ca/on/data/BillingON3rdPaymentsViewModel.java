/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for billing/CA/ON/billingON3rdPayments.jsp, the 3rd
 * party payment list popup. The legacy JSP scriptlet did all of:
 *
 * <ul>
 *   <li>Iterate the {@code itemDataList} and convert each entry's fee/paid/
 *       discount/credit strings into BigDecimal arithmetic for the per-item
 *       summary (paid sign, balance, balance sign)</li>
 *   <li>Iterate {@code paymentsList} to compute running totals, the per-row
 *       balance, and look up the payment type name via BillingPaymentTypeDao</li>
 *   <li>Format Total/Balance currency strings via NumberFormat</li>
 * </ul>
 *
 * <p>All of that runs in
 * {@link io.github.carlos_emr.carlos.billing.CA.ON.web.BillingONPayments2Action}
 * and is exposed as {@code paymentsViewModel} on the request.</p>
 *
 * @since 2026-04-25
 */
public final class BillingON3rdPaymentsViewModel {

    /** Per-item summary row in the ADD/EDIT PAYMENT block. */
    public record ItemSummary(
            String id,
            String serviceCode,
            String fee,
            String realPaidDisplay,   // pre-signed, currency-formatted
            String balanceDisplay) { } // pre-signed, currency-formatted

    /** One existing-payment row in the PAYMENTS LIST table. */
    public record PaymentRow(
            String id,
            String totalPayment,
            String paymentTypeName,
            String paymentDateFormatted,
            String totalDiscount,
            String totalCredit,
            String totalRefund,
            String balanceDisplay) { } // pre-signed, currency-formatted

    /** Today as yyyy-MM-dd, used as the default paymentDate input value. */
    private final String today;
    /** Current page's billingNo (echoed back to the form). */
    private final String billingNo;
    /** Total number of items in the per-item block (used as form size). */
    private final int itemCount;
    /** Per-item summary rows. */
    private final List<ItemSummary> items;
    /** Existing payment rows. */
    private final List<PaymentRow> payments;
    /** Pre-formatted "Total: $..." currency. */
    private final String totalDisplay;
    /** Pre-formatted "Balance: $..." with sign prefix when negative. */
    private final String balanceDisplay;
    /** Validation errors that the bottom of the page renders. */
    private final List<String> errors;

    private BillingON3rdPaymentsViewModel(Builder b) {
        this.today = b.today == null ? "" : b.today;
        this.billingNo = b.billingNo == null ? "" : b.billingNo;
        this.itemCount = b.itemCount;
        this.items = b.items == null ? Collections.emptyList() : List.copyOf(b.items);
        this.payments = b.payments == null ? Collections.emptyList() : List.copyOf(b.payments);
        this.totalDisplay = b.totalDisplay == null ? "" : b.totalDisplay;
        this.balanceDisplay = b.balanceDisplay == null ? "" : b.balanceDisplay;
        this.errors = b.errors == null ? Collections.emptyList() : List.copyOf(b.errors);
    }

    public static Builder builder() { return new Builder(); }

    public String getToday() { return today; }
    public String getBillingNo() { return billingNo; }
    public int getItemCount() { return itemCount; }
    public List<ItemSummary> getItems() { return items; }
    public List<PaymentRow> getPayments() { return payments; }
    public String getTotalDisplay() { return totalDisplay; }
    public String getBalanceDisplay() { return balanceDisplay; }
    public List<String> getErrors() { return errors; }

    public static final class Builder {
        private String today;
        private String billingNo;
        private int itemCount;
        private List<ItemSummary> items;
        private List<PaymentRow> payments;
        private String totalDisplay;
        private String balanceDisplay;
        private List<String> errors;

        public Builder today(String v) { this.today = v; return this; }
        public Builder billingNo(String v) { this.billingNo = v; return this; }
        public Builder itemCount(int v) { this.itemCount = v; return this; }
        public Builder items(List<ItemSummary> v) { this.items = v == null ? null : List.copyOf(v); return this; }
        public Builder payments(List<PaymentRow> v) { this.payments = v == null ? null : List.copyOf(v); return this; }
        public Builder totalDisplay(String v) { this.totalDisplay = v; return this; }
        public Builder balanceDisplay(String v) { this.balanceDisplay = v; return this; }
        public Builder errors(List<String> v) { this.errors = v == null ? null : List.copyOf(v); return this; }

        public BillingON3rdPaymentsViewModel build() { return new BillingON3rdPaymentsViewModel(this); }
    }
}
