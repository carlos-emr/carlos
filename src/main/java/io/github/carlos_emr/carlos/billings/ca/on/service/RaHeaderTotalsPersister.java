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

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaSummaryViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Write access for RA header totals/content and lazy RA premium population.
 */
@Service
@Transactional
public class RaHeaderTotalsPersister {

    private final RaHeaderDao raHeaderDao;
    private final BillingONPremiumDao billingONPremiumDao;
    private final RaDescriptionFileParser raDescriptionFileParser;

    public RaHeaderTotalsPersister(RaHeaderDao raHeaderDao,
                                   BillingONPremiumDao billingONPremiumDao,
                                   RaDescriptionFileParser raDescriptionFileParser) {
        this.raHeaderDao = raHeaderDao;
        this.billingONPremiumDao = billingONPremiumDao;
        this.raDescriptionFileParser = raDescriptionFileParser;
    }

    public void refreshDescriptionHeaderAndPremiums(LoggedInInfo loggedInInfo, Integer raNo, Locale locale) {
        if (raNo == null) {
            return;
        }
        RaHeader rh = raHeaderDao.find(raNo);
        if (rh == null || "D".equals(rh.getStatus())) {
            return;
        }

        RaDescriptionFileParser.ParsedFile parsed = raDescriptionFileParser.parse(rh.getFilename());
        if (parsed.isCompleteForHeaderMerge()) {
            String mergedContent = raDescriptionFileParser.mergedContent(parsed, rh.getContent());
            for (RaHeader r : raHeaderDao.findByFilenamePaymentDate(rh.getFilename(), parsed.paymentDate())) {
                r.setTotalAmount(parsed.cheque());
                r.setRecords(String.valueOf(parsed.recordCount()));
                r.setClaims(String.valueOf(parsed.claimCount()));
                r.setContent(mergedContent);
                raHeaderDao.merge(r);
            }
        } else {
            MiscUtils.getLogger().warn(
                    "Skipping RaHeader merge for raNo={} — parse incomplete (fileReadComplete={}, h1Parsed={})",
                    raNo, parsed.fileReadComplete(), parsed.h1Parsed());
        }

        if (loggedInInfo != null) {
            List<BillingONPremium> existing = billingONPremiumDao.getRAPremiumsByRaHeaderNo(raNo);
            if (existing.isEmpty()) {
                billingONPremiumDao.parseAndSaveRAPremiums(loggedInInfo, raNo, locale);
            }
        }
    }

    public void mergeSummaryTotals(GenerateRaSummaryViewModel model) {
        if (model == null) {
            return;
        }
        Integer raNo = parseInt(model.getRaNo());
        if (raNo == null) {
            return;
        }
        RaHeader rh = raHeaderDao.find(raNo);
        if (rh == null) {
            return;
        }
        String existing = nullToEmpty(rh.getContent());
        String transaction = nullToEmpty(SxmlMisc.getXmlContent(existing,
                "<xml_transaction>", "</xml_transaction>"));
        String balanceFwd = nullToEmpty(SxmlMisc.getXmlContent(existing,
                "<xml_balancefwd>", "</xml_balancefwd>"));

        String content = "<xml_transaction>" + transaction + "</xml_transaction>"
                + "<xml_balancefwd>" + balanceFwd + "</xml_balancefwd>"
                + "<xml_local>" + model.getRaHeaderLocalTotal() + "</xml_local>"
                + "<xml_total>" + model.getPaidTotal() + "</xml_total>"
                + "<xml_other_total>" + model.getOtherPayTotal() + "</xml_other_total>"
                + "<xml_ob_total>" + model.getObTotal() + "</xml_ob_total>"
                + "<xml_co_total>" + model.getCoTotal() + "</xml_co_total>";
        rh.setContent(content);
        raHeaderDao.merge(rh);
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
