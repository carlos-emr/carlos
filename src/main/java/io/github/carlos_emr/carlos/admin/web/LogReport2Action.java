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

import java.sql.ResultSet;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.Misc;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.db.DBPreparedHandler;
import io.github.carlos_emr.carlos.db.DBPreparedHandlerParam;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

/**
 * Security gate and data loader for the Log Admin Report page.
 *
 * <p>Requires either {@code _admin} or {@code _admin.reporting} read privilege.
 * When the report form is submitted (POST with a non-null {@code submit}
 * parameter), this action executes fully-parameterized SQL queries against the
 * {@code log} table, eliminating the SQL-injection vulnerabilities that existed
 * in the original JSP:
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
 * @throws SecurityException if the logged-in user lacks the required admin privilege
 */
public class LogReport2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

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

        // Build the provider list for the dropdown using parameterized queries.
        DBPreparedHandler dbObj = new DBPreparedHandler();
        Properties propName = new Properties();
        Vector<Properties> vecProvider = new Vector<>();

        ResultSet rs;
        if (isSiteAccessPrivacy) {
            // Filter providers to those sharing at least one site with the current user.
            String sql = "select p.* from provider p"
                    + " INNER JOIN providersite s ON p.provider_no = s.provider_no"
                    + " WHERE s.site_id IN"
                    + " (SELECT site_id FROM providersite WHERE provider_no = ?)"
                    + " order by p.first_name, p.last_name";
            DBPreparedHandlerParam[] providerParams = {new DBPreparedHandlerParam(curUser_no)};
            rs = dbObj.queryResults(sql, providerParams);
        } else {
            rs = dbObj.queryResults(
                    "select * from provider p order by p.first_name, p.last_name",
                    new DBPreparedHandlerParam[0]);
        }

        try {
            while (rs.next()) {
                String pNo = Misc.getString(rs, "provider_no");
                String fullName = Misc.getString(rs, "first_name") + " " + Misc.getString(rs, "last_name");
                propName.setProperty(pNo, fullName);

                Properties prop = new Properties();
                prop.setProperty("providerNo", pNo);
                prop.setProperty("name", fullName);
                vecProvider.add(prop);
            }
        } finally {
            rs.close();
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
            DBPreparedHandlerParam endDateParam =
                    new DBPreparedHandlerParam(MyDateFormat.getSysDateEX(eDate, 1));
            DBPreparedHandlerParam startDateParam =
                    new DBPreparedHandlerParam(MyDateFormat.getSysDate(sDate));

            String sql;
            DBPreparedHandlerParam[] params;

            if (bAll) {
                if (isSiteAccessPrivacy) {
                    // All providers, but constrained to the current user's sites.
                    sql = "select * from log force index (datetime)"
                            + " where dateTime <= ? and dateTime >= ? and content like ?"
                            + " and provider_no IN"
                            + " (SELECT provider_no FROM providersite WHERE site_id IN"
                            + " (SELECT site_id FROM providersite WHERE provider_no = ?))"
                            + " order by dateTime desc";
                    params = new DBPreparedHandlerParam[]{
                            endDateParam,
                            startDateParam,
                            new DBPreparedHandlerParam(content),
                            new DBPreparedHandlerParam(curUser_no)
                    };
                } else {
                    sql = "select * from log force index (datetime)"
                            + " where dateTime <= ? and dateTime >= ? and content like ?"
                            + " order by dateTime desc";
                    params = new DBPreparedHandlerParam[]{
                            endDateParam,
                            startDateParam,
                            new DBPreparedHandlerParam(content)
                    };
                }
            } else {
                // Specific provider — providerNo bound as a parameter.
                sql = "select * from log force index (datetime)"
                        + " where provider_no = ? and dateTime <= ? and dateTime >= ?"
                        + " and content like ? order by dateTime desc";
                params = new DBPreparedHandlerParam[]{
                        new DBPreparedHandlerParam(providerNo),
                        endDateParam,
                        startDateParam,
                        new DBPreparedHandlerParam(content)
                };
            }

            Vector<Properties> vec = new Vector<>();
            ResultSet logRs = dbObj.queryResults(sql, params);
            try {
                while (logRs.next()) {
                    Properties prop = new Properties();
                    prop.setProperty("dateTime", "" + logRs.getTimestamp("dateTime"));
                    // Do not pre-encode these string fields — the JSP uses <c:out> which encodes on output.
                    // Pre-encoding here would cause double-encoding (e.g. "<" → "&amp;lt;").
                    prop.setProperty("action", Misc.getString(logRs, "action"));
                    prop.setProperty("content", Misc.getString(logRs, "content"));
                    prop.setProperty("contentId", Misc.getString(logRs, "contentId"));
                    prop.setProperty("ip", Misc.getString(logRs, "ip"));
                    prop.setProperty("provider_no", Misc.getString(logRs, "provider_no"));
                    prop.setProperty("demographic_no", Misc.getString(logRs, "demographic_no"));
                    // For 'data' we inject <br/> line-break tags, so we must encode HTML-special chars
                    // first and then add the <br/> tags. The JSP outputs this field raw (not via <c:out>)
                    // to preserve the injected markup.
                    prop.setProperty("data",
                            Encode.forHtml(Misc.getString(logRs, "data")).replaceAll("\n", "<br/>"));
                    vec.add(prop);
                }
            } finally {
                logRs.close();
            }

            request.setAttribute("vec", vec);
            request.setAttribute("bAll", bAll);
            request.setAttribute("providerNo", providerNo);
            request.setAttribute("startDate", sDate);
            request.setAttribute("endDate", eDate);
        }

        return SUCCESS;
    }
}
