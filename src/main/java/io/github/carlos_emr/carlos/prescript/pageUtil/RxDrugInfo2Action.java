/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Displays configured DrugRef information without redirecting medication names to another site. */
public final class RxDrugInfo2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_rx", "r", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        String genericName = selector(request.getParameter("GN"));
        String din = selector(request.getParameter("DIN"));
        // Existing chooser links supply a DrugRef primary key under the legacy BN parameter.
        String productId = selector(request.getParameter("BN"));
        List<RxDrugData.MinDrug> matches = new ArrayList<>();
        request.setAttribute("drugInfoMatches", matches);
        request.setAttribute("drugInfoUnavailable", false);
        request.removeAttribute("drugInfoMonograph");
        request.setAttribute("drugInfoSearchTerm", genericName);
        request.setAttribute("drugInfoRequested", genericName != null || productId != null || din != null);
        if (genericName == null && productId == null && din == null) {
            return SUCCESS;
        }

        try {
            RxDrugData data = new RxDrugData();
            // Prefer the exact product identifier. Saved GN descriptions can include strength
            // and form, so treating them as catalogue search terms can miss the prescribed drug.
            if (din != null || productId != null) {
                RxDrugData.DrugMonograph monograph = din != null
                        ? data.getDrugByDIN(din) : data.getDrug2(productId);
                if (monograph != null && (StringUtils.isNotBlank(monograph.getName())
                        || StringUtils.isNotBlank(monograph.getProduct()))) {
                    request.setAttribute("drugInfoMonograph", monograph);
                    if (genericName == null) {
                        request.setAttribute("drugInfoSearchTerm",
                                StringUtils.defaultIfBlank(monograph.getName(), monograph.getProduct()));
                    }
                }
            } else {
                RxDrugData.DrugSearch search = data.listDrug2(genericName);
                if (search == null || search.failed) {
                    request.setAttribute("drugInfoUnavailable", true);
                } else {
                    addMatches(matches, search.getGen());
                    addMatches(matches, search.getBrand());
                }
            }
        } catch (Exception exception) {
            MiscUtils.getLogger().warn("DrugRef information lookup failed; failureType={}",
                    exception.getClass().getSimpleName());
            request.setAttribute("drugInfoUnavailable", true);
        }
        return SUCCESS;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive match of the literal "null" a legacy selector may post; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of the literal \"null\" a legacy selector may post; not a security or authorization decision")
    private static String selector(String value) {
        String normalized = StringUtils.trimToNull(value);
        return "null".equalsIgnoreCase(normalized) ? null : normalized;
    }

    private static void addMatches(List<RxDrugData.MinDrug> matches, Iterable<?> entries) {
        if (entries == null) {
            return;
        }
        for (Object entry : entries) {
            if (entry instanceof RxDrugData.MinDrug drug
                    && StringUtils.isNotBlank(drug.getpKey()) && StringUtils.isNotBlank(drug.getName())) {
                matches.add(drug);
            }
        }
    }
}
