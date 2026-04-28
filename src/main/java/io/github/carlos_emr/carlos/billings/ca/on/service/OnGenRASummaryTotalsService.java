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

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.data.OnGenRASummaryViewModel;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;

/** Persists recalculated RA summary totals outside the read-only assembler. */
@Service
public class OnGenRASummaryTotalsService {

    private final RaHeaderDao raHeaderDao;

    public OnGenRASummaryTotalsService(RaHeaderDao raHeaderDao) {
        this.raHeaderDao = raHeaderDao;
    }

    @Transactional
    public void mergeTotals(OnGenRASummaryViewModel model) {
        if (model == null) {
            return;
        }
        mergeTotals(model.getRaNo(), model.getLocalTotal(), model.getPayTotal(),
                model.getOtherTotal(), model.getObTotal(), model.getCoTotal());
    }

    @Transactional
    public void mergeTotals(String raNoStr, BigDecimal localTotal, BigDecimal payTotal,
                            BigDecimal otherTotal, BigDecimal obTotal, BigDecimal coTotal) {
        int raNo;
        try {
            raNo = Integer.parseInt(raNoStr);
        } catch (NumberFormatException e) {
            return;
        }
        RaHeader header = raHeaderDao.find(raNo);
        if (header == null) {
            return;
        }

        String content = header.getContent();
        String transaction = SxmlMisc.getXmlContent(content,
                "<xml_transaction>", "</xml_transaction>");
        String balanceFwd = SxmlMisc.getXmlContent(content,
                "<xml_balancefwd>", "</xml_balancefwd>");

        StringBuilder rebuilt = new StringBuilder();
        rebuilt.append("<xml_transaction>").append(nullSafe(transaction)).append("</xml_transaction>");
        rebuilt.append("<xml_balancefwd>").append(nullSafe(balanceFwd)).append("</xml_balancefwd>");
        rebuilt.append("<xml_local>").append(nullToZero(localTotal)).append("</xml_local>");
        rebuilt.append("<xml_total>").append(nullToZero(payTotal)).append("</xml_total>");
        rebuilt.append("<xml_other_total>").append(nullToZero(otherTotal)).append("</xml_other_total>");
        rebuilt.append("<xml_ob_total>").append(nullToZero(obTotal)).append("</xml_ob_total>");
        rebuilt.append("<xml_co_total>").append(nullToZero(coTotal)).append("</xml_co_total>");

        header.setContent(rebuilt.toString());
        raHeaderDao.merge(header);
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
