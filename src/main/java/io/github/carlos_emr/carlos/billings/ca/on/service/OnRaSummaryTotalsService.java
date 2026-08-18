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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaSummaryViewModel;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;

/** Persists recalculated RA summary totals outside the read-only assembler. */
@Service
public class OnRaSummaryTotalsService {

    private final RaHeaderDao raHeaderDao;

    public OnRaSummaryTotalsService(RaHeaderDao raHeaderDao) {
        this.raHeaderDao = raHeaderDao;
    }

    @Transactional
    public void mergeTotals(OnRaSummaryViewModel model) {
        if (model == null) {
            return;
        }
        // Refuse to overwrite RaHeader.content when the upstream RA summary
        // had unreadable rows excluded from the running totals — persisting
        // would silently understate the operator's reconciliation snapshot.
        // The view still renders fine; only the persist is blocked.
        if (model.isPartial()) {
            throw new BillingValidationException(
                    "RA summary merge rejected: " + model.getUnreadableRowCount()
                            + " row(s) had unparseable amountPay; reconcile the source data and retry");
        }
        mergeTotals(model.getRaNo(), model.getLocalTotal(), model.getPayTotal(),
                model.getOtherTotal(), model.getObTotal(), model.getCoTotal());
    }

    @Transactional
    public void mergeTotals(String raNoStr, BigDecimal localTotal, BigDecimal payTotal,
                            BigDecimal otherTotal, BigDecimal obTotal, BigDecimal coTotal) {
        // Surface a parse / null-input failure so the action's exception
        // mapping renders the validation page. Silently returning would
        // leave the RA header content untouched while the caller believed
        // the merge succeeded, drifting the grand-total grid out of sync.
        if (raNoStr == null || raNoStr.isEmpty()) {
            throw new BillingValidationException(
                    "RA summary merge rejected: raNo is missing");
        }
        int raNo;
        try {
            raNo = Integer.parseInt(raNoStr.trim());
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "RA summary merge rejected: raNo [" + raNoStr
                            + "] is not a valid integer", e);
        }
        RaHeader header = raHeaderDao.find(raNo);
        if (header == null) {
            // Same rationale as above — better to fail visibly than to let
            // the caller present "Saved" for a phantom raNo.
            throw new BillingValidationException(
                    "RA summary merge rejected: no RaHeader for raNo [" + raNo + "]");
        }

        String content = header.getContent();
        String transaction = SxmlMisc.getXmlContent(content,
                "<xml_transaction>", "</xml_transaction>");
        String balanceFwd = SxmlMisc.getXmlContent(content,
                "<xml_balancefwd>", "</xml_balancefwd>");

        StringBuilder rebuilt = new StringBuilder();
        appendPreservedRaFragment(rebuilt, "xml_transaction", transaction);
        appendPreservedRaFragment(rebuilt, "xml_balancefwd", balanceFwd);
        rebuilt.append("<xml_local>").append(nullToZero(localTotal)).append("</xml_local>");
        rebuilt.append("<xml_total>").append(nullToZero(payTotal)).append("</xml_total>");
        rebuilt.append("<xml_other_total>").append(nullToZero(otherTotal)).append("</xml_other_total>");
        rebuilt.append("<xml_ob_total>").append(nullToZero(obTotal)).append("</xml_ob_total>");
        rebuilt.append("<xml_co_total>").append(nullToZero(coTotal)).append("</xml_co_total>");

        header.setContent(rebuilt.toString());
        raHeaderDao.merge(header);
    }

    // FindSecBugs POTENTIAL_XML_INJECTION: RA transaction/balance fragments are application-generated XML/HTML tables extracted from existing RA headers and preserved verbatim.
    @SuppressFBWarnings(value = "POTENTIAL_XML_INJECTION", justification = "RA transaction/balance fragments are application-generated XML/HTML tables extracted from the existing RA header and must be preserved verbatim")
    private static void appendPreservedRaFragment(StringBuilder rebuilt, String elementName, String fragment) {
        rebuilt.append("<").append(elementName).append(">").append(nullSafe(fragment)).append("</").append(elementName).append(">");
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
