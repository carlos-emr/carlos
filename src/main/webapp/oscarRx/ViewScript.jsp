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
<%@ page import="ca.openosp.openo.providers.data.*, ca.openosp.OscarProperties, ca.openosp.openo.clinic.ClinicData, java.util.*" %>
<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.commn.model.Site" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Appointment" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@ page import="ca.openosp.openo.providers.data.ProviderData" %>
<%@ page import="ca.openosp.openo.providers.data.ProSignatureData" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxProviderData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%! boolean bMultisites = IsPropertiesOn.isMultisitesEnable(); %>
<%
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
<%
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
%>
<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.title"/></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <c:if test="${empty sessionScope.RxSessionBean}">
            <c:redirect url="error.html"/>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
            <c:set var="bean" value="${sessionScope.RxSessionBean}" scope="page"/>
            <c:if test="${bean.valid == false}">
                <c:redirect url="error.html"/>
            </c:if>
        </c:if>

        <%
            RxSessionBean bean = (RxSessionBean) session.getAttribute("RxSessionBean");

//are we printing in the past?
            String reprint = (String) request.getAttribute("rePrint") != null ? (String) request.getAttribute("rePrint") : "false";
            String createAnewRx;
            if (reprint.equalsIgnoreCase("true")) {
                bean = (RxSessionBean) session.getAttribute("tmpBeanRX");
                createAnewRx = "window.location.href = '" + request.getContextPath() + "/oscarRx/SearchDrug.jsp'";
            } else
                createAnewRx = "javascript:clearPending('')";

