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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>




<%
    String formClass = "SelfAdministered";
    String formLink = "formselfadministered.jsp";

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

    //FrmData fd = new FrmData();    String resource = fd.getResource(); resource = resource + "ob/riskinfo/";

    //get project_home
    String project_home = request.getContextPath().substring(1);
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Self Administered Questions Used in Self-Report Risk
            Index</title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>


    <script type="text/javascript" language="Javascript">

        var choiceFormat = new Array(6, 10, 11, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);
        var allMatch = null;
        var allNumericField = null;
        var action = "/<%=project_home%>/form/formname";

        function checkBeforeSave() {
            if (isFormCompleted(6, 30, 9, 0) == true)
                return true;

            return false;
        }

    </script>
    <script type="text/javascript" src="formScripts.js">
    </script>


    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0"
          onload="window.resizeTo(768,768)">
    <!--
    @oscar.formDB Table="formAdf"
    @oscar.formDB Field="ID" Type="int(10)" Null="NOT NULL" Key="PRI" Default="" Extra="auto_increment"
    @oscar.formDB Field="demographic_no" Type="int(10)" Null="NOT NULL" Default="'0'"
    @oscar.formDB Field="provider_no" Type="int(10)" Null="" Default="NULL"
    @oscar.formDB Field="formCreated" Type="date" Null="" Default="NULL"
    @oscar.formDB Field="formEdited" Type="timestamp"
    -->
    <form action="${pageContext.request.contextPath}/form/formname" method="post">
        <input type="hidden" name="demographic_no"
               value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="formCreated"
               value="<%= props.getProperty("formCreated", "") %>"/>
        <input type="hidden" name="form_class" value="<%=formClass%>"/>
        <input type="hidden" name="form_link" value="<%=formLink%>"/>
        <input type="hidden" name="formId" value="<%=formId%>"/>
        <!--input type="hidden" name="provider_no" value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="provNo" value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' context="htmlAttribute"/>" /-->
        <input type="hidden" name="submit" value="exit"/>

        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align='LEFT'><font face="Arial, Helvetica, sans-serif"
                                       color="#FFFFFF"><fmt:message key="form.selfAdministered.heading"/></font></th>
            </tr>
        </table>

        <table>
            <tr>
                <td>
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td align="right">1.</td>
                            <td><fmt:message key="form.selfAdministered.q1"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="healthEx"
                                    <%= props.getProperty("healthEx", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.excellent"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="healthVG"
                                    <%= props.getProperty("healthVG", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.veryGood"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="healthG"
                                    <%= props.getProperty("healthG", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.good"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="healthF"
                                    <%= props.getProperty("healthF", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.fair"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="healthP"
                                    <%= props.getProperty("healthP", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.poor"/></td>
                        </tr>
                        <tr>
                            <td align="right">2.</td>
                            <td><fmt:message key="form.selfAdministered.q2"/>
                            </td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="stayInHospNo"
                                    <%= props.getProperty("stayInHospNo", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.notAtAll"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="stayInHosp1"
                                    <%= props.getProperty("stayInHosp1", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.oneTime"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="stayInHosp2Or3"
                                    <%= props.getProperty("stayInHosp2Or3", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.twoOrThreeTimes"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="stayInHospMore3"
                                    <%= props.getProperty("stayInHospMore3", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.moreThanThreeTimes"/></td>
                        </tr>
                        <tr>
                            <td align="right">3.</td>
                            <td><fmt:message key="form.selfAdministered.q3"/>
                            </td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="visitPhyNo"
                                    <%= props.getProperty("visitPhyNo", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.notAtAll"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="visitPhy1"
                                    <%= props.getProperty("visitPhy1", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.oneTime"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="visitPhy2Or3"
                                    <%= props.getProperty("visitPhy2Or3", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.twoOrThreeTimes"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="visitPhyMore3"
                                    <%= props.getProperty("visitPhyMore3", "") %> /></td>
                            <td><fmt:message key="form.selfAdministered.opt.moreThanThreeTimes"/></td>
                        </tr>
                        <tr>
                            <td align="right">4.</td>
                            <td><fmt:message key="form.selfAdministered.q4"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="diabetesY"
                                    <%= props.getProperty("diabetesY", "") %> /></td>
                            <td><fmt:message key="global.yes"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="diabetesN"
                                    <%= props.getProperty("diabetesN", "") %> /></td>
                            <td><fmt:message key="global.no"/></td>
                        </tr>
                        <tr>
                            <td align="right">5.</td>
                            <td><fmt:message key="form.selfAdministered.q5"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td colspan="2">
                                <table bgcolor="white">
                                    <tr>
                                        <td width="30%"><fmt:message key="form.selfAdministered.opt.coronaryHeartDisease"/></td>
                                        <td width="20%"><fmt:message key="form.selfAdministered.opt.anginaPectoris"/></td>
                                        <td width="25%"><fmt:message key="form.selfAdministered.opt.myocardialInfarction"/></td>
                                        <td width="25%"><fmt:message key="form.selfAdministered.opt.anyOtherHeartAttack"/></td>
                                    </tr>
                                    <tr>
                                        <td align="left"><input type="checkbox" class="checkbox"
                                                                name="heartDiseaseY"
                                                <%= props.getProperty("heartDiseaseY", "") %> /> <fmt:message key="global.yes"/> <br>
                                            <input type="checkbox" class="checkbox" name="heartDiseaseN"
                                                    <%= props.getProperty("heartDiseaseN", "") %> /> <fmt:message key="global.no"/>
                                        </td>
                                        <td align="left"><input type="checkbox" class="checkbox"
                                                                name="anginaPectorisY"
                                                <%= props.getProperty("anginaPectorisY", "") %> /> <fmt:message key="global.yes"/><br>
                                            <input type="checkbox" class="checkbox" name="anginaPectorisN"
                                                    <%= props.getProperty("anginaPectorisN", "") %> /> <fmt:message key="global.no"/>
                                        </td>
                                        <td align="left"><input type="checkbox" class="checkbox"
                                                                name="myocardialInfarctionY"
                                                <%= props.getProperty("myocardialInfarctionY", "") %> /> <fmt:message key="global.yes"/><br>
                                            <input type="checkbox" class="checkbox"
                                                   name="myocardialInfarctionN"
                                                    <%= props.getProperty("myocardialInfarctionN", "") %> /> <fmt:message key="global.no"/>
                                        </td>
                                        <td align="left"><input type="checkbox" class="checkbox"
                                                                name="anyHeartAttackY"
                                                <%= props.getProperty("anyHeartAttackY", "") %> /> <fmt:message key="global.yes"/><br>
                                            <input type="checkbox" class="checkbox" name="anyHeartAttackN"
                                                    <%= props.getProperty("anyHeartAttackN", "") %> /> <fmt:message key="global.no"/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td align="right" valign="top">6.</td>
                            <td><fmt:message key="form.selfAdministered.q6"/>: <%= props.getProperty("sex", "") %>
                            </td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right">&nbsp;</td>
                            <td></td>
                        </tr>
                        <tr>
                            <td align="right" valign="top">7.</td>
                            <td><fmt:message key="form.selfAdministered.q7"/>
                            </td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="relativeTakeCareY"
                                    <%= props.getProperty("relativeTakeCareY", "") %> /></td>
                            <td><fmt:message key="global.yes"/></td>
                        </tr>
                        <tr bgcolor="white">
                            <td width="5%" align="right"><input type="checkbox"
                                                                class="checkbox" name="relativeTakeCareN"
                                    <%= props.getProperty("relativeTakeCareN", "") %> /></td>
                            <td><fmt:message key="global.no"/></td>
                        </tr>
                        <tr>
                            <td align="right" valign="top">8.</td>
                            <td><fmt:message key="form.selfAdministered.q8"/>: <%= props.getProperty("dob", "") %>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <table class="Head" class="hidePrint">
            <tr>
                <td align="left">
                    <%
                        if (!bView) {
                    %> <input type="submit" value="<fmt:message key='global.save'/>"
                              onclick="javascript:return onSave();"/> <input type="submit"
                                                                             value="<fmt:message key='global.saveExit'/>"
                                                                             onclick="javascript:if(checkBeforeSave()==true) return onSaveExit(); else return false;"/>
                    <%
                        }
                    %> <input type="button" value="<fmt:message key='global.btnExit'/>"
                              onclick="javascript:return onExit();"/> <input type="button"
                                                                             value="<fmt:message key='global.btnPrint'/>"
                                                                             onclick="javascript:window.print();"/></td>
                <td align="right"><fmt:message key="form.common.studyId"/>: <%= props.getProperty("studyID", "N/A") %>
                    <input type="hidden" name="studyID"
                           value="<%= props.getProperty("studyID", "N/A") %>"/> <input
                            type="hidden" name="sex" value="<%= props.getProperty("sex", "") %>"/>
                    <input type="hidden" name="dob"
                           value="<%= props.getProperty("dob", "") %>"/></td>
            </tr>
        </table>
    </form>
    </body>
</html>
