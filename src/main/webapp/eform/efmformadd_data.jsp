<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page import="io.github.carlos_emr.carlos.eform.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.managers.EmailComposeManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager"%>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.eform.data.EForm" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
	Addition of a floating global toolbar specifically for activation of the
	Fax and eDocument functions.
--%>

<c:if test="${ not empty requestScope.page_errors }">
    <script type='text/javascript'>
        function hideDiv() {
            if (document.getElementById) { // DOM3 = IE5, NS6
                document.getElementById('hideshow').style.display = 'none';
            } else {
                if (document.layers) { // Netscape 4
                    document.hideshow.display = 'none';
                } else { // IE 4
                    document.all.hideshow.style.display = 'none';
                }
            }
        }
    </script>

    <div id="hideshow" style="position: relative; z-index: 999;">
        <a href="javascript:hideDiv()">Hide Errors</a>
        <span style="font-size: 10px; font-color: darkred;"> <%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %> </span>
    </div>
</c:if>

<%!
    public void addHiddenEmailProperties(LoggedInInfo loggedInInfo, EForm thisEForm, String demographicNo) {
        EmailComposeManager emailComposeManager = SpringUtils.getBean(EmailComposeManager.class);
        if (!emailComposeManager.hasEmailPrivilege(loggedInInfo, SecurityInfoManager.WRITE)) {
            thisEForm.addHiddenInputElement("hasEmailPrivilege", Boolean.FALSE.toString());
            return;
        }

        Boolean hasValidRecipient = emailComposeManager.hasValidRecipient(loggedInInfo, Integer.parseInt(demographicNo));
        String[] emailConsent = emailComposeManager.getEmailConsentStatus(loggedInInfo, Integer.parseInt(demographicNo));

        thisEForm.addHiddenInputElement("hasValidRecipient", Boolean.toString(hasValidRecipient));
        thisEForm.addHiddenInputElement("emailConsentName", emailConsent[0]);
        thisEForm.addHiddenInputElement("emailConsentStatus", emailConsent[1]);
    }
%>

<%
    /**
     * TODO: Move all JSP scriptlet code from efmshowform_data.jsp and efmformadd_data.jsp to the ShowEFormAction.java (create if necessary) action file.
     */
    String provider_no = (String) session.getValue("user");
    String demographic_no = request.getParameter("demographic_no");
    String appointment_no = request.getParameter("appointment");
    String fid = request.getParameter("fid");
    String eform_link = request.getParameter("eform_link");
    String source = request.getParameter("source");


    EForm thisEForm = null;
    if (fid == null || demographic_no == null) {
        //if the info is in the request attribute
        thisEForm = (EForm) request.getAttribute("curform");
    } else {
        //if the info is in the request parameter
        thisEForm = new EForm(fid, demographic_no);
        thisEForm.setProviderNo(provider_no);  //needs providers for the action
    }

    if (appointment_no != null) {
        thisEForm.setAppointmentNo(appointment_no);
    }

    if (eform_link != null) {
        thisEForm.setEformLink(eform_link);
    }

    thisEForm.setContextPath(request.getContextPath());
    thisEForm.setImagePath();
    thisEForm.setDatabaseAPs();
    thisEForm.setOscarOPEN(request.getRequestURI());
    thisEForm.setAction();
    thisEForm.setSource(source);

    /*
     * Modifying EForm by directly incorporating libraries and adding hidden fields.
     * Ordering is very important.
     * For Javascript: First is last.
     */
    thisEForm.addHeadJavascript(request.getContextPath()+"/js/jquery.are-you-sure.js");
    thisEForm.addHeadJavascript(request.getContextPath()+"/library/jquery/jquery-ui-1.12.1.min.js");
    thisEForm.addHeadJavascript(request.getContextPath()+"/library/jquery/jquery-3.6.4.min.js");

    thisEForm.addCSS(request.getContextPath()+"/library/bootstrap/5.3.3/css/bootstrap.min.css", "all");
    thisEForm.addHeadJavascript(request.getContextPath()+"/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js");

    thisEForm.addCSS(request.getContextPath()+"/css/oscar_alert.css", "all");
    thisEForm.addCSS(request.getContextPath()+"/library/jquery/jquery-ui-1.12.1.min.css", "all");
    thisEForm.addBodyJavascript(request.getContextPath()+"/eform/eformFloatingToolbar/eform_floating_toolbar.js");
    thisEForm.addBodyJavascript(request.getContextPath()+"/js/oscar-alert.js");
    thisEForm.addHiddenInputElement("context", request.getContextPath());
    thisEForm.addHiddenInputElement("demographicNo", demographic_no);
    thisEForm.addHiddenInputElement("fid", fid);
    thisEForm.addHiddenInputElement("fdid", request.getParameter("fdid"));
    thisEForm.addHiddenInputElement("newForm", "true");

    // Add email consent properties
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    addHiddenEmailProperties(loggedInInfo, thisEForm, demographic_no);

    out.print(thisEForm.getFormHtml());
%>
