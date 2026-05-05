package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.providers.gate.BaseProviderViewGate2Action;

/**
 * Action for viewing the Billing OBECEA report for Ontario.
 * This action validates the required administrative billing privileges before displaying the report.
 *
 * @since 2026-03-25
 */
public class ViewBillingOBECEA2Action extends BaseProviderViewGate2Action {
    @Override
    protected String getSecurityObject() {
        return "_admin.billing";
    }

    @Override
    protected String getAccessRight() {
        return "w";
    }
}
