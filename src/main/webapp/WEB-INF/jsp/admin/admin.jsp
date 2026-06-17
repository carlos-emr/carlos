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
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.*");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<%
    String curProvider_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
%>

<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<%
    String country = request.getLocale().getCountry();
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:message key="admin.admin.page.title"/> Start Time
            : <%=CarlosProperties.getInstance().getStartTime()%>
        </title>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <oscar:customInterface section="admin"/>

        <script type="text/JavaScript">
            function onsub() {
                if (document.searchprovider.keyword.value == "") {
                    alert("<fmt:message key="global.msgInputKeyword"/>");
                    return false;
                } else return true;
                // do nothing at the moment
                // check input data in the future
            }

            function popupOscarRx(vheight, vwidth, varpage) { //open a new popup window
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "rx", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function postToPopup(formId, vheight, vwidth) {
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                window.open("", "groupno", windowprops);
                document.getElementById(formId).submit();
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
        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">

    </head>

    <body class="BodyStyle">

    <div class="title"><fmt:message key="admin.admin.page.title"/></div>

    <div class="logoutBox">
        <%
            if (roleName$.equals("admin" + "," + curProvider_no)) {
        %><a href="${pageContext.request.contextPath}/logoutPage">
        <fmt:message key="global.btnLogout"/>
        </a>&nbsp;<%
        }
    %>
    </div>

    <!-- #USER MANAGEMENT -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.torontoRfq,_admin.provider"
                       rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:message key="admin.admin.UserManagement"/></h3>
            <ul>
                <li><a href="${pageContext.request.contextPath}/admin/ViewProviderAddARecordHtm">
                    <fmt:message key="admin.admin.btnAddProvider"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/ViewProviderSearchRecordsHtm">
                    <fmt:message key="admin.admin.btnSearchProvider"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/ViewSecurityAddARecord">
                    <fmt:message key="admin.admin.btnAddLogin"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/admin/ViewSecuritySearchRecordsHtm">
                    <fmt:message key="admin.admin.btnSearchLogin"/>
                </a></li>

                <li><a href="#"
                       onclick='popupPage(500,700,"${pageContext.request.contextPath}/admin/ProviderRole");return false;'>
                    <fmt:message key="admin.admin.assignRole"/></a></li>

                <security:oscarSec roleName="<%=roleName$%>"
                                   objectName="_admin,_admin.unlockAccount" rights="r">
                    <li><a href="#"
                           onclick='popupPage(500,800,"${pageContext.request.contextPath}/admin/UnLock");return false;'>
                        <fmt:message key="admin.admin.unlockAcct"/></a></li>
                </security:oscarSec>
            </ul>
        </div>
    </security:oscarSec>

    <!-- #USER MANAGEMENT END -->

    <!-- #BILLING -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.invoices,_admin,_admin.billing" rights="r"
                       reverse="<%=false%>">
        <div class="adminBox">
            <h3>&nbsp;<fmt:message key="admin.admin.billing"/></h3>
            <ul>
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.billing" rights="r"
                                   reverse="<%=false%>">
                    <%
                        if (oscarVariables.getProperty("billregion", "").equals("BC")) {
                    %>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing" rights="w" reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/ON/ManageBillingform");return false;'><fmt:message key="admin.admin.ManageBillFrm"/></a></li>
                    </security:oscarSec>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/billing/CA/BC/billingAddCode");return false;'><fmt:message key="admin.admin.ManagePrivFrm"/></a></li>
                    <oscar:oscarPropertiesCheck property="BC_BILLING_CODE_MANAGEMENT"
                                                value="yes">
                        <li><a href="#"
                               onclick='popupPage(600,900,"${pageContext.request.contextPath}/billing/CA/BC/billingAddCode");return false;'><fmt:message key="admin.admin.ManageBillCodes"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,600,"${pageContext.request.contextPath}/billing/CA/BC/showServiceCodeAssocs");return false;'><fmt:message key="admin.admin.ManageServiceDiagnosticCodeAssoc"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,500,"${pageContext.request.contextPath}/billing/CA/BC/supServiceCodeAssocAction");return false;'><fmt:message key="admin.admin.ManageProcedureFeeCodeAssoc"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/AddReferralDoc");return false;'><fmt:message key="admin.admin.ManageReferralDoc"/></a></li>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="no"
                                                defaultVal="true">
                        <li><a href="#"
                               onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/ViewBillingSim");return false;'><fmt:message key="admin.admin.SimulateSubFile"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,720,"${pageContext.request.contextPath}/billing/CA/BC/ViewBillingTeleplanGroupReport");return false;'><fmt:message key="admin.admin.genTeleplanFile"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="yes">
                        <li><a href="#"
                               onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/BC/GenerateTeleplanFile");return false;'><fmt:message key="admin.admin.simulateSubFile2"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,720,"${pageContext.request.contextPath}/billing/CA/BC/SimulateTeleplanFile");return false;'><fmt:message key="admin.admin.genTeleplanFile2"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/BC/ManageTeleplan");return false;'><fmt:message key="admin.admin.manageTeleplan"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <oscar:oscarPropertiesCheck property="NEW_BC_TELEPLAN" value="no"
                                                defaultVal="true">
                        <li><a href="#"
                               onclick='popupPage(600,800,"${pageContext.request.contextPath}/billing/CA/BC/ViewBillingTA");return false;'><fmt:message key="admin.admin.uploadRemittance"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,800,"${pageContext.request.contextPath}/billing/CA/BC/ProcessRemittance");return false;'><fmt:message key="admin.admin.reconciliationReports"/></a></li>
                    <li><a href="#"
                           onclick='popUpBillStatus(375,425,"${pageContext.request.contextPath}/billing/CA/BC/ViewBillingAccountReports");return false;'><fmt:message key="admin.admin.AccountingRpts"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/BC/reprocessBill");return false;'><fmt:message key="admin.admin.editInvoices"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(200,300,"${pageContext.request.contextPath}/billing/CA/BC/ViewSettleBG");return false;'><fmt:message key="admin.admin.settlePaidClaims"/></a></li>

                    <%-- Addition of BC MSP Quick Billing by Dennis Warren - December 2011 --%>
                    <li>
                        <a href='javascript: popupPage( 500, 900, "${pageContext.request.contextPath}/quickBillingBC");'>
                            BC MSP Quick Billing
                        </a>
                    </li>

                    <%
                    } else if (oscarVariables.getProperty("billregion", "").equals("ON")) {
                    %>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing" rights="w" reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/ViewBenefitScheduleUpload");return false;'><fmt:message key="admin.admin.scheduleOfBenefits"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(300,600, "${pageContext.request.contextPath}/billing/CA/ON/AddEditServiceCode");return false;'><fmt:message key="admin.admin.manageBillingServiceCode"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(300,600, "${pageContext.request.contextPath}/billing/CA/ON/ViewBillingONEditPrivateCode");return false;'><fmt:message key="admin.admin.managePrivBillingCode"/></a></li>
                    </security:oscarSec>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/admin/manageCSSStyles");return false;'><fmt:message key="admin.admin.manageCodeStyles"/></a></li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing" rights="w" reverse="<%=false%>">
                    <li><a href="${pageContext.request.contextPath}/admin/GstControl"><fmt:message key="admin.admin.manageGSTControl"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/GstReport"><fmt:message key="admin.admin.gstReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/ManageBillingLocation");return false;'><fmt:message key="admin.admin.btnAddBillingLocation"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000, "${pageContext.request.contextPath}/billing/CA/ON/ManageBillingform");return false;'><fmt:message key="admin.admin.btnManageBillingForm"/></a></li>
                    </security:oscarSec>
                    <li><a href="#"
                           onclick='popupPage(700,700, "${pageContext.request.contextPath}/billing/CA/ON/ViewBillingOHIPsimulation");return false;'><fmt:message key="admin.admin.btnSimulationOHIPDiskette"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,720, "${pageContext.request.contextPath}/billing/CA/ON/ViewBillingOHIPreport");return false;'><fmt:message key="admin.admin.btnGenerateOHIPDiskette"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,640, "${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCorrection?billing_no=");return false;'><fmt:message key="admin.admin.btnBillingCorrection"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,820, "${pageContext.request.contextPath}/billing/CA/ON/BatchBill?service_code=all");return false;'><fmt:message key="admin.admin.btnBatchBilling"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,640, "${pageContext.request.contextPath}/billing/CA/ON/ViewInrReportINR?provider_no=all");return false;'><fmt:message key="admin.admin.btnINRBatchBilling"/></a></li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing" rights="w" reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(600,900, "${pageContext.request.contextPath}/billing/CA/ON/BillingONUpload");return false;'><fmt:message key="admin.admin.uploadMOHFile"/></a></li>
                    <% if (CarlosProperties.getInstance().isPropertyActive("moh_file_management_enabled")) { %>
                    <li><a href="#" onclick='popupPage(600,900, "${pageContext.request.contextPath}/billing/CA/ON/moveMOHFiles");return false;'><fmt:message key="admin.admin.viewMOHFiles"/></a></li>
                    <% } %>
                    </security:oscarSec>
                    <li><a href="#"
                           onclick='popupPage(600,900, "${pageContext.request.contextPath}<%= oscarVariables.getProperty("RA_FORWORD", "/billing/CA/ON/ViewGenRA") %>");return false;'><fmt:message key="admin.admin.btnBillingReconciliation"/></a></li>
                    <!-- li><a href="#" onclick ='popupPage(600,1000,"${pageContext.request.contextPath}/billing/CA/ON/ViewBillingOBECEA");return false;'><fmt:message key="admin.admin.btnEDTBillingReportGenerator"/></a></li-->
                    <li>
                        <a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/mcedt/mcedt");return false;'><fmt:message key="admin.admin.mcedt"/></a>
                    </li>
                    </li>
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/billing/CA/ON/ViewBillStatus");return false;'><fmt:message key="admin.admin.invoiceRpts"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(700,1000,"${pageContext.request.contextPath}/billing/CA/ON/endYearStatement");return false;'><fmt:message key="admin.admin.endYearStatement"/></a></li>
                    <%if (CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
                    <li>
                        <a href='#' onclick='popupPage(300,750,"${pageContext.request.contextPath}/admin/ViewClinicNbrManage");return false;'>Manage Clinic NBR Codes</a>
                    </li>
                    <%}%>
                    <li>
                        <a href='#' onclick='popupPage(300,750,"${pageContext.request.contextPath}/billing/CA/ON/managePaymentType");return false;'><fmt:message key="admin.admin.managePaymentType"/></a>
                    </li>

                    <%
                        }
                    %>
                </security:oscarSec>

                <% if (oscarVariables.getProperty("billregion", "").equals("ON")) { %>
                <li><a href="#" onclick="popupPage(800,1000,'/billing/CA/ON/BillingONPayment');return false;"><fmt:message key="admin.admin.paymentReceived"/></a></li>
                <% } %>
            </ul>
        </div>
    </security:oscarSec>
    <!-- #BILLING END-->

    <!-- #LABS/INBOX -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin," rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:message key="admin.admin.LabsInbox"/></h3>
            <ul>
                <li><a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/lab/CA/ALL/insideLabUpload");return false;'><fmt:message key="admin.admin.hl7LabUpload"/></a></li>
                <oscar:oscarPropertiesCheck property="OLD_LAB_UPLOAD" value="yes"
                                            defaultVal="false">
                    <li><a href="#"
                           onclick='popupPage(800,1000,"${pageContext.request.contextPath}/lab/labUpload");return false;'><fmt:message key="admin.admin.oldLabUpload"/></a></li>
                </oscar:oscarPropertiesCheck>
                <security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="w" reverse="<%=false%>">
                <li><a href="#" onclick='popupPage(800,1000,"${pageContext.request.contextPath}/admin/labForwardingRules");return false;'><fmt:message key="admin.admin.labFwdRules"/></a></li>
                </security:oscarSec>
                <li><a href="javascript:void(0);" onclick='popupPage(550,800,"${pageContext.request.contextPath}/admin/ViewAddQueue");return false;'><fmt:message key="admin.admin.AddNewQueue"/></a></li>
            </ul>
        </div>

    </security:oscarSec>
    <!-- #LABS/INBOX END -->

    <!--  #FORMS/EFORMS -->
    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r" reverse="<%=false%>">

        <div class="adminBox">
            <h3>&nbsp;<fmt:message key="admin.admin.FormsEforms"/></h3>
            <ul>
                <li><a href="#"
                       onclick='popupPage(500,1000,"${pageContext.request.contextPath}/form/setupSelect");return false;'><fmt:message key="admin.admin.btnSelectForm"/></a></li>
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="w" reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(500,1000,"${pageContext.request.contextPath}/form/formXmlUpload");return false;'><fmt:message key="admin.admin.btnImportFormData"/></a></li>
                </security:oscarSec>
                <li><a href="${pageContext.request.contextPath}/eform/efmformmanager">
                    <fmt:message key="admin.admin.btnUploadForm"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/eform/efmimagemanager">
                    <fmt:message key="admin.admin.btnUploadImage"/>
                </a></li>
                <li><a href="${pageContext.request.contextPath}/eform/efmmanageformgroups">
                    <fmt:message key="admin.admin.frmGroups"/>
                </a></li>


                <li><a href="${pageContext.request.contextPath}/eform/efmmanageindependent">
                    <fmt:message key="admin.admin.frmIndependent"/>
                </a></li>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fieldnote" rights="r"
                                   reverse="<%=false%>">
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/eform/fieldNoteReport/fieldnotereport");return false;'>
                        <fmt:message key="admin.admin.fieldNoteReport"/></a>
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
                <h3>&nbsp;<fmt:message key="admin.admin.oscarReport"/></h3>
                <ul>
                    <%
                        session.setAttribute("reportdownload", "/usr/local/tomcat/webapps/oscar_sfhc/oscarReport/download/");
                    %>

                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/RptByExample");return false;'><fmt:message key="admin.admin.btnQueryByExample"/></a></li>

                    <li>
                        <a href="${pageContext.request.contextPath}/oscarReport/reportByTemplate/ViewHomePage">
                            <fmt:message key="admin.admin.rptbyTemplate"/>
                        </a>
                    </li>
                    <li><a href="#"
                           onclick='postToPopup("ageSexForm",600,900);return false;'><fmt:message key="admin.admin.btnAgeSexReport"/></a>
                        <form id="ageSexForm" method="post" action="${pageContext.request.contextPath}/oscarReport/DbReportAgeSex" target="groupno" style="display:none"></form>
                    </li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/ViewOscarReportVisitControl");return false;'><fmt:message key="admin.admin.btnVisitReport"/></a></li>
                        <%-- This links doesnt make sense on Brazil. Hide then --%>

                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/ViewOscarReportCatchment");return false;'><fmt:message key="admin.admin.btnPCNCatchmentReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/FluBilling?orderby=");return false;'><fmt:message key="admin.admin.btnFluBillingReport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(600,1000,"${pageContext.request.contextPath}/oscarReport/obec");return false;'><fmt:message key="admin.admin.btnOvernightChecking"/></a></li>


                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/oscarReport/ViewPatientlist")'><fmt:message key="admin.admin.exportPatientbyAppt"/></a></li>

                    <li><a href="${pageContext.request.contextPath}/oscarReport/ViewProviderServiceReportForm"><fmt:message key="admin.admin.providerServiceRpt"/></a></li>
                    <caisi:isModuleLoad moduleName="caisi">
                        <li><a href="${pageContext.request.contextPath}/PopulationReport"><fmt:message key="admin.admin.popRpt"/></a></li>
                    </caisi:isModuleLoad>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/ViewCds4ReportForm"><fmt:message key="admin.admin.cdsRpt"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/oscarReport/ViewMisReportForm"><fmt:message key="admin.admin.misRpt"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/admin/ViewUsageReport"><fmt:message key="admin.admin.usageRpt"/></a></li>
                    <oscar:oscarPropertiesCheck property="SERVERLOGGING" value="yes">
                        <li><a href="#"
                               onclick='popupPage(600,900, "${pageContext.request.contextPath}/admin/ViewOscarLogging")'><fmt:message key="admin.admin.serverLog"/></a></li>
                    </oscar:oscarPropertiesCheck>
                    <li><a href="#"
                           onclick='popupPage(600,900,"${pageContext.request.contextPath}/report/DxresearchReport")'><fmt:message key="admin.admin.diseaseRegister"/></a></li>
                    <%
                        if (oscarVariables.getProperty("billregion", "").equals("ON")) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(660,1000, "${pageContext.request.contextPath}/report/ViewReportonbilledphcp");return false;'><fmt:message key="admin.admin.PHCP"/></a>
                        <span style="font-size: x-small;"> (Setting: <a href="#"
                                                                        onclick='popupPage(660,1000, "${pageContext.request.contextPath}/report/ViewReportonbilledvisitprovider");return false;'><fmt:message key="admin.admin.provider"/></a>,
			        ) </span></li>
                    <%
                        }

                    %>
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
                <h3>&nbsp;<fmt:message key="admin.admin.eChart"/></h3>
                <ul>

                    <security:oscarSec roleName="<%=roleName$%>" objectName="_newCasemgmt.templates" rights="w"
                                       reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/ProviderTemplate");return false;'>
                            <fmt:message key="admin.admin.btnInsertTemplate"/></a>
                        </li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
    </caisi:isModuleLoad>
    <!-- #ECHART END-->


    <caisi:isModuleLoad moduleName="caisi">
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.caisi" rights="r" reverse="<%=false%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.caisi"/></h3>
                <ul>
                    <li><a href="${pageContext.request.contextPath}/SystemMessage">
                        <fmt:message key="admin.admin.systemMessage"/>
                    </a></li>
                    <li><a href="${pageContext.request.contextPath}/FacilityMessage?"><fmt:message key="admin.admin.FacilitiesMsgs"/></a></li>
                    <li><a href="${pageContext.request.contextPath}/issueAdmin?method=list">
                        <fmt:message key="admin.admin.issueEditor"/>
                    </a></li>
                    <li><a href="${pageContext.request.contextPath}/DefaultEncounterIssue">
                        <fmt:message key="admin.admin.defaultEncounterIssue"/>
                    </a></li>
                </ul>
            </div>
        </security:oscarSec>

        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.caisi" rights="r" reverse="<%=true%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.caisi"/></h3>
                <ul>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.systemMessage" rights="r" reverse="<%=false%>">
                        <li><a href="${pageContext.request.contextPath}/SystemMessage">
                            <fmt:message key="admin.admin.systemMessage"/>
                        </a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.facilityMessage" rights="r" reverse="<%=false%>">
                        <li><a href="${pageContext.request.contextPath}/FacilityMessage?"><fmt:message key="admin.admin.FacilitiesMsgs"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.lookupFieldEditor" rights="r">
                        <li><a href="${pageContext.request.contextPath}/lookupListManagerAction"> <fmt:message key="admin.admin.LookupFieldEditor"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin.issueEditor" rights="r">
                        <li><a href="${pageContext.request.contextPath}/issueAdmin?method=list">
                            <fmt:message key="admin.admin.issueEditor"/>
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
                <h3>&nbsp;<fmt:message key="admin.admin.ScheduleManagement"/></h3>
                <ul>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/schedule/TemplateSetting");return false;'
                           title="<fmt:message key="admin.admin.scheduleSettingTitle"/>"><fmt:message key="admin.admin.scheduleSetting"/></a></li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.schedule.curprovider_only"
                                       rights="r" reverse="<%=true%>">
                        <oscar:oscarPropertiesCheck property="ENABLE_EDIT_APPT_STATUS"
                                                    value="yes">
                            <li><a href="#"
                                   onclick="popupPage(500,600,'${pageContext.request.contextPath}/appointment/apptStatusSetting');return false;"
                                   title="<fmt:message key="admin.admin.scheduleSettingTitle"/>"><fmt:message key="admin.admin.appointmentStatusSetting"/></a></li>
                        </oscar:oscarPropertiesCheck>

                        <li><a href="#"
                               onclick="popupPage(500,screen.width,'${pageContext.request.contextPath}/appointment/appointmentTypeAction'); return false;"><fmt:message key="admin.admin.appointmentTypeList"/></a></li>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.schedule.groupCreate"
                                           rights="w" reverse="<%=false%>">
                            <li><a href="#"
                                   onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/AdminNewGroup?submit=blank");return false;'><fmt:message key="admin.admin.btnAddGroupNoRecord"/></a></li>
                        </security:oscarSec>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/ViewAdminDisplayMyGroup");return false;'><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/GroupNoAcl")'><fmt:message key="admin.admin.btnGroupNoAcl"/></a></li>
                        <li><a href="#"
                               onclick='popupPage(360,600, "${pageContext.request.contextPath}/admin/GroupPreference")'><fmt:message key="admin.admin.btnGroupPreference"/></a></li>
                        <li><a href="#" onclick='popupPage(800, 700, "${pageContext.request.contextPath}/prevention/ViewPreventionManager");return false;'
                               title="Customize prevention notifications."><fmt:message key="admin.admin.preventionNotification.title"/></a></li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #Schedule Management END-->

        <!-- #FLOWSHEET & DOCUMENT MANAGEMENT -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.document,_admin.flowsheet" rights="r"
                           reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.flowsheetManagement"/></h3>
                <ul>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.flowsheet" rights="r"
                                       reverse="<%=false%>">
                        <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/ManageFlowsheets");return false;'><fmt:message key="admin.admin.flowsheetManager"/></a></li>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.document" rights="r"
                                       reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/DisplayDocumentDescriptionTemplate?setDefault=true");return false;'><fmt:message key="admin.admin.DocumentDescriptionTemplate"/></a></li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #FLOWSHEET & DOCUMENT MANAGEMENT END -->


        <!-- #SYSTEM Management-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.SystemManagement"/></h3>
                <ul>

                    <li><a href="javascript:void(0);" onclick='popupPage(550,800, "${pageContext.request.contextPath}/lookupListManagerAction?method=manageSingle&listName=consultApptInst");return false;'>
                        <fmt:message key="admin.admin.encounter.consult.appointmentIntructions"/></a>
                    </li>

                    <li><a href="javascript:void(0);" onclick='popupPage(550,800, "${pageContext.request.contextPath}/lookupListManagerAction?method=manage");return false;'><fmt:message key="admin.admin.lookUpLists"/></a></li>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupPage(300,600, "${pageContext.request.contextPath}/admin/ProviderAddRole");return false;'>
                            <fmt:message key="admin.admin.addRole"/></a></li>
                    </security:oscarSec>

                    <li><a href="#"
                           onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/ProviderPrivilege");return false;'>
                        <fmt:message key="admin.admin.assignRightsObject"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/DisplayDocumentDescriptionTemplate?setDefault=true");return false;'><fmt:message key="admin.admin.DocumentDescriptionTemplate"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/ManageClinic");return false;'><fmt:message key="admin.admin.clinicAdmin"/></a></li>
                    <%
                        if (IsPropertiesOn.isMultisitesEnable()) {
                    %>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/ManageSites");return false;'><fmt:message key="admin.admin.sitesAdmin"/></a></li>
                    <%
                        }
                    %>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/encounter/oscarConsultationRequest/config/ViewEditSpecialists");return false;'><fmt:message key="admin.admin.professionalSpecialistAdmin"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(400,450, "${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/ViewDxResearchCustomization");return false;'><fmt:message key="encounter.Index.btnCustomize"/> <fmt:message key="oscar.admin.diseaseRegistryQuickList"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(250,450, "${pageContext.request.contextPath}/encounter/oscarMeasurements/ViewCustomization");return false;'><fmt:message key="encounter.Index.btnCustomize"/> <fmt:message key="admin.admin.oscarMeasurements"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(200,300, "${pageContext.request.contextPath}/admin/ResourceBaseUrl");return false;'
                           title='<fmt:message key="admin.admin.baseURLSettingTitle"/>'><fmt:message key="admin.admin.btnBaseURLSetting"/></a></li>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.messenger" rights="r" reverse="<%=false%>">
                        <li><a href="#"
                               onclick='popupOscarRx(600,1024, "${pageContext.request.contextPath}/messenger/DisplayMessages?providerNo=<%=curProvider_no%>");return false;'><fmt:message key="admin.admin.messages"/></a></li>
                        <li><a href="#"
                               onclick='popupOscarRx(600,900, "${pageContext.request.contextPath}/messenger");return false;'><fmt:message key="admin.admin.btnMessengerAdmin"/></a></li>

                    </security:oscarSec>

                    <li><a href="#" onclick='popupPage(800,1000, "${pageContext.request.contextPath}/admin/ViewKeygenKeyManager");return false;'><fmt:message key="admin.admin.keyPairGen"/></a></li>
                    <li><a href="#" onclick='popupPage(600,600, "${pageContext.request.contextPath}/FacilityManager");return false;'><fmt:message key="admin.admin.manageFacilities"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/encounter/oscarMeasurements/adminFlowsheet/ViewNewFlowsheet");return false;'>Create
                        New Flowsheet</a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/ManageFlowsheets");return false;'><fmt:message key="admin.admin.flowsheetManager"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/ViewLotNrAddRecordHtm");return false;'><fmt:message key="admin.admin.add_lot_nr.title"/></a></li>
                    <li><a href="#" onclick='popupPage(800, 1000, "${pageContext.request.contextPath}/admin/ViewLotNrSearchRecordsHtm");return false;'><fmt:message key="admin.lotnrsearchrecordshtm.title"/></a></li>

                    <oscar:oscarPropertiesCheck property="LOGINTEST" value="yes">
                        <li><a href="#"
                               onclick='popupPage(800,1000,"${pageContext.request.contextPath}/admin/uploadEntryText");return false;'><fmt:message key="admin.admin.uploadEntryTxt"/></a>
                        </li>
                    </oscar:oscarPropertiesCheck>

                        <%--		 	<%--%>
                        <%--				if (oscar.oscarSecurity.CRHelper.isCRFrameworkEnabled())--%>
                        <%--						{--%>
                        <%--			%>--%>
                        <%--			<sec:oscarSec roleName="<%=roleName$%>"--%>
                        <%--				objectName="_admin.cookieRevolver" rights="r">--%>
                        <%--		--%>
                        <%--				<li>&nbsp; <fmt:message key="admin.admin.titleFactorAuth"/>--%>
                        <%--				<ul>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/ip/show');return false;"><fmt:message key="admin.admin.ipFilter"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/cert/?act=super');return false;"><fmt:message key="admin.admin.setCert"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/supercert');return false;"><fmt:message key="admin.admin.genCert"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/clear');return false;"><fmt:message key="admin.admin.clearCookie"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/quest/adminQuestions');return false;"><fmt:message key="admin.admin.adminSecQuestions"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/policyadmin/select');return false;"><fmt:message key="admin.admin.adminSecPolicies"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/banremover/show');return false;"><fmt:message key="admin.admin.removeBans"/></a></li>--%>
                        <%--					<li><a href="#"--%>
                        <%--						onclick="popupPage(500,700,'<%= request.getContextPath() %>/gatekeeper/matrixadmin/show');return false;"><fmt:message key="admin.admin.genMatrixCards"/></a></li>--%>
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
                <h3>&nbsp;<fmt:message key="admin.admin.SystemReports"/></h3>
                <ul>

                    <security:oscarSec roleName="<%=roleName$%>"
                                       objectName="_admin,_admin.securityLogReport" rights="r">
                        <li><a href="#"
                               onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/LogReport?keyword=admin");return false;'>
                            <fmt:message key="admin.admin.securityLogReport"/></a></li>
                    </security:oscarSec>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #SYSTEM REPORTS END-->


        <!-- #INTEGRATION-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.Integration"/></h3>
                <ul>

                    <li>&nbsp;<a href="#" onclick='popupPage(500,800, "${pageContext.request.contextPath}/admin/ViewApiClients");return false;'>REST Clients</a></li>
                    <li><a href="#" onclick='popupPage(400, 400, "${pageContext.request.contextPath}/hospitalReportManager/Statement");return false;'>Hospital
                        Report Manager (HRM) Status</a></li>

                    <li><a href="javascript:void(0);" onclick="popupPage(550,800, '${pageContext.request.contextPath}/admin/ViewUpdateDrugref');return false;"><fmt:message key="admin.admin.UpdateDrugref"/></a></li>
                </ul>
            </div>
        </security:oscarSec>
        <!-- #INTEGRATION END -->

        <!-- #STATUS-->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=false%>">
            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.Status"/></h3>
                <ul>
                    <% if (CarlosProperties.getInstance().isFaxEnabled()) { %>
                    <li><a href="#" onclick='popupPage(600, 800, "${pageContext.request.contextPath}/admin/faxStatus");return false;'><fmt:message key="admin.faxStatus.faxStatus"/></a></li>
                    <% } %>

                </ul>
            </div>
        </security:oscarSec>
        <!-- #STATUS END -->

        <!-- #Data Management -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.backup" rights="r" reverse="<%=false%>">

            <div class="adminBox">
                <h3>&nbsp;<fmt:message key="admin.admin.DataManagement"/></h3>
                <ul>
                    <li><a href="#"
                           onclick='popupPage(500,600, "${pageContext.request.contextPath}/admin/ViewAdminBackupDownload"); return false;'><fmt:message key="admin.admin.btnAdminBackupDownload"/></a></li>

                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/demographic/DemographicExport");return false;'><fmt:message key="admin.admin.DemoExport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/form/importUpload");return false;'><fmt:message key="admin.admin.DemoImport"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/DemographicMergeRecord");return false;'><fmt:message key="admin.admin.mergeRec"/></a></li>
                    <li><a href="#"
                           onclick='popupPage(550,800, "${pageContext.request.contextPath}/admin/UpdateDemographicProvider");return false;'><fmt:message key="admin.admin.btnUpdatePatientProvider"/></a></li>

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
            href="${pageContext.request.contextPath}/logoutPage">
        <fmt:message key="global.btnLogout"/>
    </a>&nbsp;<%
        }
    %>
    </div>


    </body>
</html>
