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

--%>
<%@page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="ca.openosp.openo.providers.data.*, ca.openosp.openo.log.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="ca.openosp.*,java.lang.*,java.util.Date" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.providers.data.ProviderData" %>
<%@ page import="ca.openosp.openo.providers.data.ProSignatureData" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.util.RxUtil" %>
<%@ page import="ca.openosp.openo.prescript.data.RxProviderData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    RxSessionBean bean = null;
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <% response.sendRedirect("error.html"); %>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
            <%
                // Directly access the RxSessionBean from the session
                bean = (RxSessionBean) session.getAttribute("RxSessionBean");
                if (!bean.isValid()) {
                    response.sendRedirect("error.html");
                }
            %>
        </c:if>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarRx/styles.css">
        <script type="text/javascript" language="Javascript">
            function onPrint(cfgPage) {
                //document.forms[0].submit.value="print";
                //var ret = checkAllDates();
                //if(ret==true) {
                document.forms[0].action = "<%= request.getContextPath() %>/form/createpdf?__title=Rx&__cfgfile=" + cfgPage + "&__template=a6blank";
                document.forms[0].target = "_blank";
                //}
                return true;
            }
        </script>

    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">

    <%
        Date rxDate = RxUtil.Today();
        String rePrint = request.getParameter("rePrint");
        RxProviderData.Provider provider;
        String signingProvider;
        if (rePrint != null && rePrint.equalsIgnoreCase("true")) {
            bean = (RxSessionBean) session.getAttribute("tmpBeanRX");
            signingProvider = bean.getStashItem(0).getProviderNo();
            rxDate = bean.getStashItem(0).getRxDate();
            provider = new RxProviderData().getProvider(signingProvider);
            session.setAttribute("tmpBeanRX", null);
            String ip = request.getRemoteAddr();
            //LogAction.addLog((String) session.getAttribute("user"), LogConst.UPDATE, LogConst.CON_PRESCRIPTION, String.valueOf(bean.getDemographicNo()), ip);
        } else {
            bean = (RxSessionBean) pageContext.findAttribute("RxSessionBean");

            //set Date to latest in stash
            Date tmp;
            for (int idx = 0; idx < bean.getStashSize(); ++idx) {
                tmp = bean.getStashItem(idx).getRxDate();
                if (tmp.after(rxDate)) {
                    rxDate = tmp;
                }
            }
            rePrint = "";
            signingProvider = bean.getProviderNo();
            provider = new RxProviderData().getProvider(bean.getProviderNo());
        }


        RxPatientData.Patient patient = RxPatientData.getPatient(LoggedInInfo.getLoggedInInfoFromSession(request), bean.getDemographicNo());

        RxPrescriptionData.Prescription rx = null;
        int i;
        ProSignatureData sig = new ProSignatureData();
        boolean hasSig = sig.hasSignature(signingProvider);
        String doctorName = "";
        if (hasSig) {
            doctorName = sig.getSignature(signingProvider);
        } else {
            doctorName = (provider.getFirstName() + ' ' + provider.getSurname());
        }

        doctorName = doctorName.replaceAll("\\d{6}", "");
        doctorName = doctorName.replaceAll("\\-", "");

        OscarProperties props = OscarProperties.getInstance();

        String pracNo = provider.getPractitionerNo();
        String strUser = (String) session.getAttribute("user");
        ProviderData user = new ProviderData(strUser);
    %>
    <form action="${pageContext.request.contextPath}/form/formname.do" method="post">

        <table width="400px" height="500px" cellspacing=0 cellpadding=10
               border=2>
            <tr>
                <td valign=top height="100px"><input type="image"
                                                     src="oscarRx/img/rx.gif" border="0" value="submit" alt="[Submit]"
                                                     name="submit" title="Print in a half letter size paper"
                                                     onclick="<%=rePrint.equalsIgnoreCase("true") ? "javascript:return onPrint('oscarRxRePrintCfgPg1');" : "javascript:return onPrint('oscarRxPrintCfgPg1');" %>">
                    <input type="hidden" name="printPageSize"
                           value="PageSize.A6"/> <% String clinicTitle = provider.getClinicName().replaceAll("\\(\\d{6}\\)", "") + "<br>";
                        clinicTitle += provider.getClinicAddress() + "<br>";
                        clinicTitle += provider.getClinicCity() + "   " + provider.getClinicPostal();
                    %> <input type="hidden" name="doctorName"
                              value="<%= StringEscapeUtils.escapeHtml4(doctorName) %>"/> <c:choose>
                        <c:when test="${empty infirmaryView_programAddress}">
                            <input type="hidden" name="clinicName"
                                   value="<%= StringEscapeUtils.escapeHtml4(clinicTitle.replaceAll("(<br>)","\\\n")) %>"/>
                            <input type="hidden" name="clinicPhone"
                                   value="<%= StringEscapeUtils.escapeHtml4(provider.getClinicPhone()) %>"/>
                            <input type="hidden" name="clinicFax"
                                   value="<%= StringEscapeUtils.escapeHtml4(provider.getClinicFax()) %>"/>
                        </c:when>
                        <c:otherwise>
                            <input type="hidden" name="clinicName"
                                   value="<c:out value="${infirmaryView_programAddress}"/>"/>
                            <input type="hidden" name="clinicPhone"
                                   value="<c:out value="${infirmaryView_programTel}"/>"/>
                            <input type="hidden" name="clinicFax"
                                   value="<c:out value="${infirmaryView_programFax}"/>"/>
                        </c:otherwise>
                    </c:choose> <input type="hidden" name="patientName"
                                       value="<%= StringEscapeUtils.escapeHtml4(patient.getFirstName())+ " " +StringEscapeUtils.escapeHtml4(patient.getSurname()) %>"/>
                    <input type="hidden" name="patientAddress"
                           value="<%= StringEscapeUtils.escapeHtml4(patient.getAddress()) %>"/>
                    <input type="hidden" name="patientCityPostal"
                           value="<%= StringEscapeUtils.escapeHtml4(patient.getCity())+ ", " + StringEscapeUtils.escapeHtml4(patient.getProvince()) + " " + StringEscapeUtils.escapeHtml4(patient.getPostal())%>"/>
                    <input type="hidden" name="patientPhone"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/><%=StringEscapeUtils.escapeHtml4(patient.getPhone()) %>"/>

                    <input type="hidden" name="rxDate"
                           value="<%= StringEscapeUtils.escapeHtml4(RxUtil.DateToString(rxDate, "MMMM d, yyyy")) %>"/>
                    <input type="hidden" name="sigDoctorName"
                           value="<%= StringEscapeUtils.escapeHtml4(doctorName) %>"/>
                    <!--img src="img/prescript.gif" border="0"-->
                </td>
                <td valign=top height="100px" id="clinicAddress"><b><%=doctorName%>
                </b><br>
                    <c:choose>
                        <c:when test="${empty infirmaryView_programAddress}">
                            <%= provider.getClinicName().replaceAll("\\(\\d{6}\\)", "") %><br>
                            <%= provider.getClinicAddress() %><br>
                            <%= provider.getClinicCity() %>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                            <%= provider.getClinicPostal() %><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/>: <%= provider.getClinicPhone() %><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgFax"/>: <%= provider.getClinicFax() %><br>
                        </c:when>
                        <c:otherwise>
                            <c:out value="${infirmaryView_programAddress}" escapeXml="false"/>
                            <br/>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/>: <c:out value="${infirmaryView_programTel}"/>
                            <br/>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgFax"/>: <c:out value="${infirmaryView_programFax}"/>
                        </c:otherwise>
                    </c:choose></td>
            </tr>
            <tr>
                <td colspan=2 valign=top height="75px">
                    <table width=100% cellspacing=0 cellpadding=0>
                        <tr>
                            <td align=left valign=top><br>
                                <%= patient.getFirstName() %> <%= patient.getSurname() %><br>
                                <%= patient.getAddress() %><br>
                                <%= patient.getCity() %>, <%= patient.getProvince() %> <%= patient.getPostal() %><br>
                                <%= patient.getPhone() %><br>
                                <b><% if (!props.getProperty("showRxHin", "").equals("false")) { %>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.rx.hin"/><%= patient.getHin() %> <% } %>
                                </b><br>
                                <% if (props.getProperty("showRxChartNo", "").equalsIgnoreCase("true")) { %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.rx.chartNo"/><%=patient.getChartNo()%> <% } %></td>
                            <td align=right valign=top>
                                <b><%= RxUtil.DateToString(rxDate, "MMMM d, yyyy", request.getLocale()) %>
                                </b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td colspan=2 valign=top height="275px">
                    <table height=100% width=100%>
                        <tr valign=top>
                            <td colspan=2 height=225px>
                                <%
                                    String strRx = "";
                                    StringBuffer strRxNoNewLines = new StringBuffer();
                                    for (i = 0; i < bean.getStashSize(); i++) {
                                        rx = bean.getStashItem(i);
                                        String fullOutLine = rx.getFullOutLine().replaceAll(";", "<br />");

                                        if (fullOutLine == null || fullOutLine.length() <= 6) {
                                            ca.openosp.openo.utility.MiscUtils.getLogger();
                                            fullOutLine = "<span style=\"color:red;font-size:16;font-weight:bold\">An error occurred, please write a new prescription.</span><br />" + fullOutLine;
                                        }
                                %>
                                <%=fullOutLine%>
                                <hr>
                                <%
                                        strRx += rx.getFullOutLine() + ";;";
                                        strRxNoNewLines.append(rx.getFullOutLine().replaceAll(";", " ") + "\n");
                                    }
                                %> <input type="hidden" name="rx"
                                          value="<%= StringEscapeUtils.escapeHtml4(strRx.replaceAll(";","\\\n")) %>"/>
                                <input type="hidden" name="rx_no_newlines"
                                       value="<%= strRxNoNewLines.toString() %>"/></td>
                        </tr>

                        <tr valign="bottom">
                            <td colspan="2" id="additNotes"></td>
                        </tr>


                        <% if (ca.openosp.OscarProperties.getInstance().getProperty("RX_FOOTER") != null) {
                            out.write(ca.openosp.OscarProperties.getInstance().getProperty("RX_FOOTER"));
                        }%>


                        <tr valign=bottom>
                            <td height=25px width=25%><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgSignature"/>:</td>
                            <td height=25px width=75%
                                style="border-width: 0; border-bottom-width: 1; border-style: solid;">
                                &nbsp;
                            </td>
                        </tr>
                        <tr valign=bottom>
                            <td height=25px></td>

                            <td height=25px>
                                &nbsp; <%= doctorName%> <% if (pracNo != null && !pracNo.equals("") && !pracNo.equalsIgnoreCase("null")) { %>
                                <br/> &nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.PractNo"/> <%= pracNo%> <% } %>
                            </td>


                        </tr>
                        <% if (rePrint.equalsIgnoreCase("true") && rx != null) { %>
                        <tr valign=bottom style="font-size: 6px;">
                            <td height=25px colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgReprintBy"/> <%=user.getProviderName(strUser)%><span
                                    style="float: left;">
					<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgOrigPrinted"/>:&nbsp;<%=rx.getPrintDate()%></span> <span
                                    style="float: right;"><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTimesPrinted"/>:&nbsp;<%=String.valueOf(rx.getNumPrints())%></span>
                            </td>
                            <input type="hidden" name="origPrintDate"
                                   value="<%=rx.getPrintDate()%>">
                            <input type="hidden" name="numPrints"
                                   value="<%=String.valueOf(rx.getNumPrints())%>">
                        </tr>

                        <%
                            }
                            if (ca.openosp.OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") != null) {%>
                        <tr valign=bottom align="center" style="font-size: 9px">
                            <td height=25px colspan="2"></br>
                                <%= ca.openosp.OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") %>
                            </td>
                        </tr>
                        <%}%>
                    </table>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
