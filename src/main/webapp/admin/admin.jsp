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
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin,_admin.schedule,_admin.billing,_admin.invoices,_admin.resource,_admin.reporting,_admin.backup,_admin.messenger,_admin.eform,_admin.encounter,_admin.misc,_admin.torontoRfq,_admin.flowsheet"
                   rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.*");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<%
    String curProvider_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
%>

<%@ page errorPage="/errorpage.jsp" %>
<% java.util.Properties oscarVariables = OscarProperties.getInstance(); %>
<%
    String country = request.getLocale().getCountry();
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<%@page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.page.title"/> Start Time
            : <%=OscarProperties.getInstance().getStartTime()%>
        </title>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script>
            jQuery.noConflict();
        </script>
        <oscar:customInterface section="admin"/>

        <script type="text/JavaScript">
            function onsub() {
                if (document.searchprovider.keyword.value == "") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="global.msgInputKeyword"/>");
                    return false;
                } else return true;
                // do nothing at the moment
                // check input data in the future
            }

            function popupOscarRx(vheight, vwidth, varpage) { //open a new popup window
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "oscarRx", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function popupPage(vheight, vwidth, varpage) { //open a new popup window
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
                var popup = window.open(page, "groupno", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function popUpBillStatus(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=no,menubars=no,toolbars=no,resizable=no,screenX=0,screenY=0,top=0,left=0";//360,680
                var popup = window.open(page, "groupno", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }
        </script>
        <style type="text/css">
            a:link {
                text-decoration: none;
                color: #003399;
            }

            a:active {
                text-decoration: none;
                color: #003399;
            }

            a:visited {
                text-decoration: none;
                color: #003399;
            }

            a:hover {
                text-decoration: none;
                color: #003399;
            }

            BODY {
                font-family: Arial, Verdana, Tahoma, Helvetica, sans-serif;
                background-color: #A9A9A9;
            }

            .title {
                font-size: 15pt;
                font-weight: bold;
                text-align: center;
                background-color: #000000;
                color: #FFFFFF;
            }

            div.adminBox {
                width: 90%;
                background-color: #eeeeff;
                margin-top: 2px;
                margin-left: auto;
                margin-right: auto;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.adminBox h3 {
                color: #ffffff;
                font-size: 14pt;
                font-weight: bold;
                text-align: left;
                background-color: #486ebd;
                margin-top: 0px;
                padding-top: 0px;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.adminBox ul {
                text-align: left;
                list-style: none;
                list-style-type: none;
                list-style-position: outside;
                padding-left: 1px;
                margin-left: 1px;
                margin-top: 0px;
                padding-top: 1px;
                margin-bottom: 0px;
                padding-bottom: 0px;
            }

            div.logoutBox {
                text-align: right;
            }
        </style>

    </head>

    <body class="BodyStyle">

    <div class="title"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.page.title"/></div>

    <div class="logoutBox">
        <%
            if (roleName$.equals("admin" + "," + curProvider_no)) {
        %><a href="${pageContext.request.contextPath}/logout.jsp">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnLogout"/>
        </a>&nbsp;<%
        }
    %>
    </div>

    <!-- #USER MANAGEMENT -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.torontoRfq,_admin.provider"
                       rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.UserManagement"/></h3>
            <ul>
                <li><a href="${pageContext.request.contextPath}/admin/provideraddarecordhtm.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddProvider"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/providersearchrecordshtm.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSearchProvider"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/securityaddarecord.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddLogin"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/securitysearchrecordshtm.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSearchLogin"/>
                </a></li>

                <li><a href="#"
                       onclick='popupPage(500,700,"${pageContext.request.contextPath}/admin/providerRole.jsp");return false;'>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.assignRole"/></a></li>

                <security:oscarSec roleName="<%=roleName$%>"
                                   objectName="_admin,_admin.unlockAccount" rights="r">
                    <li><a href="#"
                           onclick='popupPage(500,800,"${pageContext.request.contextPath}/admin/unLock.jsp");return false;'>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.unlockAcct"/></a></li>
                </security:oscarSec>
            </ul>
        </div>
    </security:oscarSec>

    <!-- #USER MANAGEMENT END -->

    <!-- #BILLING -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.invoices,_admin,_admin.billing" rights="r"
                       reverse="<%=false%>">
        <div class="adminBox">
            <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.billing"/></h3>
            <ul>
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.billing" rights="r"
                                   reverse="<%=false%>">
                    <%
                        // Only show link to Clinicaid admin if Clinicaid Billing is enabled
                        if (oscarVariables.getProperty("billregion", "").equals("CLINICAID")) {
                    %>
                    <li>
                        <a href="<%= request.getContextPath() %>/billing.do?billRegion=CLINICAID&action=invoice_reports" target="_blank">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.invoiceRpts"/>
                        </a>
                    </li>
                    <%
                    } else if (oscarVariables.getProperty("billregion", "").equals("BC")) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/manageBillingform.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageBillFrm"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/billing/CA/BC/billingPrivateCodeAdjust.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManagePrivFrm"/></a></li>
                    <oscar:oscarPropertiesCheck property="BC_BILLING_CODE_MANAGEMENT"
                                                value="yes">
                        <li><a href="#"
                               onclick='popupPage(600,900,"${pageContext.request.contextPath}/billing/CA/BC/billingCodeAdjust.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageBillCodes"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,600,"${pageContext.request.contextPath}/billing/CA/BC/showServiceCodeAssocs.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageServiceDiagnosticCodeAssoc"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,500,"${pageContext.request.contextPath}/billing/CA/BC/supServiceCodeAssocAction.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageProcedureFeeCodeAssoc"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/billingManageReferralDoc.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageReferralDoc"/></a></li>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="no"
                                                defaultVal="true">
                        <li><a href="#"
                               onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/billingSim.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.SimulateSubFile"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,720,"${pageContext.request.contextPath}/billing/CA/BC/billingTeleplanGroupReport.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.genTeleplanFile"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="yes">
                        <li><a href="#"
                               onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/TeleplanSimulation.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.simulateSubFile2"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,720,"${pageContext.request.contextPath}/billing/CA/BC/TeleplanSubmission.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.genTeleplanFile2"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/BC/teleplan/ManageTeleplan.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageTeleplan"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="no"
                                                defaultVal="true">
                        <li><a href="#"
                               onclick='popupPage(600,800,"${pageContext.request.contextPath}/billing/CA/BC/billingTA.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.uploadRemittance"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,800,"${pageContext.request.contextPath}/billing/CA/BC/viewReconcileReports.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.reconciliationReports"/></a></li>
                    <li><a href="#"
                           onclick='popUpBillStatus(375,425,"${pageContext.request.contextPath}/billing/CA/BC/billingAccountReports.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.AccountingRpts"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/BC/billStatus.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.editInvoices"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(200,300,"${pageContext.request.contextPath}/billing/CA/BC/settleBG.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.settlePaidClaims"/></a></li>

                    <%-- Addition of BC MSP Quick Billing by Dennis Warren - December 2011 --%>
                    <li>
                        <a href='javascript: popupPage( 500, 900, "${pageContext.request.contextPath}/quickBillingBC.do");'>
                            BC MSP Quick Billing
                        </a>
                    </li>

                    <%
                    } else if (oscarVariables.getProperty("billregion", "").equals("ON")) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/ScheduleOfBenefitsUpload.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleOfBenefits"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(300,600, "${pageContext.request.contextPath}/billing/CA/ON/addEditServiceCode.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageBillingServiceCode"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(300,600, "${pageContext.request.contextPath}/billing/CA/ON/billingONEditPrivateCode.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.managePrivBillingCode"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/admin/manageCSSStyles.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageCodeStyles"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/gstControl.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageGSTControl"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/gstreport.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.gstReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/manageBillingLocation.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddBillingLocation"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/manageBillingform.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnManageBillingForm"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,700, "${pageContext.request.contextPath}/billing/CA/ON/billingOHIPsimulation.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSimulationOHIPDiskette"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,720, "${pageContext.request.contextPath}/billing/CA/ON/billingOHIPreport.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnGenerateOHIPDiskette"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,640, "${pageContext.request.contextPath}/billing/CA/ON/billingCorrection.jsp?billing_no=");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnBillingCorrection"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,820, "${pageContext.request.contextPath}/billing/CA/ON/batchBilling.jsp?service_code=all");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnBatchBilling"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,640, "${pageContext.request.contextPath}/billing/CA/ON/inr/reportINR.jsp?provider_no=all");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnINRBatchBilling"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,900, "${pageContext.request.contextPath}/billing/CA/ON/billingONUpload.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.uploadMOHFile"/></a></li>
                    <% if (OscarProperties.getInstance().isPropertyActive("moh_file_management_enabled")) { %>
                    <li><a href="#" onclick='popupPage(600,900, "${pageContext.request.contextPath}/billing/CA/ON/viewMOHFiles.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.viewMOHFiles"/></a></li>
                    <% } %>
                    <li><a href="#"
                           onclick='popupPage(600,900, "${pageContext.request.contextPath}<%= oscarVariables.getProperty("RA_FORWORD", "/billing/CA/ON/genRA.jsp") %>");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnBillingReconciliation"/></a></li>
                    <!-- li><a href="#" onclick ='popupPage(600,1000,"${pageContext.request.contextPath}/billing/CA/ON/billingOBECEA.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnEDTBillingReportGenerator"/></a></li-->
                    <li>
                        <a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/mcedt/mcedt.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.mcedt"/></a>
                    </li>
                    </li>
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/ON/billStatus.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.invoiceRpts"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/ON/endYearStatement.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.endYearStatement"/></a></li>
                    <%if (OscarProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
                    <li>
                        <a href='#' onclick='popupPage(300,750,"${pageContext.request.contextPath}/admin/clinicNbrManage.jsp");return false;'>Manage Clinic NBR Codes</a>
                    </li>
                    <%}%>
                    <li>
                        <a href='#' onclick='popupPage(300,750,"${pageContext.request.contextPath}/billing/CA/ON/managePaymentType.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.managePaymentType"/></a>
                    </li>

                    <%
                        }
                    %>
                </security:oscarSec>

                <% if (oscarVariables.getProperty("billregion", "").equals("ON")) { %>
                <li><a href="#" onclick="popupPage(800,1000,'/billing/CA/ON/billingONPayment.jsp');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.paymentReceived"/></a></li>
                <% } %>
            </ul>
        </div>
    </security:oscarSec>
    <!-- #BILLING END-->

    <!-- #LABS/INBOX -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin," rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.LabsInbox"/></h3>
            <ul>
                <li><a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/lab/CA/ALL/testUploader.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.hl7LabUpload"/></a></li>
                <oscar:oscarPropertiesCheck property="OLD_LAB_UPLOAD" value="yes"
                                            defaultVal="false">
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/lab/CA/BC/LabUpload.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.oldLabUpload"/></a></li>
                </oscar:oscarPropertiesCheck>
                <li><a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/admin/labforwardingrules.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.labFwdRules"/></a></li>
                <li><a href="javascript:void(0);" onclick='popupPage(550,800,"${pageContext.request.contextPath}/admin/addQueue.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.AddNewQueue"/></a></li>
            </ul>
        </div>

    </security:oscarSec>
    <!-- #LABS/INBOX END -->

    <!--  #FORMS/EFORMS -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.FormsEforms"/></h3>
            <ul>
                <li><a href="#"
                       onclick='popupPage(500,1000,"${pageContext.request.contextPath}/form/setupSelect.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSelectForm"/></a></li>
                <li><a href="#"
                       onclick='popupPage(500,1000,"${pageContext.request.contextPath}/form/formXmlUpload.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnImportFormData"/></a></li>
                <li><a href="${pageContext.request.contextPath}/eform/efmformmanager.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnUploadForm"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/eform/efmimagemanager.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnUploadImage"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/eform/efmmanageformgroups.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmGroups"/>
                </a></li>

                <% if (IsPropertiesOn.isIndivicaRichTextLetterEnable()) { %>
                <li><a href="${pageContext.request.contextPath}/eform/efmformrtl_config.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.richTextLetter"/>
                </a></li>
                <% } %>

                <li><a href="${pageContext.request.contextPath}/eform/efmmanageindependent.jsp">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmIndependent"/>
                </a></li>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fieldnote" rights="r"
                                   reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/eform/fieldNoteReport/fieldnotereport.jsp");return false;'>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.fieldNoteReport"/></a>
                    </li>
                </security:oscarSec>
            </ul>
        </div>
    </security:oscarSec>
    <!--  #FORMS/EFORMS END-->

    <!-- #REPORTS-->
    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.reporting" rights="r"
                           reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.oscarReport"/></h3>
                <ul>
                    <%
                        session.setAttribute("reportdownload", "/usr/local/tomcat/webapps/oscar_sfhc/oscarReport/download/");
                    %>

                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/RptByExample.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnQueryByExample"/></a></li>

                    <li>
                        <a href="${pageContext.request.contextPath}/oscarReport/reportByTemplate/homePage.jsp">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.rptbyTemplate"/>
                        </a>
                    </li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/dbReportAgeSex.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAgeSexReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/oscarReportVisitControl.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnVisitReport"/></a></li>
                        <%-- This links doesnt make sense on Brazil. Hide then --%>

                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/oscarReportCatchment.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnPCNCatchmentReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/FluBilling.do?orderby=");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnFluBillingReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,1000,"${pageContext.request.contextPath}/oscarReport/obec.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnOvernightChecking"/></a></li>


                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/patientlist.jsp")'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.exportPatientbyAppt"/></a></li>
                    <caisi:isModuleLoad moduleName="caisi">
                        <li><a href="${pageContext.request.contextPath}/PMmodule/reports/activity_report_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.activityRpt"/></a></li>
                    </caisi:isModuleLoad>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/provider_service_report_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.providerServiceRpt"/></a></li>
                    <caisi:isModuleLoad moduleName="caisi">
                        <li><a href="${pageContext.request.contextPath}/PopulationReport.do"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.popRpt"/></a></li>
                    </caisi:isModuleLoad>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/cds_4_report_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.cdsRpt"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/mis_report_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.misRpt"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/cbi_submit_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.cbiSubmit"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/cbiAdmin.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.cbi.reportlink"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/cbi_report_form.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.cbiRpt"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/UsageReport.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.usageRpt"/></a></li>
                    <oscar:oscarPropertiesCheck property="SERVERLOGGING" value="yes">
                        <li><a href="#"
                               onclick='popupPage(600,900, "${pageContext.request.contextPath}/admin/oscarLogging.jsp")'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.serverLog"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/report/DxresearchReport.do")'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.diseaseRegister"/></a></li>
                    <%
                        if (oscarVariables.getProperty("billregion", "").equals("ON")) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(660,1000, "${pageContext.request.contextPath}/report/reportonbilledphcp.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.PHCP"/></a>
                        <span style="font-size: x-small;"> (Setting: <a href="#"
                                                                        onclick='popupPage(660,1000, "${pageContext.request.contextPath}/report/reportonbilledvisitprovider.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.provider"/></a>,
			        ) </span></li>
                    <%
                        }

                    %>
                    <li>
                        <a href="#" onclick='popupPage(550,800, "${pageContext.request.contextPath}/renal/ckd_screening_report.jsp");return false;'>
                            CKD Screening Report
                        </a>
                    </li>
                    <li>
                        <a href="#" onclick='popupPage(550,800, "${pageContext.request.contextPath}/renal/preImplementationSubmit.jsp");return false;'>
                            Pre-Implementation Report
                        </a>
                    </li>
                    <li>
                        <a href="#" onclick='popupPage(550,800, "${pageContext.request.contextPath}/renal/patientLetterManager.jsp");return false;'>
                            Manage Patient Letter
                        </a>
                    </li>
                </ul>
            </div>
        </security:oscarSec>
    </caisi:isModuleLoad>
    <!-- #REPORTS END -->

    <!-- #ECHART -->
    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.encounter" rights="r"
                           reverse="<%=false%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.eChart"/></h3>
                <ul>

                    <security:oscarSec roleName="<%=roleName$%>" objectName="_newCasemgmt.templates" rights="w"
                                       reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/providertemplate.jsp");return false;'>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnInsertTemplate"/></a>
                        </li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
    </caisi:isModuleLoad>
    <!-- #ECHART END-->


        <%-- -add by caisi  TODO: move these under integration or system management?--%>
    <caisi:isModuleLoad moduleName="caisi">
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.caisi" rights="r" reverse="<%=false%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.caisi"/></h3>
                <ul>
                    <li><a href="${pageContext.request.contextPath}/SystemMessage.do">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.systemMessage"/>
                    </a></li>
                    <li><a href="${pageContext.request.contextPath}/FacilityMessage.do?"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.FacilitiesMsgs"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/issueAdmin.do?method=list">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.issueEditor"/>
                    </a></li>
                    <li><a href="${pageContext.request.contextPath}/DefaultEncounterIssue.do">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.defaultEncounterIssue"/>
                    </a></li>
                </ul>
            </div>
        </security:oscarSec>

        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.caisi" rights="r" reverse="<%=true%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.caisi"/></h3>
                <ul>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.systemMessage" rights="r" reverse="<%=false%>">
                        <li><a href="${pageContext.request.contextPath}/SystemMessage.do">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.systemMessage"/>
                        </a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.facilityMessage" rights="r" reverse="<%=false%>">
                        <li><a href="${pageContext.request.contextPath}/FacilityMessage.do?"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.FacilitiesMsgs"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.lookupFieldEditor" rights="r">
                        <li><a href="${pageContext.request.contextPath}/Lookup/LookupTableList.do"> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.LookupFieldEditor"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.issueEditor" rights="r">
                        <li><a href="${pageContext.request.contextPath}/issueAdmin.do?method=list">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.issueEditor"/>
                        </a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.userCreatedForms" rights="r">
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>

    </caisi:isModuleLoad>
        <%-- -add by caisi end--%>


    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">

        <!-- #Schedule Management -->
        <security:oscarSec roleName="<%=roleName$%>"
                           objectName="_admin,_admin.schedule,_admin.schedule.curprovider_only" rights="r"
                           reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ScheduleManagement"/></h3>
                <ul>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/schedule/scheduletemplatesetting.jsp");return false;'
                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleSettingTitle"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleSetting"/></a></li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.schedule.curprovider_only"
                                       rights="r" reverse="<%=true%>">
                        <oscar:oscarPropertiesCheck property="ENABLE_EDIT_APPT_STATUS"
                                                    value="yes">
                            <li><a href="#"
                                   onclick="popupPage(500,600,'${pageContext.request.contextPath}/appointment/apptStatusSetting.do');return false;"
                                   title="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleSettingTitle"/>"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.appointmentStatusSetting"/></a></li>
                        </oscar:oscarPropertiesCheck>

                        <li><a href="#"
                               onclick="popupPage(500,screen.width,'${pageContext.request.contextPath}/appointment/appointmentTypeAction.do'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.appointmentTypeList"/></a></li>

                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/adminnewgroup.jsp?submit=blank");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddGroupNoRecord"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/admindisplaymygroup.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/groupnoacl.jsp")'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnGroupNoAcl"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/groupPreferences.jsp")'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnGroupPreference"/></a></li>
                        <li><a href="#" onclick='popupPage(800, 700, "${pageContext.request.contextPath}/oscarPrevention/PreventionManager.jsp");return false;'
                               title="Customize prevention notifications."><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.preventionNotification.title"/></a></li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #Schedule Management END-->

        <!-- #FLOWSHEET & DOCUMENT MANAGEMENT -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.document,_admin.flowsheet" rights="r"
                           reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.flowsheetManagement"/></h3>
                <ul>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.flowsheet" rights="r"
                                       reverse="<%=false%>">
                        <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/manageFlowsheets.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.flowsheetManager"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.document" rights="r"
                                       reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/displayDocumentCategories.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DocumentCategories"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/displayDocumentDescriptionTemplate.jsp?setDefault=true");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DocumentDescriptionTemplate"/></a></li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #FLOWSHEET & DOCUMENT MANAGEMENT END -->


        <!-- #SYSTEM Management-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.SystemManagement"/></h3>
                <ul>

                    <li><a href="javascript:void(0);" onclick='popupPage(550,800, "${pageContext.request.contextPath}/lookupListManagerAction.do?method=manageSingle&listName=consultApptInst");return false;'>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.oscarEncounter.consult.appointmentIntructions"/></a>
                    </li>

                    <li><a href="javascript:void(0);" onclick='popupPage(550,800, "${pageContext.request.contextPath}/lookupListManagerAction.do?method=manage");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.lookUpLists"/></a></li>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(300,600, "${pageContext.request.contextPath}/admin/providerAddRole.jsp");return false;'>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.addRole"/></a></li>
                    </security:oscarSec>

                    <li><a href="#"
                           onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/providerPrivilege.jsp");return false;'>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.assignRightsObject"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/displayDocumentCategories.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DocumentCategories"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/displayDocumentDescriptionTemplate.jsp?setDefault=true");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DocumentDescriptionTemplate"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/ManageClinic.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.clinicAdmin"/></a></li>
                    <%
                        if (IsPropertiesOn.isMultisitesEnable()) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/ManageSites.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.sitesAdmin"/></a></li>
                    <%
                        }
                    %>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/oscarEncounter/oscarConsultationRequest/config/EditSpecialists.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.professionalSpecialistAdmin"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(400,450, "${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchCustomization.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnCustomize"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscar.admin.diseaseRegistryQuickList"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(250,450, "${pageContext.request.contextPath}/oscarEncounter/oscarMeasurements/Customization.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.btnCustomize"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.oscarMeasurements"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(200,300, "${pageContext.request.contextPath}/admin/resourcebaseurl.jsp");return false;'
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.baseURLSettingTitle"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnBaseURLSetting"/></a></li>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.messenger" rights="r" reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupOscarRx(600,900, "${pageContext.request.contextPath}/messenger/DisplayMessages.do?providerNo=<%=curProvider_no%>&amp;userName=<%=userfirstname%>%20<%=userlastname%>");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.messages"/></a></li>
                        <li><a href="#"
                               onclick='popupOscarRx(600,900, "${pageContext.request.contextPath}/messenger/config/MessengerAdmin.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnMessengerAdmin"/></a></li>

                    </security:oscarSec>

                    <li><a href="#" onclick='popupPage(800,1000, "${pageContext.request.contextPath}/admin/keygen/keyManager.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.keyPairGen"/></a></li>
                    <li><a href="#" onclick='popupPage(600,600, "${pageContext.request.contextPath}/FacilityManager.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageFacilities"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/oscarEncounter/oscarMeasurements/adminFlowsheet/NewFlowsheet.jsp");return false;'>Create
                        New Flowsheet</a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/manageFlowsheets.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.flowsheetManager"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/lotnraddrecordhtm.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.add_lot_nr.title"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/lotnrsearchrecordshtm.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotnrsearchrecordshtm.title"/></a></li>

                    <oscar:oscarPropertiesCheck property="LOGINTEST" value="yes">
                        <li><a href="#"
                               onclick='popupPage(800,1000,"${pageContext.request.contextPath}/admin/uploadEntryText.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.uploadEntryTxt"/></a>
                        </li>
                    </oscar:oscarPropertiesCheck>

                        <%--		 	<%--%>
                        <%--				if (oscar.oscarSecurity.CRHelper.isCRFrameworkEnabled())--%>
                        <%--						{--%>
                        <%--			%>--%>
                        <%--			<sec:oscarSec roleName="<%=roleName$%>"--%>
                        <%--				objectName="_admin.cookieRevolver" rights="r">--%>
                        <%--		--%>
                        <%--				<li>&nbsp; <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.titleFactorAuth"/>--%>
                        <%--				<ul>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/ip/show');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ipFilter"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/cert/?act=super');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.setCert"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/supercert');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.genCert"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/clear');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.clearCookie"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/quest/adminQuestions');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.adminSecQuestions"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/policyadmin/select');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.adminSecPolicies"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/banremover/show');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.removeBans"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/matrixadmin/show');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.genMatrixCards"/></a></li>--%>
                        <%--				</ul>--%>
                        <%--				</li>--%>
                        <%--			</sec:oscarSec>--%>
                        <%--			<%--%>
                        <%--				}--%>
                        <%--			%>           	--%>

                </ul>
            </div>
        </security:oscarSec>
        <!-- #SYSTEM Management END-->

        <!-- #SYSTEM REPORTS-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.SystemReports"/></h3>
                <ul>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.securityLogReport" rights="r">
                        <li><a href="#"
                               onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/logReport.jsp?keyword=admin");return false;'>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.securityLogReport"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin, _admin.traceability" rights="r">
                        <li><a href="#"
                               onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/traceReport.jsp?keyword=admin");return false;'>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.traceabilityReport"/></a></li>
                    </security:oscarSec>


                </ul>
            </div>
        </security:oscarSec>
        <!-- #SYSTEM REPORTS END-->


        <!-- #INTEGRATION-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.Integration"/></h3>
                <ul>

                    <li>&nbsp;<a href="#" onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/api/clients.jsp");return false;'>REST Clients</a></li>
                    <li><a href="#"
                           onclick="popupPage(900, 500, '<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewIntegratorProperties');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetIntegratorPreferences"/></a></li>
                    <li><a href="#"
                           onClick="popupPage(800, 1000, '${pageContext.request.contextPath}/admin/integratorPushStatus.jsp');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.integratorPush"/></a></li>

                    <li><a href="<%=request.getContextPath()%>/lab/CA/ALL/sendOruR01.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.sendOruR01"/></a></li>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/hospitalReportManager/hospitalReportManager.jsp");return false;'>Hospital
                        Report Manager (HRM) Status</a></li>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/hospitalReportManager/hrmPreferences.jsp");return false;'>Hospital Report
                        Manager (HRM) Preferences</a></li>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/hospitalReportManager/hrmShowMapping.jsp");return false;'>Hospital Report
                        Manager (HRM) Class Mappings</a></li>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/hospitalReportManager/hrmCategories.jsp");return false;'>Hospital Report
                        Manager (HRM) Categories</a></li>

                    <%
                        String olisKeystore = OscarProperties.getInstance().getProperty("olis_keystore", "");
                        if (olisKeystore.length() > 0) {
                    %>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/olis/Preferences.jsp");return false;'>OLIS Preferences</a></li>
                    <% } %>

                    <li><a href="javascript:void(0);" onclick="popupPage(550,800, '${pageContext.request.contextPath}/admin/updateDrugref.jsp');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.UpdateDrugref"/></a></li>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #INTEGRATION END -->

        <!-- #STATUS-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.Status"/></h3>
                <ul>
                    <% if (OscarProperties.getInstance().isFaxEnabled()) { %>
                    <li><a href="#" onclick='popupPage(600, 800, "${pageContext.request.contextPath}/admin/faxStatus.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.faxStatus.faxStatus"/></a></li>
                    <% } %>
                    <li><a href="#" onclick='popupPage(800, 800, "${pageContext.request.contextPath}/admin/oscarStatus.do");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.oscarStatus.oscarStatus"/></a></li>

                </ul>
            </div>
        </security:oscarSec>
        <!-- #STATUS END -->

        <!-- #Data Management -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.backup" rights="r" reverse="<%=false%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DataManagement"/></h3>
                <ul>
                    <li><a href="#"
                           onclick='popupPage(500,600, "${pageContext.request.contextPath}/admin/adminbackupdownload.jsp"); return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAdminBackupDownload"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/demographic/demographicExport.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DemoExport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/demographic/demographicImport.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DemoImport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/demographicmergerecord.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.mergeRec"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/updatedemographicprovider.jsp");return false;'><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnUpdatePatientProvider"/></a></li>

                </ul>
            </div>

        </security:oscarSec>
        <!-- #Data Management END-->


    </caisi:isModuleLoad>


    <hr style="color: black;"/>
    <div class="logoutBox">
        <%
            if (roleName$.equals("admin" + "," + curProvider_no)) {
        %><a
            href="${pageContext.request.contextPath}/logout.jsp">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnLogout"/>
    </a>&nbsp;<%
        }
    %>
    </div>


    </body>
</html>
