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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.text.WordUtils;

import io.github.carlos_emr.carlos.billing.CA.ON.model.Billing3rdPartyAddress;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnThirdPartyBillingAddressSearchViewModel;
import io.github.carlos_emr.carlos.commn.dao.Billing3rdPartyAddressDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * View gate for {@code billing/CA/ON/onSearch3rdBillAddr.jsp}. Enforces
 * {@code _billing r} privilege before forwarding to the JSP at its
 * {@code /WEB-INF/jsp/} location, AND assembles the {@link
 * OnThirdPartyBillingAddressSearchViewModel} the JSP renders so the JSP body is pure
 * EL/JSTL.
 *
 * @since 2026-04-13
 */
public class ViewOnThirdPartyBillingAddressSearch2Action extends ActionSupport {

    /** Allowlist replicated from the legacy JSP — keep in sync if extended. */
    private static final Set<String> VALID_ORDER_BY_COLUMNS = Set.of(
            "company_name", "attention", "address", "city", "province",
            "postcode", "telephone", "fax", "id");
    private static final Set<String> VALID_SEARCH_MODES = Set.of(
            "search_name", "company_name", "attention", "address", "city",
            "province", "postcode", "telephone", "fax");

    private final SecurityInfoManager securityInfoManager;
    private final Billing3rdPartyAddressDao billing3rdPartyAddressDao;

    public ViewOnThirdPartyBillingAddressSearch2Action(SecurityInfoManager securityInfoManager,
                                          Billing3rdPartyAddressDao billing3rdPartyAddressDao) {
        this.securityInfoManager = securityInfoManager;
        this.billing3rdPartyAddressDao = billing3rdPartyAddressDao;
    }
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        request.setAttribute("searchAddrModel", assembleViewModel(request));

        return SUCCESS;
    }

    /**
     * Assembles the view model. Public so the defensive JSP fallback can
     * invoke it directly when the action chain wasn't traversed (callers
     * remain responsible for the privilege check before invoking).
     *
     * @param request the live servlet request — search parameters read from this
     * @return populated view model (never null)
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public OnThirdPartyBillingAddressSearchViewModel assembleViewModel(HttpServletRequest request) {
        String limit1Param = request.getParameter("limit1");
        String limit2Param = request.getParameter("limit2");
        String strLimit1 = limit1Param == null ? "1" : limit1Param;
        String strLimit2 = limit2Param == null ? "25" : limit2Param;
        String paramVal = nullToEmpty(request.getParameter("param"));
        String param2Val = nullToEmpty(request.getParameter("param2"));
        String keyword = request.getParameter("keyword");
        String submit = request.getParameter("submit");
        String searchModeParam = request.getParameter("search_mode");
        String orderByParam = request.getParameter("orderby");

        List<OnThirdPartyBillingAddressSearchViewModel.AddressEntry> addresses = new ArrayList<>();
        boolean searched = "Search".equals(submit) || "Next Page".equals(submit) || "Last Page".equals(submit);
        if (searched) {
            String orderBy = (orderByParam == null || !VALID_ORDER_BY_COLUMNS.contains(orderByParam))
                    ? "company_name" : orderByParam;
            String searchMode = (searchModeParam == null || !VALID_SEARCH_MODES.contains(searchModeParam))
                    ? "search_name" : searchModeParam;

            // searchMode and orderBy validated against allowlists above to
            // satisfy the deepcode SqlInjection check the legacy JSP marked.
            for (Billing3rdPartyAddress ba : billing3rdPartyAddressDao.findAddresses(searchMode, orderBy, keyword, strLimit1, strLimit2)) {
                Properties prop = new Properties();
                prop.setProperty("id", String.valueOf(ba.getId()));
                prop.setProperty("attention", nullToEmpty(ba.getAttention()));
                prop.setProperty("company_name", nullToEmpty(ba.getCompanyName()));
                prop.setProperty("address", nullToEmpty(ba.getAddress()));
                prop.setProperty("city", nullToEmpty(ba.getCity()));
                prop.setProperty("province", nullToEmpty(ba.getProvince()));
                prop.setProperty("postcode", nullToEmpty(ba.getPostalCode()));
                prop.setProperty("telephone", nullToEmpty(ba.getTelephone()));
                prop.setProperty("fax", nullToEmpty(ba.getFax()));

                String onClick;
                if (paramVal.length() > 0) {
                    onClick = "typeInData1('"
                            + SafeEncode.forJavaScript(prop.getProperty("attention", "").isEmpty() ? "" : (prop.getProperty("attention") + "\n"))
                            + SafeEncode.forJavaScript(prop.getProperty("company_name", "").isEmpty() ? "" : (prop.getProperty("company_name") + "\n"))
                            + SafeEncode.forJavaScript(prop.getProperty("address", "").isEmpty() ? "" : (prop.getProperty("address") + "\n"))
                            + SafeEncode.forJavaScript(prop.getProperty("city", "").isEmpty() ? "" : (prop.getProperty("city") + " "))
                            + SafeEncode.forJavaScript(prop.getProperty("province", "").isEmpty() ? "" : (prop.getProperty("province") + " "))
                            + SafeEncode.forJavaScript(prop.getProperty("postcode", "").isEmpty() ? "" : (prop.getProperty("postcode") + "\n"))
                            + SafeEncode.forJavaScript(prop.getProperty("telephone", "").isEmpty() ? "" : (prop.getProperty("telephone") + "\n"))
                            + SafeEncode.forJavaScript(prop.getProperty("fax", "").isEmpty() ? "" : (prop.getProperty("fax") + "\n"))
                            + "')";
                } else {
                    onClick = "typeInData1('"
                            + SafeEncode.forJavaScript(prop.getProperty("city", ""))
                            + "')";
                }

                addresses.add(new OnThirdPartyBillingAddressSearchViewModel.AddressEntry(
                        prop.getProperty("id"),
                        prop.getProperty("attention"),
                        prop.getProperty("company_name"),
                        WordUtils.capitalize(prop.getProperty("company_name", "").toLowerCase()),
                        WordUtils.capitalize(prop.getProperty("address", "").toLowerCase()),
                        prop.getProperty("city"),
                        prop.getProperty("province"),
                        prop.getProperty("postcode"),
                        prop.getProperty("telephone"),
                        prop.getProperty("fax"),
                        onClick));
            }
        }

        int nItems = addresses.size();
        int nLastPage = parseIntOr(strLimit1, 1) - parseIntOr(strLimit2, 25);
        int nNextPage = parseIntOr(strLimit2, 25) + parseIntOr(strLimit1, 1);
        boolean showNoResults = nItems == 0 && nLastPage <= 0;
        boolean showPrevPage = nLastPage >= 0;
        boolean showNextPage = nItems == parseIntOr(strLimit2, 25);

        return OnThirdPartyBillingAddressSearchViewModel.builder()
                .param(paramVal)
                .param2(param2Val)
                .keyword(keyword == null ? "" : keyword)
                .searchMode(searchModeParam == null ? "" : searchModeParam)
                .orderBy(orderByParam == null ? "" : orderByParam)
                .limit1(strLimit1)
                .limit2(strLimit2)
                .addresses(addresses)
                .showNoResults(showNoResults)
                .showPrevPage(showPrevPage)
                .showNextPage(showNextPage)
                .nextPageLimit1(nNextPage)
                .prevPageLimit1(nLastPage)
                .build();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static int parseIntOr(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
