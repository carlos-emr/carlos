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
package io.github.carlos_emr.carlos.admin.web;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

/**
 * Security gate and data loader for the Log Admin Report page.
 *
 * <p>Requires either {@code _admin} or {@code _admin.reporting} read privilege.
 * When the report form is submitted (POST with a non-null {@code submit}
 * parameter), this action loads providers and audit log entries through the
 * existing JPA/DAO layer, eliminating the deprecated direct JDBC usage that was
 * preserved during the original JSP migration. The request filtering still
 * addresses the SQL-injection vulnerabilities that existed in the original JSP:
 * <ul>
 *   <li>{@code providerNo} was concatenated directly into the query string.</li>
 *   <li>{@code content} was concatenated directly into the query string.</li>
 *   <li>{@code curUser_no} (used for site-access-privacy filtering) was
 *       concatenated directly into a sub-select.</li>
 * </ul>
 *
 * <p>The {@code content} parameter is sanitised via an allowlist: only the
 * literal values {@code "login"} and {@code "admin"} are passed through; all
 * other values default to the wildcard {@code "%"} so that the query matches
 * all content types.</p>
 *
 * <p>Request attributes set for the JSP:
 * <ul>
 *   <li>{@code vecProvider} – {@code Vector<Properties>} provider list for the
 *       dropdown (keys: {@code providerNo}, {@code name}).</li>
 *   <li>{@code propName} – {@code Properties} mapping providerNo → full name.</li>
 *   <li>{@code vec} – {@code Vector<Properties>} log rows (only on POST with
 *       submit); keys: {@code dateTime}, {@code action}, {@code content},
 *       {@code contentId}, {@code ip}, {@code provider_no},
 *       {@code demographic_no}, {@code data}.</li>
 *   <li>{@code bAll} – {@code Boolean} true when all providers were queried.</li>
 *   <li>{@code providerNo} – the queried provider number.</li>
 *   <li>{@code startDate} – start date string used for the query.</li>
 *   <li>{@code endDate} – end date string used for the query.</li>
 * </ul>
 *
 * @since 2026-04-05
 */
