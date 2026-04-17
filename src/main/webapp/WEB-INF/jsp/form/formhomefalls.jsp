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
<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%
    String formClass = "HomeFalls";
    String formLink = "formhomefalls.jsp";

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
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.homeFalls.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>


    <script type="text/javascript" language="Javascript">
        var choiceFormat = new Array(6, 7, 8, 9, 10, 11, 12, 14, 15, 17, 18, 20, 21, 22, 23, 24, 25, 27, 28, 30, 31, 33, 34, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 49, 50, 52, 53, 55, 56, 58, 59, 60, 61, 63, 64, 65, 66, 68);
        var allNumericField = null;
        var allMatch = null;
        var action = "/<%=project_home%>/form/formname";

        function backToPage1() {
            document.getElementById('page1').style.display = 'block';
            document.getElementById('page2').style.display = 'none';
            document.getElementById('page3').style.display = 'none';
            document.getElementById('page4').style.display = 'none';
            document.getElementById('page5').style.display = 'none';
        }

        function goToPage2() {
            var checkboxes = new Array(6, 7, 8, 9, 10, 11, 12, 14);
            if (is1CheckboxChecked(0, checkboxes) == true && isFormCompleted(6, 14, 4, 0) == true) {
                document.getElementById('page1').style.display = 'none';
                document.getElementById('page2').style.display = 'block';
                document.getElementById('page3').style.display = 'none';
                document.getElementById('page4').style.display = 'none';
                document.getElementById('page5').style.display = 'none';
            }
        }

        function bTackoPage2() {
            document.getElementById('page1').style.display = 'none';
            document.getElementById('page2').style.display = 'block';
            document.getElementById('page3').style.display = 'none';
            document.getElementById('page4').style.display = 'none';
            document.getElementById('page5').style.display = 'none';
        }

        function goToPage3() {
            var checkboxes = new Array(15, 17, 18, 20, 21, 22, 23, 24, 25, 27);
            var numericFields = new Array(57, 58, 59, 60);
            if (is1CheckboxChecked(0, checkboxes) == true && isFormCompleted(15, 27, 5, 0) == true) {
                document.getElementById('page1').style.display = 'none';
                document.getElementById('page2').style.display = 'none';
                document.getElementById('page3').style.display = 'block';
                document.getElementById('page4').style.display = 'none';
                document.getElementById('page5').style.display = 'none';
            }
        }

        function backToPage3() {
            document.getElementById('page1').style.display = 'none';
            document.getElementById('page2').style.display = 'none';
            document.getElementById('page3').style.display = 'block';
            document.getElementById('page4').style.display = 'none';
            document.getElementById('page5').style.display = 'none';
        }

        function goToPage4() {
            var checkboxes = new Array(28, 30, 31, 33, 34, 36, 37, 38, 39, 40, 41, 42);
            if (is1CheckboxChecked(0, checkboxes) == true && isFormCompleted(28, 42, 6, 0) == true) {
                document.getElementById('page1').style.display = 'none';
                document.getElementById('page2').style.display = 'none';
                document.getElementById('page3').style.display = 'none';
                document.getElementById('page4').style.display = 'block';
                document.getElementById('page5').style.display = 'none';
            }
        }

        function backToPage4() {
            document.getElementById('page1').style.display = 'none';
            document.getElementById('page2').style.display = 'none';
            document.getElementById('page3').style.display = 'none';
            document.getElementById('page4').style.display = 'block';
            document.getElementById('page5').style.display = 'none';
        }

        function goToPage5() {
            var checkboxes = new Array(43, 44, 45, 46, 47, 49, 50, 52, 53, 55);
            if (is1CheckboxChecked(0, checkboxes) == true && isFormCompleted(43, 55, 5, 0) == true) {
                document.getElementById('page1').style.display = 'none';
                document.getElementById('page2').style.display = 'none';
                document.getElementById('page3').style.display = 'none';
                document.getElementById('page4').style.display = 'none';
                document.getElementById('page5').style.display = 'block';
            }
        }

        function checkBeforeSave() {
            if (document.getElementById('page5').style.display == 'block') {
                if (isFormCompleted(56, 68, 5, 0) == true)
                    return true;
            } else {
                if (isFormCompleted(6, 14, 4, 0) == true && isFormCompleted(15, 27, 5, 0) == true && isFormCompleted(28, 42, 6, 0) == true && isFormCompleted(43, 55, 5, 0) == true && isFormCompleted(56, 68, 5, 0) == true)
                    return true;
            }

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
        <input type="hidden" name="submit" value="exit"/>

        <table border="0" cellspacing="0" cellpadding="0" width="740px"
               height="95%">
            <tr>
                <td>
                    <table border="0" cellspacing="0" cellpadding="0" width="740px"
                           height="10%">
                        <tr>
                            <th class="subject">The Home Falls and Accidents Screening
                                Tool (HOME FAST)
                            </th>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td valign="top">
                    <table border="0" cellspacing="0" cellpadding="0" height="85%"
                           width="740px" id="page1">
                        <tr>
                            <td colspan="2">
                                <table width="740px" height="620px" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr>
                                        <td colspan="4">
                                            <table width="100%">
                                                <tr>
                                                    <td valign="top" width="15%"><font
                                                            style="font-weight: bold"><fmt:message key="form.homeFalls.definitionLabel"/></font></td>
                                                    <td valign="top" width="85%"><fmt:message key="form.homeFalls.definitionText"/></td>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.floors"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">1.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q1"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><font style="font-style: italic"><fmt:message key="form.homeFalls.definitions"/></font>
                                            <fmt:message key="form.homeFalls.d1"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor1Y" <%= props.getProperty("floor1Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor1N" <%= props.getProperty("floor1N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">2.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q2"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d2"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor2Y" <%= props.getProperty("floor2Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor2N" <%= props.getProperty("floor2N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">3.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q3"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d3"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor3Y" <%= props.getProperty("floor3Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor3N" <%= props.getProperty("floor3N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">4.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q4"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d4"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor4Y" <%= props.getProperty("floor4Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="floor4N" <%= props.getProperty("floor4N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="floor4NA" <%= props.getProperty("floor4NA", "") %> /><fmt:message key="global.na"/>
                                            (there are no mats in the house)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="7">
                                            <table height="30">
                                                <tr>
                                                    <td>&nbsp;</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="subject">
                            <td></td>
                            <td align="right"><a href="javascript: goToPage2();"><fmt:message key="form.homeFalls.nextPage"/> >></a></td>
                        </tr>
                    </table>

                    <table border="0" cellspacing="0" cellpadding="0"
                           style="display: none" width="740px" height="85%" id="page2">
                        <tr>
                            <td colspan="2">
                                <table width="740px" height="620px" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.furniture"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">5.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q5"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d5"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="furniture5Y" <%= props.getProperty("furniture5Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="furniture5N" <%= props.getProperty("furniture5N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="furniture5NA" <%= props.getProperty("furniture5NA", "") %> /><fmt:message key="global.na"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">6.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q6"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d6"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="furniture6Y" <%= props.getProperty("furniture6Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="furniture6N" <%= props.getProperty("furniture6N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="furniture6NA" <%= props.getProperty("furniture6NA", "") %> /><fmt:message key="global.na"/>
                                            (person uses wheelchair constantly)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>&nbsp;</td>
                                    </tr>
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.lighting"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">7.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q7"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d7"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting7Y" <%= props.getProperty("lighting7Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting7N" <%= props.getProperty("lighting7N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">8.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q8"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><font style="font-style: italic"><fmt:message key="form.homeFalls.definitions"/></font>
                                            <fmt:message key="form.homeFalls.d8"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting8Y" <%= props.getProperty("lighting8Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting8N" <%= props.getProperty("lighting8N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">9.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q9"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><font style="font-style: italic"><fmt:message key="form.homeFalls.definitions"/></font>
                                            <fmt:message key="form.homeFalls.d9"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting9Y" <%= props.getProperty("lighting9Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="lighting9N" <%= props.getProperty("lighting9N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%">
                                            <table width="100%">
                                                <tr>
                                                    <td width="3%"><input type="checkbox" class="checkbox"
                                                                          name="lighting9NA" <%= props.getProperty("lighting9NA", "") %> />
                                                    </td>
                                                    <td width="97%"><fmt:message key="global.na"/> (no outside path, step or entrance =
                                                        access door opens straight onto public footpath)
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="7">
                                            <table height="30">
                                                <tr>
                                                    <td>&nbsp;</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="subject">
                            <td align="left"><a href="javascript: backToPage1();"><<
                                <fmt:message key="form.homeFalls.previousPage"/></a></td>
                            <td align="right"><a href="javascript: goToPage3();"><fmt:message key="form.homeFalls.nextPage"/> >></a></td>
                        </tr>
                    </table>

                    <table border="0" cellspacing="0" cellpadding="0"
                           style="display: none" width="740px" height="85%" id="page3">
                        <tr>
                            <td colspan="2">
                                <table width="740px" height="620px" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.bathroom"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">10.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q10"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d10"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom10Y" <%= props.getProperty("bathroom10Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom10N" <%= props.getProperty("bathroom10N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="bathroom10NA" <%= props.getProperty("bathroom10NA", "") %> /><fmt:message key="global.na"/>
                                            (person uses commode constantly)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">11.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q11"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><font style="font-style: italic"><fmt:message key="form.homeFalls.definitions"/></font>
                                            <fmt:message key="form.homeFalls.d11"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom11Y" <%= props.getProperty("bathroom11Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom11N" <%= props.getProperty("bathroom11N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="bathroom11NA" <%= props.getProperty("bathroom11NA", "") %> /><fmt:message key="global.na"/>
                                            (no bath in the home, or bath never used)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">12.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q12"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            Person can step over shower hob, or screen tracks without risk
                                            and without having to hold onto anything for support.
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom12Y" <%= props.getProperty("bathroom12Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom12N" <%= props.getProperty("bathroom12N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="bathroom12NA" <%= props.getProperty("bathroom12NA", "") %> /><fmt:message key="global.na"/>
                                            <fmt:message key="form.homeFalls.naShowerRecess"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">13.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q13"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d13"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom13Y" <%= props.getProperty("bathroom13Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom13N" <%= props.getProperty("bathroom13N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">14.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q14"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d14"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom14Y" <%= props.getProperty("bathroom14Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom14N" <%= props.getProperty("bathroom14N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">15.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q15"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d15"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom15Y" <%= props.getProperty("bathroom15Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="bathroom15N" <%= props.getProperty("bathroom15N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <td colspan="7">
                                            <table height="30">
                                                <tr>
                                                    <td>&nbsp;</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="subject">
                            <td align="left"><a href="javascript: backToPage2();"><<
                                <fmt:message key="form.homeFalls.previousPage"/></a></td>
                            <td align="right"><a href="javascript: goToPage4();"><fmt:message key="form.homeFalls.nextPage"/> >></a></td>
                        </tr>
                    </table>

                    <table border="0" cellspacing="0" cellpadding="0"
                           style="display: none" width="740px" height="85%" id="page4">
                        <tr>
                            <td colspan="2">
                                <table width="740px" height="620px" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.storage"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%" valign="top">16.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q16"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d16"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="storage16Y" <%= props.getProperty("storage16Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="storage16N" <%= props.getProperty("storage16N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">17.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q17"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d17"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="storage17Y" <%= props.getProperty("storage17Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="storage17N" <%= props.getProperty("storage17N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <td>&nbsp;</td>
                                    </tr>
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.stairwaysSteps"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%" valign="top">18.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q18"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d18"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway18Y" <%= props.getProperty("stairway18Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway18N" <%= props.getProperty("stairway18N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="stairway18NA" <%= props.getProperty("stairway18NA", "") %> /><fmt:message key="global.na"/>
                                            (no steps or stairs exist inside the home)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%" valign="top">19.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q19"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d19"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway19Y" <%= props.getProperty("stairway19Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway19N" <%= props.getProperty("stairway19N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="stairway19NA" <%= props.getProperty("stairway19NA", "") %> /><fmt:message key="global.na"/>
                                            (no steps or stairs exist outside the home)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%" valign="top">20.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q20"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d20"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway20Y" <%= props.getProperty("stairway20Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway20N" <%= props.getProperty("stairway20N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="stairway20NA" <%= props.getProperty("stairway20NA", "") %> /><fmt:message key="global.na"/>
                                            (no steps or stairs exist)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="7">
                                            <table height="5">
                                                <tr>
                                                    <td></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="subject">
                            <td align="left"><a href="javascript: backToPage3();"><<
                                <fmt:message key="form.homeFalls.previousPage"/></a></td>
                            <td align="right"><a href="javascript: goToPage5();"><fmt:message key="form.homeFalls.nextPage"/> >></a></td>
                        </tr>
                    </table>

                    <table border="0" cellspacing="0" cellpadding="0"
                           style="display: none" width="740px" height="85%" id="page5">
                        <tr>
                            <td colspan="2">
                                <table width="740px" height="620px" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.stairwaysStepsContinue"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">21.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q21"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d21"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway21Y" <%= props.getProperty("stairway21Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway21N" <%= props.getProperty("stairway21N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="stairway21NA" <%= props.getProperty("stairway21NA", "") %> /><fmt:message key="global.na"/>
                                            (no steps or stairs exist)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">22.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q22"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d22"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway22Y" <%= props.getProperty("stairway22Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="stairway22N" <%= props.getProperty("stairway22N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <td>&nbsp;</td>
                                    </tr>
                                    <tr class="title">
                                        <th colspan="4"><fmt:message key="form.homeFalls.mobility"/></th>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">23.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q23"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d23"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility23Y" <%= props.getProperty("mobility23Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility23N" <%= props.getProperty("mobility23N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="mobility23NA" <%= props.getProperty("mobility23NA", "") %> /><fmt:message key="global.na"/>
                                            (no garden, path or yard exists)
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%">24.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.q24"/>
                                        </th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d24"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility24Y" <%= props.getProperty("mobility24Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility24N" <%= props.getProperty("mobility24N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"></td>
                                    </tr>
                                    <tr>
                                        <th class="question" width="5%" valign="top">25.</th>
                                        <th class="question" colspan="3"><fmt:message key="form.homeFalls.petQuestion"/></th>
                                    </tr>
                                    <tr>
                                        <td></td>
                                        <td colspan="3"><fmt:message key="form.homeFalls.definitions"/>
                                            <fmt:message key="form.homeFalls.d25"/>
                                        </td>
                                    </tr>
                                    <tr bgcolor="white">
                                        <td width="5%"></td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility25Y" <%= props.getProperty("mobility25Y", "") %> />
                                            <fmt:message key="global.yes"/>
                                        </td>
                                        <td width="10%"><input type="checkbox" class="checkbox"
                                                               name="mobility25N" <%= props.getProperty("mobility25N", "") %> />
                                            <fmt:message key="global.no"/>
                                        </td>
                                        <td width="75%"><input type="checkbox" class="checkbox"
                                                               name="mobility25NA" <%= props.getProperty("mobility25NA", "") %> /><fmt:message key="form.homeFalls.na"/>
                                            (there are no pets/animals)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="3">
                                            <table height="40">
                                                <tr>
                                                    <td>&nbsp;</td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr class="subject">
                            <td align="left"><a href="javascript: backToPage4();"><<
                                <fmt:message key="form.homeFalls.previousPage"/></a></td>
                            <td align="right"></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td valign="top">
                    <table class="Head" class="hidePrint" height="5%">
                        <tr>
                            <td align="left">
                                <%
                                    if (!bView) {
                                %> <input type="submit" value="<fmt:message key='global.save'/>"
                                          onclick="javascript: return onSave();"/> <input type="submit"
                                                                                          value="<fmt:message key='global.saveExit'/>"
                                                                                          onclick="javascript:if(checkBeforeSave()==true) return onSaveExit(); else return false;"/>
                                <%
                                    }
                                %> <input type="button" value="<fmt:message key='global.btnExit'/>"
                                          onclick="javascript:return onExit();"/> <input type="button"
                                                                                         value="<fmt:message key='global.btnPrint'/>"
                                                                                         onclick="javascript:window.print();"/>
                            </td>
                            <td align="right"><fmt:message key="form.homeFalls.studyId"/>: <%= props.getProperty("studyID", "N/A") %>
                                <input type="hidden" name="studyID"
                                       value="<%= props.getProperty("studyID", "N/A") %>"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
