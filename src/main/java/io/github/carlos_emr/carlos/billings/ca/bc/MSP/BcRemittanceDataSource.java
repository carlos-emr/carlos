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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.util.Iterator;
import java.util.List;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.RemittanceRow;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

/** Adapts detached remittance rows to the unchanged pdf_rep_msprem template field contract. */
public final class BcRemittanceDataSource implements JRDataSource {
    private final Iterator<RemittanceRow> rows;
    private RemittanceRow row;

    public BcRemittanceDataSource(List<RemittanceRow> rows) {
        this.rows = rows.iterator();
    }

    @Override
    public boolean next() {
        if (!rows.hasNext()) return false;
        row = rows.next();
        return true;
    }

    @Override
    public Object getFieldValue(JRField field) throws JRException {
        if (row == null) throw new JRException("No current remittance row");
        return switch (field.getName()) {
            case "billing_code" -> row.billing_code();
            case "first_name" -> row.first_name();
            case "last_name" -> row.last_name();
            case "t_practitionerno" -> row.t_practitionerno();
            case "t_s00type" -> row.t_s00type();
            case "t_servicedate" -> row.t_servicedate();
            case "t_payment" -> row.t_payment();
            case "t_datacenter" -> row.t_datacenter();
            case "demographic_name" -> row.demographic_name();
            case "demographic_no" -> row.demographic_no();
            case "t_paidamt" -> row.t_paidamt();
            case "t_exp1" -> row.t_exp1();
            case "t_exp2" -> row.t_exp2();
            case "t_exp3" -> row.t_exp3();
            case "t_exp4" -> row.t_exp4();
            case "t_exp5" -> row.t_exp5();
            case "t_exp6" -> row.t_exp6();
            case "t_dataseq" -> row.t_dataseq();
            default -> throw new JRException("Unknown remittance field");
        };
    }
}