public class LogReport2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
    private OscarLogDao oscarLogDao = SpringUtils.getBean(OscarLogDao.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.reporting", "r", null)) {
            throw new SecurityException("missing required sec object (_admin or _admin.reporting)");
        }

        String curUser_no = (String) request.getSession().getAttribute("user");

        // Determine whether the current user is constrained to their own site's providers.
        boolean isSiteAccessPrivacy = securityInfoManager.hasPrivilege(
                loggedInInfo, "_site_access_privacy", "r", null);

        Properties propName = new Properties();
        Vector<Properties> vecProvider = new Vector<>();
        List<ProviderData> providers;

        try {
            if (isSiteAccessPrivacy) {
                providers = providerDataDao.findByProviderSite(curUser_no);
            } else {
                providers = providerDataDao.findAllOrderByLastName();
            }

            providers.sort(Comparator
                    .comparing(ProviderData::getFirstName, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(ProviderData::getLastName, Comparator.nullsFirst(Comparator.naturalOrder())));

            for (ProviderData provider : providers) {
                String pNo = StringUtils.defaultString(provider.getId());
                String fullName = (StringUtils.defaultString(provider.getFirstName()) + " " + StringUtils.defaultString(provider.getLastName())).trim();
                propName.setProperty(pNo, fullName);

                Properties prop = new Properties();
                prop.setProperty("providerNo", pNo);
                prop.setProperty("name", fullName);
                vecProvider.add(prop);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Failed to load provider list for log report", e);
            // Fail closed: if we cannot determine the allowed provider set for site-restricted users,
            // use an empty list so no cross-site log rows are returned.
            providers = List.of();
        }

        request.setAttribute("vecProvider", vecProvider);
        request.setAttribute("propName", propName);

        if (request.getParameter("submit") != null) {
            String providerNo = request.getParameter("providerNo");
            boolean bAll = "*".equals(providerNo);

            // Allowlist the content parameter — only known values pass through literally;
            // everything else becomes a wildcard.
            String contentParam = request.getParameter("content");
            String content;
            if ("login".equals(contentParam) || "admin".equals(contentParam)) {
                content = contentParam;
            } else {
                content = "%";
            }

            String sDate = request.getParameter("startDate");
            String eDate = request.getParameter("endDate");
            if (sDate == null || sDate.isEmpty()) sDate = "1900-01-01";
            if (eDate == null || eDate.isEmpty()) eDate = "2999-01-01";

            // Date params: getSysDateEX adds one day to make the end-date inclusive.
            java.sql.Date parsedStart = MyDateFormat.getSysDate(sDate);
            java.sql.Date parsedEnd = MyDateFormat.getSysDateEX(eDate, 1);
            if (parsedStart == null || parsedEnd == null) {
                request.setAttribute("dateError", "Invalid date format. Please use YYYY-MM-DD.");
                return SUCCESS;
            }
            // Build the allowed provider set for all site-restricted requests so both the
            // all-providers and specific-provider report paths enforce the same privacy boundary.
            List<String> siteRestrictedProviderNos = isSiteAccessPrivacy
                    ? providers.stream().map(ProviderData::getId).toList()
                    : null;

            if (isUnauthorizedSiteRestrictedProviderRequest(isSiteAccessPrivacy, bAll, providerNo, siteRestrictedProviderNos)) {
                request.setAttribute("vec", new Vector<Properties>());
                request.setAttribute("bAll", bAll);
                request.setAttribute("providerNo", providerNo);
                request.setAttribute("startDate", sDate);
                request.setAttribute("endDate", eDate);
                return SUCCESS;
            }

            Vector<Properties> vec = new Vector<>();
            try {
                List<OscarLog> logEntries = oscarLogDao.findForReport(
                        parsedStart,
                        parsedEnd,
                        content,
                        bAll ? null : providerNo,
                        siteRestrictedProviderNos);

                for (OscarLog logEntry : logEntries) {
                    Properties prop = new Properties();
                    prop.setProperty("dateTime", formatTimestamp(logEntry.getCreated()));
                    // Do not pre-encode these string fields — the view layer is responsible for output encoding.
                    // Pre-encoding here would cause double-encoding (e.g. "<" → "&amp;lt;").
                    prop.setProperty("action", StringUtils.defaultString(logEntry.getAction()));
                    prop.setProperty("content", StringUtils.defaultString(logEntry.getContent()));
                    prop.setProperty("contentId", StringUtils.defaultString(logEntry.getContentId()));
                    prop.setProperty("ip", StringUtils.defaultString(logEntry.getIp()));
                    prop.setProperty("provider_no", StringUtils.defaultString(logEntry.getProviderNo()));
                    prop.setProperty("demographic_no", logEntry.getDemographicId() == null ? "" : logEntry.getDemographicId().toString());
                    // For 'data' we inject <br/> line-break tags, so we must encode HTML-special chars
                    // first and then add the <br/> tags. The JSP outputs this field raw (not via <c:out>)
                    // to preserve the injected markup. The injected <br/> is constant application markup
                    // and does not include any user-controlled content.
                    prop.setProperty("data",
                            Encode.forHtml(StringUtils.defaultString(logEntry.getData())).replace("\n", "<br/>"));
                    vec.add(prop);
                }
            } catch (Exception e) {
                MiscUtils.getLogger().error("Failed to execute log report query", e);
            }

            request.setAttribute("vec", vec);
            request.setAttribute("bAll", bAll);
            request.setAttribute("providerNo", providerNo);
            request.setAttribute("startDate", sDate);
            request.setAttribute("endDate", eDate);
        }

        return SUCCESS;
    }

    private boolean isUnauthorizedSiteRestrictedProviderRequest(boolean isSiteAccessPrivacy, boolean bAll,
                                                                String providerNo, List<String> siteRestrictedProviderNos) {
        return isSiteAccessPrivacy && !bAll
                && (providerNo == null || siteRestrictedProviderNos == null || !siteRestrictedProviderNos.contains(providerNo));
    }

    private String formatTimestamp(java.util.Date value) {
        return value == null ? "" : new Timestamp(value.getTime()).toString();
    }
}
