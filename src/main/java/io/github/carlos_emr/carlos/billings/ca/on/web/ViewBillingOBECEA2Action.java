package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.providers.gate.BaseProviderViewGate2Action;

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
