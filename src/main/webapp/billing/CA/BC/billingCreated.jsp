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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%@ page import="io.github.carlos_emr.carlos.billing.ca.bc.data.*,io.github.carlos_emr.carlos.billing.ca.bc.pageUtil.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingreferralDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingBillingManager" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.WCBForm" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%
    BillingreferralDao billingReferralDao = (BillingreferralDao) SpringUtils.getBean(BillingreferralDao.class);
%>
<%

    String color = "", colorflag = "";
    BillingSessionBean bean = (BillingSessionBean) pageContext.findAttribute("billingSessionBean");
    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), bean.getPatientNo());

    ArrayList billItem = bean.getBillItem();
    BillingFormData billform = new BillingFormData();


%>

<!DOCTYPE HTML >
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.bc.title"/></title>

    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/css/fontawesome-all.min.css"/>
    <link rel="stylesheet" type="text/css" media="all"
          href="${pageContext.servletContext.contextPath}/css/bootstrap-datetimepicker-standalone.css"/>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript"
            src="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>

    <script language="JavaScript">
        //<!--

        function setfocus() {
            //document.serviceform.xml_diagnostic_code.focus();
            //document.serviceform.xml_diagnostic_code.select();
        }

        function RecordAttachments(Files, File0, File1, File2) {
            window.document.serviceform.elements["File0Data"].value = File0;
            window.document.serviceform.elements["File1Data"].value = File1;
            window.document.serviceform.elements["File2Data"].value = File2;
            window.document.all.Atts.innerText = Files;
        }

        var remote = null;

        function rs(n, u, w, h, x) {
            args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
            remote = window.open(u, n, args);
            if (remote != null) {
                if (remote.opener == null)
                    remote.opener = self;
            }
            if (x == 1) {
                return remote;
            }
        }


        var awnd = null;

        function ScriptAttach() {


            t0 = escape(document.serviceform.xml_diagnostic_detail1.value);
            t1 = escape(document.serviceform.xml_diagnostic_detail2.value);
            t2 = escape(document.serviceform.xml_diagnostic_detail3.value);
            awnd = rs('att', '/billingDigSearch.jsp?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=', 600, 600, 1);
            awnd.focus();


        }


        function OtherScriptAttach() {
            t0 = escape(document.serviceform.xml_other1.value);
            t1 = escape(document.serviceform.xml_other2.value);
            t2 = escape(document.serviceform.xml_other3.value);
            // f1 = document.serviceform.xml_dig_search1.value;
            // f2 = escape(document.serviceform.elements["File2Data"].value);
            // fname = escape(document.Compose.elements["FName"].value);
            awnd = rs('att', 'billingCodeSearch.jsp?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=', 600, 600, 1);
            awnd.focus();
        }

        function ResearchScriptAttach() {
            t0 = escape(document.serviceform.xml_research1.value);
            t1 = escape(document.serviceform.xml_research2.value);
            t2 = escape(document.serviceform.xml_research3.value);
            // f1 = document.serviceform.xml_dig_search1.value;
            // f2 = escape(document.serviceform.elements["File2Data"].value);
            // fname = escape(document.Compose.elements["FName"].value);
            awnd = rs('att', 'billingResearchCodeSearch.jsp?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=', 600, 600, 1);
            awnd.focus();
        }


        function ResearchScriptAttach() {
            t0 = escape(document.serviceform.xml_referral1.value);
            t1 = escape(document.serviceform.xml_referral2.value);

            awnd = rs('att', 'billingReferralCodeSearch.jsp?name=' + t0 + '&name1=' + t1 + '&search=', 600, 600, 1);
            awnd.focus();
        }

        function POP(n, h, v) {
            window.open(n, 'OSCAR', 'toolbar=no,location=no,directories=no,status=yes,menubar=no,resizable=yes,copyhistory=no,scrollbars=yes,width=' + h + ',height=' + v + ',top=100,left=200');
        }

        //-->
    </script>
    <script language="JavaScript">

        //<!--
        function reloadPage(init) {  //reloads the window if Nav4 resized
            if (init == true) with (navigator) {
                if ((appName == "Netscape") && (parseInt(appVersion) == 4)) {
                    document.pgW = innerWidth;
                    document.pgH = innerHeight;
                    onresize = reloadPage;
                }
            }
            else if (innerWidth != document.pgW || innerHeight != document.pgH) location.reload();
        }

        reloadPage(true);

        function findObj(n, d) { //v4.0
            var p, i, x;
            if (!d) d = document;
            if ((p = n.indexOf("?")) > 0 && parent.frames.length) {
                d = parent.frames[n.substring(p + 1)].document;
                n = n.substring(0, p);
            }
            if (!(x = d[n]) && d.all) x = d.all[n];
            for (i = 0; !x && i < d.forms.length; i++) x = d.forms[i][n];
            for (i = 0; !x && d.layers && i < d.layers.length; i++) x = findObj(n, d.layers[i].document);
            if (!x && document.getElementById) x = document.getElementById(n);
            return x;
        }

        function showHideLayers() { //v3.0
            var i, p, v, obj, args = showHideLayers.arguments;
            for (i = 0; i < (args.length - 2); i += 3) if ((obj = findObj(args[i])) != null) {
                v = args[i + 2];
                if (obj.style) {
                    obj = obj.style;
                    v = (v == 'show') ? 'visible' : (v = 'hide') ? 'hidden' : v;
                }
                obj.visibility = v;
            }
        }

        //-->
    </script>

    <style type="text/css">

        :root * {
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif, 'Glyphicons Halflings';
            font-size: 12px;
            line-height: 1 !important;
        }

        .button-bar {
            margin-bottom: 15px;
        }

        tr.table-heading td {
            font-weight: bold;
            background-color: #EAEAFF !important;
        }

        table tr th {
            text-align: right;
        }

        table {
            border-collapse: collapse;
            margin: 5px 0 0 0;
            width: 100%;
        }

        table tr td {
            vertical-align: top;
        }

        .wrapper {
            margin: auto 15px
        }

        #oscarBillingHeader {
            width: 100%;
            border-collapse: collapse;
            margin-top: .5%;
        }

        table#oscarBillingHeader tr td {
            padding: 1px 5px;
            background-color: #F3F3F3;
            vertical-align: middle;
        }

        #oscarBillingHeader #oscarBillingHeaderLeftColumn {
            width: 19.5% !important;
            background-color: white;
            padding: 0px;
            padding-right: .5% !important;
            width: 20%;
        }

        #oscarBillingHeader #oscarBillingHeaderLeftColumn h1 {
            margin: 0px;
            padding: 7px !important;
            display: block;
            font-size: large !important;
            background-color: black;
            color: white;
            font-weight: bold;
        }

        #oscarBillingHeaderRightColumn {
            vertical-align: top !important;
            text-align: right;
            padding-top: 3px !important;
            padding-right: 3px !important;
        }

        span.HelpAboutLogout a {
            font-size: x-small;
            color: black;
            float: right;
            padding: 0 3px;
        }

    </style>