// for satellite clinics
            Vector vecAddressName = null;
            Vector vecAddress = null;
            Vector vecAddressPhone = null;
            Vector vecAddressFax = null;
            OscarProperties props = OscarProperties.getInstance();
            if (bMultisites) {
                String appt_no = (String) session.getAttribute("cur_appointment_no");
                String location = null;
                if (appt_no != null) {
                    Appointment result = appointmentDao.find(Integer.parseInt(appt_no));
                    if (result != null) location = result.getLocation();
                }

                RxProviderData.Provider provider = new RxProviderData().getProvider(bean.getProviderNo());
                ProSignatureData sig = new ProSignatureData();
                boolean hasSig = sig.hasSignature(bean.getProviderNo());
                String doctorName = "";
                if (hasSig) {
                    doctorName = sig.getSignature(bean.getProviderNo());
                } else {
                    doctorName = (provider.getFirstName() + ' ' + provider.getSurname());
                }
                doctorName = doctorName.replaceAll("\\d{6}", "");
                doctorName = doctorName.replaceAll("\\-", "");

                vecAddressName = new Vector();
                vecAddress = new Vector();
                vecAddressPhone = new Vector();
                vecAddressFax = new Vector();

                java.util.ResourceBundle rb = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                List<Site> sites = siteDao.getActiveSitesByProviderNo((String) session.getAttribute("user"));

                for (int i = 0; i < sites.size(); i++) {
                    Site s = sites.get(i);
                    vecAddressName.add(s.getName());
                    vecAddress.add("<b>" + doctorName + "</b><br>" + s.getName() + "<br>" + s.getAddress() + "<br>" + s.getCity() + ", " + s.getProvince() + " " + s.getPostal() + "<br>" + rb.getString("RxPreview.msgTel") + ": " + s.getPhone() + "<br>" + rb.getString("RxPreview.msgFax") + ": " + s.getFax());
                    if (s.getName().equals(location))
                        session.setAttribute("RX_ADDR", String.valueOf(i));
                }


            } else if (props.getProperty("clinicSatelliteName") != null) {
                RxProviderData.Provider provider = new RxProviderData().getProvider(bean.getProviderNo());
                ProSignatureData sig = new ProSignatureData();
                boolean hasSig = sig.hasSignature(bean.getProviderNo());
                String doctorName = "";
                if (hasSig) {
                    doctorName = sig.getSignature(bean.getProviderNo());
                } else {
                    doctorName = (provider.getFirstName() + ' ' + provider.getSurname());
                }

                ClinicData clinic = new ClinicData();
                vecAddressName = new Vector();
                vecAddress = new Vector();
                vecAddressPhone = new Vector();
                vecAddressFax = new Vector();
                String[] temp0 = props.getProperty("clinicSatelliteName", "").split("\\|");
                String[] temp1 = props.getProperty("clinicSatelliteAddress", "").split("\\|");
                String[] temp2 = props.getProperty("clinicSatelliteCity", "").split("\\|");
                String[] temp3 = props.getProperty("clinicSatelliteProvince", "").split("\\|");
                String[] temp4 = props.getProperty("clinicSatellitePostal", "").split("\\|");
                String[] temp5 = props.getProperty("clinicSatellitePhone", "").split("\\|");
                String[] temp6 = props.getProperty("clinicSatelliteFax", "").split("\\|");
                java.util.ResourceBundle rb = java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

                for (int i = 0; i < temp0.length; i++) {
                    vecAddressName.add(temp0[i]);
                    vecAddress.add("<b>" + doctorName + "</b><br>" + temp0[i] + "<br>" + temp1[i] + "<br>" + temp2[i] + ", " + temp3[i] + " " + temp4[i] + "<br>" + rb.getString("RxPreview.msgTel") + ": " + temp5[i] + "<br>" + rb.getString("RxPreview.msgFax") + ": " + temp6[i]);
                }
            }
            String comment = (String) request.getAttribute("comment");
        %>
        <link rel="stylesheet" type="text/css" href="oscarRx/styles.css"/>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>

        <script type="text/javascript">

            function setComment() {
                frames['preview'].document.getElementById('additNotes').innerHTML = '<%=comment%>';
            }

            function setDefaultAddr() {
                var url = "setDefaultAddr.jsp";
                var ran_number = Math.round(Math.random() * 1000000);
                var addr = encodeURIComponent(document.getElementById('addressSel').value);
                var params = "addr=" + addr + "&rand=" + ran_number;
                new Ajax.Request(url, {method: 'post', parameters: params});
            }


            function addNotes() {


                var url = "AddRxComment.jsp";
                var ran_number = Math.round(Math.random() * 1000000);
                var comment = encodeURIComponent(document.getElementById('additionalNotes').value);
                var params = "scriptNo=<%=request.getAttribute("scriptId")%>&comment=" + comment + "&rand=" + ran_number;  //]
                new Ajax.Request(url, {method: 'post', parameters: params});
                frames['preview'].document.getElementById('additNotes').innerHTML = document.getElementById('additionalNotes').value;
            }


            function printIframe() {
                var browserName = navigator.appName;
                if (browserName == "Microsoft Internet Explorer") {
                    try {
                        iframe = document.getElementById('preview');
                        iframe.contentWindow.document.execCommand('print', false, null);
                    } catch (e) {
                        window.print();
                    }
                } else {
                    preview.focus();
                    preview.print();
                }
            }


            function printPaste2Parent() {

                try {
                    text = "****<%=ProviderData.getProviderName(bean.getProviderNo())%>********************************************************************************";
                    text = text.substring(0, 82) + "\n";
                    if (document.all) {
                        text += preview.document.forms[0].rx_no_newlines.value
                    } else {
                        text += preview.document.forms[0].rx_no_newlines.value + "\n";
                    }
                    text += "**********************************************************************************\n";

                    //we support pasting into orig encounter and new casemanagement
                    if (opener.document.forms["caseManagementEntryForm"] != undefined) {
                        opener.pasteToEncounterNote(text);
                    } else if (opener.document.encForm != undefined)
                        opener.document.encForm.enTextarea.value = opener.document.encForm.enTextarea.value + text;

                } catch (e) {
                    alert("ERROR: could not paste to EMR");
                }
                printIframe();
            }


            function addressSelect() {
                <% if(vecAddressName != null) { %>
                setDefaultAddr();
                <%      for(int i=0; i<vecAddressName.size(); i++) {%>
                if (document.getElementById("addressSel").value == "<%=i%>") {
                    frames['preview'].document.getElementById("clinicAddress").innerHTML = "<%=vecAddress.get(i)%>";
                }
                <%       }
                      }%>

                <%if (comment != null){ %>
                setComment();
                <%}%>


            }

        </script>
    </head>

    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="addressSelect()">

    <!-- HSFO functionality removed -->
    <div id="bodyView">


            <table border="0" cellpadding="0" cellspacing="0"
                   style="border-collapse: collapse" bordercolor="#111111" width="100%"
                   id="AutoNumber1" height="100%">
                <tr>
                    <td width="100%"
                        style="padding-left: 3; padding-right: 3; padding-top: 2; padding-bottom: 2"
                        height="0%" colspan="2">
                    </td>
                </tr>

                <tr>
                    <td width="10%" height="37" bgcolor="#000000">&nbsp;</td>
                    <td width="100%" bgcolor="#000000" class="leftGreyLine" height="0%">
                        <table>
                            <tr>
                                <td><span class="ScreenTitle"> oscarRx </span></td>
                                <td width=10px></td>
                                <td><span style="color: #FFFFFF"> <b> <fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgRightClick"/></b> </span>
                                </td>
                                    <%-- right click on prescription<br>
                                    and select "print" from the menu.
                                                    --%>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td></td>

                    <td width="100%" class="leftGreyLine" height="100%" valign="top">
                        <table style="border-collapse: collapse" bordercolor="#111111"
                               width="100%" height="100%">

                            <!----Start new rows here-->
                            <tr>
                                <td colspan=2>
                                    <div class="DivContentPadding"><span class="DivContentTitle"
                                                                         valign="middle"> <fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.title"/> </span></div>
                                </td>
                            </tr>

                            <tr>
                                <td width=440px>
                                    <div class="DivContentPadding">
                                        <iframe id=preview name=preview width=440px height=580px
                                                src="oscarRx/Preview.jsp?rePrint=<%=reprint%>"
                                                align=center border=0 frameborder=0></iframe>
                                    </div>
                                </td>

                                <td valign=top><form action="${pageContext.request.contextPath}/oscarRx/clearPending.do" method="post">
                                    <input type="hidden" name="action" id="action" value=""/>
                                </form>
                                    <script language=javascript>
                                        function clearPending(action) {
                                            document.forms.RxClearPendingForm.action.value = action;
                                            document.forms.RxClearPendingForm.submit();
                                        }
                                    </script>
                                    <script language=javascript>
                                        function ShowDrugInfo(drug) {
                                            window.open("drugInfo.do?GN=" + escape(drug), "_blank",
                                                "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
                                        }
                                    </script>

                                    <table cellpadding=10 cellspacing=0>
                                        <% if (vecAddress != null) { %>
                                        <tr>
                                            <td align="center" colspan=2><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgAddress"/>
                                                <select
                                                        name="addressSel" id="addressSel" onChange="addressSelect()">
                                                    <% String rxAddr = (String) session.getAttribute("RX_ADDR");
                                                        for (int i = 0; i < vecAddressName.size(); i++) {
                                                            String te = (String) vecAddressName.get(i);
                                                            String tf = (String) vecAddress.get(i);%>

                                                    <option value="<%=i%>"
                                                            <% if ( rxAddr != null && rxAddr.equals(""+i)){ %>SELECTED<%}%>
                                                    ><%=te%>
                                                    </option>
                                                    <% }%>

                                                </select></td>
                                        </tr>
                                        <% } %>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold;"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgActions"/></span>
                                            </td>
                                        </tr>

                                        <tr>
                                            <td width=10px></td>
                                            <td><span><input type=button
                                                             value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgPrint"/>"
                                                             class="ControlPushButton" style="width: 200px"
                                                             onClick="javascript:printIframe();"/></span></td>
                                        </tr>
                                        <tr>
                                            <td width=10px></td>
                                            <td><span><input type=button
							<%=reprint.equals("true") ? "disabled='true'" : ""%>" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgPrintPasteEmr"/>"
							class="ControlPushButton" style="width: 200px"
							onClick="javascript:printPaste2Parent();" /></span></td>
                                        </tr>
                                        <tr>
                                            <td width=10px></td>
                                            <td><span><input type=button
                                                             value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgCreateNewRx"/>"
                                                             class="ControlPushButton"
                                                             style="width: 200px" onClick="<%=createAnewRx%>"/></span>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td width=10px></td>
                                            <td><span><input type=button
                                                             value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgBackToOscar"/>"
                                                             class="ControlPushButton" style="width: 200px"
                                                             onClick="javascript:clearPending('close');"/></span></td>
                                        </tr>

                                        <%if (request.getAttribute("rePrint") == null) {%>

                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgAddNotesRx"/></span></td>
                                        </tr>
                                        <tr>
                                            <td width=10px></td>
                                            <td>
                                                <textarea id="additionalNotes" style="width: 200px"
                                                          onchange="javascript:addNotes();"></textarea>
                                                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgAddToRx"/>"
                                                       onclick="javascript:addNotes();"/>
                                            </td>
                                        </tr>

                                        <%}%>
                                        <tr>
                                            <td colspan=2 style="font-weight: bold"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="ViewScript.msgDrugInfo"/></span></td>
                                        </tr>
                                        <%
                                            for (int i = 0; i < bean.getStashSize(); i++) {
                                                RxPrescriptionData.Prescription rx
                                                        = bean.getStashItem(i);

                                                if (!rx.isCustom()) {
                                        %>
                                        <tr>
                                            <td width=10px></td>
                                            <td><span><a
                                                    href="javascript:ShowDrugInfo('<%= rx.getGenericName() %>');">
						<%= rx.getGenericName() %> (<%= rx.getBrandName() %>) </a></span></td>
                                        </tr>
                                        <%
                                                }
                                            }
                                        %>
                                    </table>
                                </td>
                            </tr>


                            <!----End new rows here-->

                            <tr height="100%">
                                <td></td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td height="0%" class="leftBottomGreyLine"></td>
                    <td height="0%" class="leftBottomGreyLine"></td>
                </tr>
                <tr>
                    <td width="100%" height="0%" colspan="2">&nbsp;</td>
                </tr>
                <tr>
                    <td width="100%" height="0%" style="padding: 5" bgcolor="#DCDCDC"
                        colspan="2"></td>
                </tr>
            </table>

        </div>
    </body>
</html>
