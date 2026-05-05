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
package io.github.carlos_emr.carlos.commn.service;

import java.io.InputStream;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Billing invoice Jasper template")
@Tag("unit")
@Tag("billing")
class BillingInvoiceTemplateCompileTest {

    private static final String BILLING_INVOICE_TEMPLATE =
            "org/oscarehr/common/web/BillingInvoiceTemplate.jrxml";

    @Test
    void shouldCompileWithRuntimeJasperReportsVersion() throws Exception {
        try (InputStream template = getClass().getClassLoader().getResourceAsStream(BILLING_INVOICE_TEMPLATE)) {
            assertThat(template).isNotNull();

            JasperReport report = JasperCompileManager.compileReport(template);

            assertThat(report.getName()).isEqualTo("billingInvoice");
        }
    }
}
