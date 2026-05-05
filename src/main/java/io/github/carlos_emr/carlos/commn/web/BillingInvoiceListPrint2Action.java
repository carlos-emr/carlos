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
package io.github.carlos_emr.carlos.commn.web;

public class BillingInvoiceListPrint2Action extends BillingInvoice2Action {

    @Override
    public String execute() throws Exception {
        // Dedicated direct-response route: PDF downloads must not share JSP result dispatch.
        return getListPrintPDF();
    }
}