</head>

<body onLoad="setfocus();showHideLayers('Layer1','','hide')">

<div class="wrapper">
    <form class="d-flex flex-wrap align-items-center gap-2" action="${pageContext.request.contextPath}/billing/CA/BC/SaveBilling.do" method="post">

        <div id="page-header">
            <table id="oscarBillingHeader">
                <tr>
                    <td id="oscarBillingHeaderLeftColumn"><h1><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.bc.title"/></h1></td>

                    <td id="oscarBillingHeaderCenterColumn">
                        Billing Summary
                    </td>

                </tr>
            </table>
        </div>
        <div class="container-fluid">
            <table>
                <tr>
                    <td>
                        <table class="table table-sm table-striped">
                            <tr class="table-heading">
                                <td colspan="6">Patient Information</td>
                            </tr>
                            <tr>
                                <th>Patient Name:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientName())%>
                                </td>
                                <th>Patient PHN:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientPHN())%>
                                </td>
                                <th>Health Card Type:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientHCType())%>
                                </td>
                            </tr>
                            <tr>
                                <th>Patient DoB:</th>
                                <td><%=bean.getPatientDoB()%>
                                </td>
                                <th>Patient Age:</th>
                                <td><%=bean.getPatientAge()%>
                                </td>
                                <th>Patient Sex:</th>
                                <td><%=bean.getPatientSex()%>
                                </td>
                            </tr>
                            <tr>
                                <th>Patient Address:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientAddress1())%>
                                </td>
                                <th>City:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientAddress2())%>
                                </td>
                                <th>Postal:</th>
                                <td><%=Encode.forHtmlContent(bean.getPatientPostal())%>
                                </td>
                            </tr>

                        </table>

                        <table class="table table-sm table-striped">
                            <tr class="table-heading">
                                <td colspan="8">Billing Information</td>
                            </tr>
                            <tr>
                                <th>Billing Type:</th>
                                <td><%=bean.getBillingType()%>
                                </td>
                                <th>Service Location:</th>
                                <td><%=bean.getVisitType()%>
                                </td>
                                <th>Clarification Code:</th>
                                <td><%=bean.getVisitLocation()%>
                                </td>
                                <th>Payment Method:</th>
                                <td><%=bean.getPaymentTypeName()%>
                                </td>
                            </tr>
                            <tr>
                                <th>Service Date:</th>
                                <td><%=bean.getServiceDate()%>
                                </td>
                                <th>Service To Date:</th>
                                <td><%=bean.getService_to_date()%>
                                </td>
                                <th>After Hours:</th>
                                <td><%=getAHDisplay(bean.getAfterHours())%>
                                </td>
                                <th>Time Call:</th>
                                <td><%=bean.getTimeCall()%>
                                </td>
                            </tr>
                            <tr>
                                <th>Start:</th>
                                <td><%=bean.getStartTime()%>
                                </td>
                                <th>End:</th>
                                <td><%=bean.getEndTime()%>
                                </td>
                                <th>Billing Provider:</th>
                                <td><%=Encode.forHtmlContent(billform.getProviderName(bean.getBillingProvider()))%>
                                </td>
                                <th>Appointment Provider:</th>
                                <td><%=Encode.forHtmlContent(billform.getProviderName(bean.getApptProviderNo()))%>
                                </td>
                            </tr>
                            <tr>
                                <th>Creator:</th>
                                <td><%=Encode.forHtmlContent(billform.getProviderName(bean.getCreator()))%>
                                </td>
                                <th>Dependent:</th>
                                <td><%=Encode.forHtmlContent(bean.getDependent())%>
                                </td>
                                <th>Sub Code:</th>
                                <td><%=bean.getSubmissionCode()%>
                                </td>
                                <th>&nbsp;</th>
                                <td>&nbsp;</td>
                            </tr>
                            <tr>
                                <th>Short Note:</th>
                                <td colspan="3"><%=Encode.forHtmlContent(bean.getShortClaimNote())%>
                                </td>
                                <th>ICBC Claim #:</th>
                                <td colspan="3"><%=bean.getIcbc_claim_no()%>
                                </td>
                            </tr>
                            <tr>
                                <th><%=getReferralString(bean.getReferType1())%>
                                </th>
                                <td colspan="3"><%=Encode.forHtmlContent(billingReferralDao.getReferralDocName(bean.getReferral1())) %> <%=addBrackets(bean.getReferral1())%>
                                </td>
                                <th><%=getReferralString(bean.getReferType2())%>
                                </th>
                                <td colspan="3"><%=Encode.forHtmlContent(billingReferralDao.getReferralDocName(bean.getReferral2())) %> <%=addBrackets(bean.getReferral2())%>
                                </td>
                            </tr>

                        </table>

                        <table>
                            <tr>
                                <td>
                                    <table class="table table-sm table-striped">
                                        <tr class="table-heading">
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.code"/></td>
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.desc"/></td>
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.unit"/></td>
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.fee"/></td>
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.service.total"/></td>
                                        </tr>
                                        <% for (int i = 0; i < billItem.size(); i++) {
                                            BillingBillingManager.BillingItem bi = (BillingBillingManager.BillingItem) billItem.get(i);
                                        %>
                                        <tr>
                                            <td><%=bi.getServiceCode()%>
                                            </td>
                                            <td><%=bi.getDescription()%>
                                            </td>
                                            <td><%=bi.getUnit()%>
                                            </td>
                                            <td><input type="text" class="form-control"
                                                       name="dispPrice+<%=bi.getServiceCode()%>"
                                                       value="<%=bi.getDispPrice()%>"/></td>
                                            <td><%=bi.getDispLineTotal()%>
                                            </td>
                                        </tr>
                                        <% } %>
                                        <tr>
                                            <th colspan="4">Total:</th>
                                            <td><%=bean.getGrandtotal()%>
                                            </td>
                                        </tr>

                                    </table>
                                </td>
                                <td>

                                    <table class="table table-sm table-striped">
                                        <tr class="table-heading">
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.diagnostic.code"/></td>
                                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.diagnostic.desc"/></td>
                                        </tr>
                                        <c:if test="${ not empty billingSessionBean.dx1 }">
                                            <tr>
                                                <td><%=bean.getDx1()%>
                                                </td>
                                                <td><%=billform.getDiagDesc(bean.getDx1(), bean.getBillRegion())%>
                                                </td>
                                            </tr>
                                        </c:if>
                                        <c:if test="${ not empty billingSessionBean.dx2 }">
                                            <tr>
                                                <td><%=bean.getDx2()%>
                                                </td>
                                                <td><%=billform.getDiagDesc(bean.getDx2(), bean.getBillRegion())%>
                                                </td>
                                            </tr>
                                        </c:if>
                                        <c:if test="${ not empty billingSessionBean.dx3 }">
                                            <tr>
                                                <td><%=bean.getDx3()%>
                                                </td>
                                                <td><%=billform.getDiagDesc(bean.getDx3(), bean.getBillRegion())%>
                                                </td>
                                            </tr>
                                        </c:if>
                                    </table>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <table class="table table-sm table-striped">
                                        <tr class="table-heading">
                                            <td>notes</td>
                                        </tr>
                                        <tr>
                                            <td colspan="4"><%= Encode.forHtmlContent(bean.getNotes()) %>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                                <td>
                                    <table class="table table-sm table-striped">
                                        <tr class="table-heading">
                                            <td>Internal Notes</td>
                                        </tr>
                                        <tr>
                                            <td colspan="4"><%= Encode.forHtmlContent(bean.getMessageNotes()) %>
                                            </td>
                                        </tr>

                                    </table>
                                </td>
                            </tr>

                            <tr>
                                <td>

                                    <table>
                                        <tr>
                                            <td colspan="4">
                                                <%
                                                    if (bean.getBillingType().compareTo("WCB") == 0 && request.getSession().getAttribute("WCBForm") != null) {
                                                        WCBForm wcb = (WCBForm) request.getSession().getAttribute("WCBForm");
                                                %> <%=Encode.forHtmlContent(wcb.getW_fname())%> <%}%>
                                            </td>

                                    </table>

                                </td>
                            </tr>

                        </table>
                    </td>
                </tr>
            </table>


            <div class="container-fluid ">
                <div class="float-end button-bar">
                    <%if ("pri".equalsIgnoreCase(bean.getBillingType())) {%>
                    <input class="btn btn-primary" type="submit" name="submit" value="Save & Print Receipt"/>
                    <%}%>
                    <% if (request.getAttribute("GOBACKWCB") != null && request.getAttribute("GOBACKWCB").equals("true")) {%>
                    <input class="btn btn-warning" type="button" name="Submit3" value="Go Back"
                           onClick="location.href='formwcb.jsp'"/>
                    <%} else {%>
                    <input class="btn btn-warning" type="button" name="Submit3" value="Go Back"
                           onClick="location.href='billingBC.jsp?loadFromSession=yes'"/>
                    <%}%>
                    <input class="btn btn-success" type="submit" name="submit" value="Another Bill"/>
                    <input class="btn btn-primary" type="submit" name="submit" value="Save Bill"/>
                    <input class="btn btn-danger" type="button" name="Submit2" value="Cancel"
                           onClick="window.close();"/>
                </div>
            </div>


        </div>
    </form>

</div>    <!--  end wrapper -->
</body>
</html>
<%!

    String getReferralString(String type) {
        String retval = "";
        if (type != null && type.equals("T")) {
            retval = "Referred To:";
        } else if (type != null && type.equals("B")) {
            retval = "Referred By:";
        }
        return retval;
    }

    String getAHDisplay(String s) {
        String retval = "No";
        try {
            if (s.equals("E")) {
                retval = "Evening";
            } else if (s.equals("N")) {
                retval = "Night";
            } else if (s.equals("W")) {
                retval = "Weekend";
            }
        } catch (Exception ahEx) {
        }
        return retval;
    }

    String addBrackets(String str) {
        String ret = "";
        if (str != null && str.trim().length() != 0) {
            ret = "(" + str + ")";
        }
        return ret;
    }
%>
