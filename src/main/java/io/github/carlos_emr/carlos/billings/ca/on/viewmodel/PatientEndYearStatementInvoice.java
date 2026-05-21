/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * One invoice on a patient end-year statement. Immutable record so the JSP
 * cannot mutate the persisted aggregation through the model. The {@code
 * services} list and {@code invoiceDate} are defensively copied at
 * construction.
 */
public record PatientEndYearStatementInvoice(
        int invoiceNo,
        Date invoiceDate,
        String invoiced,
        String paid,
        List<PatientEndYearStatementServiceLine> services) {

    /** Defensive copy on the way in: {@code Date} is mutable, the list might
     *  be the live ArrayList the assembler built. */
    public PatientEndYearStatementInvoice {
        invoiceDate = invoiceDate == null ? null : new Date(invoiceDate.getTime());
        services = services == null ? Collections.emptyList() : List.copyOf(services);
    }

    public PatientEndYearStatementInvoice(int invoiceNo, Date invoiceDate, String invoiced, String paid) {
        this(invoiceNo, invoiceDate, invoiced, paid, Collections.emptyList());
    }

    /** Return a new instance with {@code services} replaced — preserves
     *  immutability while letting the assembler build the list after the
     *  invoice header is ready. */
    public PatientEndYearStatementInvoice withServices(List<PatientEndYearStatementServiceLine> newServices) {
        return new PatientEndYearStatementInvoice(invoiceNo, invoiceDate, invoiced, paid, newServices);
    }

    /** Defensive copy on the way out — same rationale as the compact ctor. */
    @Override
    public Date invoiceDate() {
        return invoiceDate == null ? null : new Date(invoiceDate.getTime());
    }

    // ---- legacy getters for JSP EL compatibility ------------------------
    // EL also resolves the record's auto-accessors (e.g. ${row.invoiceNo})
    // but explicit getters keep parity with the pre-record API for any
    // direct Java callers that expected the JavaBean shape.

    public int getInvoiceNo() { return invoiceNo; }
    public Date getInvoiceDate() { return invoiceDate(); }
    public String getInvoiced() { return invoiced; }
    public String getPaid() { return paid; }
    public List<PatientEndYearStatementServiceLine> getServices() { return services; }
}
