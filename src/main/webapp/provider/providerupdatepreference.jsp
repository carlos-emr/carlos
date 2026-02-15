<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    Originally written for the Department of Family Medicine, McMaster University.
    Now maintained by the CARLOS EMR Project.
    https://github.com/carlos-emr/carlos

--%>

<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ page import="java.sql.*, java.util.*, io.github.carlos_emr.*" errorPage="/errorpage.jsp" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@page import="io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.provider.web.ProviderPropertyAction" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="java.util.UUID" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script LANGUAGE="JavaScript">
            <!--
            function start() {
                this.focus();
            }

            //-->
        </script>
    </head>

    <body>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="90%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerupdatepreference.description"/></font></th>
            </tr>
        </table>
        <%
            String programId_forCME = request.getParameter("case_program_id");
            request.getSession().setAttribute("case_program_id", programId_forCME);

            String selected_site = (String) request.getParameter("site");
            if (selected_site != null) {
                session.setAttribute("site_selected", (selected_site.equals("none") ? null : selected_site));
            }
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            if (loggedInInfo == null) {
                response.sendRedirect(request.getContextPath() + "/logout.jsp");
                return;
            }

            // Security check - require write access to _pref security object
            SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
                throw new SecurityException("missing required sec object: _pref (write access required)");
            }

            boolean saveSuccess = false;
            String errorDetails = null;
            ProviderPreference providerPreference = null;

            String curUser_providerno = loggedInInfo.getLoggedInProviderNo();
            String ticklerforproviderno = request.getParameter("ticklerforproviderno");
            if (ticklerforproviderno != null && !ticklerforproviderno.trim().isEmpty()) {
                // Validate provider number before persisting
                ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
                
                if (!providerDao.providerExists(ticklerforproviderno.trim())) {
                    String correlationId = java.util.UUID.randomUUID().toString();
                    io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                        "Invalid provider number for tickler warning. Correlation ID: {}. Provider: {}", 
                        correlationId, 
                        org.owasp.encoder.Encode.forJava(ticklerforproviderno)
                    );
                    errorDetails = "Invalid provider number. Please contact support with ID: " + correlationId;
                } else {
                    UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
                    UserProperty prop = propDao.getProp(curUser_providerno, UserProperty.PROVIDER_FOR_TICKLER_WARNING);
                    if (prop == null) {
                        prop = new UserProperty();
                        prop.setProviderNo(curUser_providerno);
                        prop.setName(UserProperty.PROVIDER_FOR_TICKLER_WARNING);
                    }
                    prop.setValue(ticklerforproviderno.trim());
                    propDao.saveProp(prop);
                }
            }
            
            try {
                providerPreference = ProviderPreferencesUIBean.updateOrCreateProviderPreferences(request);
                ProviderPropertyAction.updateOrCreateProviderProperties(request);

                // IMPORTANT: Only update session after both saves succeed to avoid inconsistent state
                session.setAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, providerPreference);
                session.setAttribute("default_servicetype", providerPreference.getDefaultServiceType());
                session.setAttribute("newticklerwarningwindow", providerPreference.getNewTicklerWarningWindow());
                session.setAttribute("default_pmm", providerPreference.getDefaultCaisiPmm());
                session.setAttribute("caisiBillingPreferenceNotDelete", providerPreference.getDefaultDoNotDeleteBilling());
                session.setAttribute("defaultDxCode", providerPreference.getDefaultDxCode());
                
                saveSuccess = true;
            } catch (IllegalArgumentException e) {
                // Validation errors - show user-friendly message from exception
                io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn("Validation errors for provider {}: {}", curUser_providerno, e.getMessage());
                errorDetails = e.getMessage();
            } catch (javax.persistence.PersistenceException e) {
                String correlationId = UUID.randomUUID().toString();
                io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Database error saving provider preferences for provider " + curUser_providerno + " [Correlation ID: " + correlationId + "]", e);
                errorDetails = "A database error occurred while saving preferences. Please contact support with reference ID: " + correlationId;
            } catch (Exception e) {
                String correlationId = UUID.randomUUID().toString();
                io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error("Unexpected error saving provider preferences for provider " + curUser_providerno + " [Correlation ID: " + correlationId + "]", e);
                errorDetails = "An unexpected error occurred while saving preferences. Please contact support with reference ID: " + correlationId;
            }
        %>
        <% if (saveSuccess) { %>
        <script LANGUAGE="JavaScript">
            if (self.opener && typeof self.opener.refresh1 === 'function') {
                self.opener.refresh1();
            }
            self.close();
        </script>
        <% } else { %>
        <div style="color: red; font-weight: bold; padding: 20px; text-align: center;">
            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerupdatepreference.error"/></p>
            <% if (errorDetails != null && !errorDetails.isEmpty()) { %>
            <p style="font-size: 0.9em; color: #666;"><fmt:message key="provider.providerupdatepreference.error.details"/>: <%= org.owasp.encoder.Encode.forHtml(errorDetails) %></p>
            <% } %>
            <p><fmt:message key="provider.providerupdatepreference.error.retry"/></p>
        </div>
        <% } %>
        <p></p>
        <hr width="90%"/>
        <form><input type="button"
                     value=
                         <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> onClick="self.close()">
        </form>
    </center>
    </body>
</html>
