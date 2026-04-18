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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page import="java.util.*, java.sql.*, java.net.*, io.github.carlos_emr.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>


<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String curProvider_no = (String) session.getAttribute("user");

    java.util.Properties oscarVariables = CarlosProperties.getInstance();

    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
%>

<html>
    <head>
        <title><fmt:message key="demographic.demographiclabelprintsetting.title"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap -->

        <script src="<%= request.getContextPath() %>/js/global.js"></script>

        <style>
            .copytext {
                font-family: courier;
                font-size: 12px;
                user-select: all;
                cursor: pointer;
            }
        </style>
        <script>

            function onNewPatient() {
                document.labelprint.label1no.value = "1";
                document.labelprint.label1checkbox.checked = true;
                document.labelprint.label2checkbox.checked = true;
                document.labelprint.label3checkbox.checked = true;
                document.labelprint.label2no.value = "6";
                document.labelprint.label3no.value = "0";
            }

            function checkTotal() {
                var total = 0 + document.labelprint.label1no.value + document.labelprint.label2no.value + document.labelprint.label3no.value + document.labelprint.label4no.value + document.labelprint.label5no.value;
                if (total > 7) return false;
                return true;
            }


        </script>
    </head>
    <body onLoad="setfocus()">
    <h4><fmt:message key="demographic.demographiclabelprintsetting.msgMainLabel"/></h4>

    <%
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        int age = 0, dob_year = 0, dob_month = 0, dob_date = 0;
        String first_name = "", last_name = "", chart_no = "", address = "", city = "", province = "", postal = "", phone = "", phone2 = "", dob = "", sex = "", hin = "";
        String refDoc = "";
        String providername = "";
        String demoNo = request.getParameter("demographic_no");

        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
    %>
    <fmt:message key="demographic.demographiclabelprintsetting.msgFailed"/>
    <%
        } else {
            ProviderData provider = providerDao.findByProviderNo(demo.getProviderNo());
            if (provider != null) {
                providername = provider.getLastName() + "," + provider.getFirstName();
            }

            first_name = demo.getFirstName() != null ? demo.getFirstName() : "";
            last_name = demo.getLastName() != null ? demo.getLastName() : "";
            sex = demo.getSex() != null ? demo.getSex() : "";
            dob_year = Integer.parseInt(demo.getYearOfBirth());
            dob_month = Integer.parseInt(demo.getMonthOfBirth());
            dob_date = Integer.parseInt(demo.getDateOfBirth());
            if (dob_year != 0) age = MyDateFormat.getAge(dob_year, dob_month, dob_date);
            dob = dob_year + "/" + demo.getMonthOfBirth() + "/" + demo.getDateOfBirth();

            if (demo.getChartNo() != null) chart_no = demo.getChartNo();
            if (demo.getAddress() != null) address = demo.getAddress();
            if (demo.getCity() != null) city = demo.getCity();
            if (demo.getProvince() != null) province = demo.getProvince();
            if (demo.getPostal() != null) postal = demo.getPostal();
            if (demo.getPhone() != null) phone = demo.getPhone();
            if (demo.getPhone2() != null) phone2 = demo.getPhone2();
            if (demo.getHin() != null) hin = "HN " + demo.getHcType() + " " + demo.getHin() + " " + demo.getVer();
            if (demo.getFamilyDoctor() != null) refDoc = SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rd");
        }
        /* phone2 is split into two variables to avoid double-encoding:
             phone2Raw holds the unencoded value for hidden form inputs (encoded at output with forHtmlAttribute);
             phone2Display pre-encodes the user portion with forHtml() before appending raw &nbsp; padding,
             so the entities render correctly. Do NOT re-encode phone2Display. */
        String phone2Raw = (phone2 == null ? "" : phone2);
        String phone2Display = phone2Raw.isEmpty()
            ? "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            : (Encode.forHtml(phone2Raw) + "&nbsp;");
    %>

    <form method="post" class="" name="labelprint" action="<%= request.getContextPath() %>/demographic/ViewDemographicPrintDemographic">
        <div class="card card-body bg-body-tertiary">
            <table style="width:100%">
                <tr style="text-align:center">
                    <th><fmt:message key="demographic.demographiclabelprintsetting.msgLabel"/></th>
                    <th><fmt:message key="demographic.demographiclabelprintsetting.msgNumeberOfLabel"/></th>
                    <th><fmt:message key="demographic.demographiclabelprintsetting.msgLocation"/>
                        <input type="hidden" name="address" value="<e:forHtmlAttribute value='<%= address %>' />">
                        <input type="hidden" name="chart_no" value="<e:forHtmlAttribute value='<%= chart_no %>' />">
                        <input type="hidden" name="city" value="<e:forHtmlAttribute value='<%= city %>' />">
                        <input type="hidden" name="dob" value="<e:forHtmlAttribute value='<%= dob %>' />">
                        <input type="hidden" name="first_name" value="<e:forHtmlAttribute value='<%= first_name %>' />">
                        <input type="hidden" name="hin" value="<e:forHtmlAttribute value='<%= hin %>' />">
                        <input type="hidden" name="last_name" value="<e:forHtmlAttribute value='<%= last_name %>' />">
                        <input type="hidden" name="phone" value="<e:forHtmlAttribute value='<%= phone %>' />">
                        <input type="hidden" name="phone2" value="<e:forHtmlAttribute value='<%= phone2Raw %>' />">
                        <input type="hidden" name="postal" value="<e:forHtmlAttribute value='<%= postal %>' />">
                        <input type="hidden" name="providername" value="<e:forHtmlAttribute value='<%= providername %>' />">
                        <input type="hidden" name="province" value="<e:forHtmlAttribute value='<%= province %>' />">
                        <input type="hidden" name="sex" value="<e:forHtmlAttribute value='<%= sex %>' />">
                        <input type="hidden" name="age" value="<e:forHtmlAttribute value='<%= String.valueOf(age) %>' />">
                    </th>
                </tr>
                <tr>
                    <td style="align:center">
                        <table style="width:90%">
                            <tr>
                                <td style="border: solid 1px; background-color: white;">
				<span id="copytext1" class="copytext"> <b><e:forHtmlContent value='<%= last_name %>' />,&nbsp;<e:forHtmlContent value='<%= first_name %>' /></b><br>
				&nbsp;&nbsp;&nbsp;&nbsp;<e:forHtmlContent value='<%= hin %>' /><br>
				&nbsp;&nbsp;&nbsp;&nbsp;<e:forHtmlContent value='<%= dob %>' />&nbsp;<e:forHtmlContent value='<%= sex %>' /><br>
				<br>
				<b><e:forHtmlContent value='<%= last_name %>' />,&nbsp;<e:forHtmlContent value='<%= first_name %>' /></b><br>
				&nbsp;&nbsp;&nbsp;&nbsp;<e:forHtmlContent value='<%= hin %>' /><br>
				&nbsp;&nbsp;&nbsp;&nbsp;<e:forHtmlContent value='<%= dob %>' />&nbsp;<e:forHtmlContent value='<%= sex %>' /><br>
				</span></td>
                            </tr>
                        </table>
                    </td>
                    <td style="text-align:center; background-color:#CCCCCC"><a href="#" onClick="onNewPatient()">
                        <fmt:message key="demographic.demographiclabelprintsetting.btnNewPatientLabel"/></a><br><br>
                        <input type="checkbox" name="label1checkbox" value="checked">
                        <input type="text" name="label1no" size="2" maxlength="2"
                               value="<%= oscarVariables.getProperty("label.1no","1") %>"/>
                    </td>
                    <td rowspan=5 style="vertical-align:middle; background-color:#999999;">
                        <div class="mb-3">
                            <label class="form-label"><fmt:message key="demographic.demographiclabelprintsetting.formLeft"/>:</label>
                            <div>
                                <input type="text" class="form-control form-control-sm d-inline-block w-auto" name="left"
                                       placeholder="<fmt:message key='demographic.demographiclabelprintsetting.msgPx'/>"
                                       maxlength="3" value="<%= oscarVariables.getProperty("label.left","200") %>">&nbsp;
                                <fmt:message key="demographic.demographiclabelprintsetting.msgPx"/>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label"><fmt:message key="demographic.demographiclabelprintsetting.formTop"/>:</label>
                            <div>
                                <input type="text" class="form-control form-control-sm d-inline-block w-auto" name="top" maxlength="3"
                                       value="<%= oscarVariables.getProperty("label.top","0")%>"
                                       placeholder="<fmt:message key='demographic.demographiclabelprintsetting.msgPx'/>">&nbsp;
                                <fmt:message key="demographic.demographiclabelprintsetting.msgPx"/>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label"><fmt:message key="demographic.demographiclabelprintsetting.formHeight"/>:</label>
                            <div>
                                <input type="text" class="form-control form-control-sm d-inline-block w-auto" name="height" maxlength="3"
                                       value="<%= oscarVariables.getProperty("label.height","145")%>"
                                       placeholder="<fmt:message key='demographic.demographiclabelprintsetting.msgPx'/>">&nbsp;
                                <fmt:message key="demographic.demographiclabelprintsetting.msgPx"/>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label"><fmt:message key="demographic.demographiclabelprintsetting.formGap"/>:</label>
                            <div>
                                <input type="text" class="form-control form-control-sm d-inline-block w-auto" name="gap" size="3" maxlength="3"
                                       value="<%= oscarVariables.getProperty("label.gap","0")%>"
                                       placeholder="<fmt:message key='demographic.demographiclabelprintsetting.msgPx'/>">&nbsp;
                                <fmt:message key="demographic.demographiclabelprintsetting.msgPx"/>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td style="align:center">
                        <table style="width:90%">
                            <tr>
                                <td style="border: solid 1px; background-color: white;">
				<span id="copytext2"
                      class="copytext"> <b><e:forHtmlContent value='<%= last_name %>' />,&nbsp;<e:forHtmlContent value='<%= first_name %>' />&nbsp;<e:forHtmlContent value='<%= chart_no %>' /></b><br><e:forHtmlContent value='<%= address %>' /><br><e:forHtmlContent value='<%= city %>' />,&nbsp;<e:forHtmlContent value='<%= province %>' />,&nbsp;<e:forHtmlContent value='<%= postal %>' /><br>
				<fmt:message key="demographic.demographiclabelprintsetting.msgHome"/>:&nbsp;<e:forHtmlContent value='<%= phone %>' /><br><e:forHtmlContent value='<%= dob %>' />&nbsp;<e:forHtmlContent value='<%= sex %>' /><br><e:forHtmlContent value='<%= hin %>' /><br>
				<fmt:message key="demographic.demographiclabelprintsetting.msgBus"/>:<%=phone2Display%>&nbsp;
				<fmt:message key="demographic.demographiclabelprintsetting.msgDr"/>&nbsp;<e:forHtmlContent value='<%= providername %>' /><br>
				</span></td>
                            </tr>
                        </table>
                    </td>
                    <td style="text-align:center; background-color:#CCCCCC">
                        <input type="checkbox" name="label2checkbox" value="checked" checked>
                        <input type="text" name="label2no" size="2" maxlength="2"
                               value="<%= oscarVariables.getProperty("label.2no","1") %>"></td>
                </tr>
                <tr>
                    <td style="align:center">
                        <table style="width:90%">
                            <tr>
                                <td style="border: solid 1px; background-color: white;">
				<span id="copytext3" class="copytext"> <e:forHtmlContent value='<%= last_name %>' />,&nbsp;<e:forHtmlContent value='<%= first_name %>' /><br><e:forHtmlContent value='<%= address %>' /><br><e:forHtmlContent value='<%= city %>' />,&nbsp;<e:forHtmlContent value='<%= province %>' />,&nbsp;<e:forHtmlContent value='<%= postal %>' /><br>
				</span></td>
                            </tr>
                        </table>
                    </td>
                    <td style="text-align:center; background-color:#CCCCCC">
                        <input type="checkbox" name="label3checkbox" value="checked">
                        <input type="text" name="label3no" size="2" maxlength="2"
                               value="<%= oscarVariables.getProperty("label.3no","1") %>"></td>
                </tr>
                <tr>
                    <td style="align:center">
                        <table style="width:90%">
                            <tr>
                                <td style="border: solid 1px; background-color: white;">
				<span id="copytext4" class="copytext"> <e:forHtmlContent value='<%= first_name %>' />&nbsp;<e:forHtmlContent value='<%= last_name %>' /><br><e:forHtmlContent value='<%= address %>' /><br><e:forHtmlContent value='<%= city %>' />,&nbsp;<e:forHtmlContent value='<%= province %>' />,&nbsp;<e:forHtmlContent value='<%= postal %>' /><br>
				</span></td>
                            </tr>
                        </table>
                    </td>
                    <td style="text-align:center; background-color:#CCCCCC">
                        <textarea id="text1" STYLE="display: none;"> </textarea>
                        <input type="checkbox" name="label4checkbox" value="checked">
                        <input type="text" name="label4no" size="2" maxlength="2"
                               value="<%= oscarVariables.getProperty("label.4no","1") %>"></td>
                </tr>
                <tr>
                    <td style="align:center">
                        <table style="width:90%">
                            <tr>
                                <td style="border: solid 1px; background-color: white;">
				<span id="copytext5"
                      class="copytext"> <e:forHtmlContent value='<%= chart_no %>' /> &nbsp;&nbsp;<e:forHtmlContent value='<%= last_name %>' />, <e:forHtmlContent value='<%= first_name %>' /><br><e:forHtmlContent value='<%= address %>' />, <e:forHtmlContent value='<%= city %>' />, <e:forHtmlContent value='<%= province %>' />, <e:forHtmlContent value='<%= postal %>' />
				<br><e:forHtmlContent value='<%= dob %>' /> &nbsp;&nbsp;&nbsp;<e:forHtmlContent value='<%= String.valueOf(age) %>' /> <e:forHtmlContent value='<%= sex %>' /> &nbsp;<e:forHtmlContent value='<%= hin %>' /><br><e:forHtmlContent value='<%= phone %>' />&nbsp;&nbsp;&nbsp;<%=phone2Display%><br><e:forHtmlContent value='<%= refDoc %>' />
				</span></td>
                            </tr>
                        </table>
                    </td>
                    <td style="text-align:center; background-color:#CCCCCC"><textarea id="text1"
                                                                                      style="display: none;"></textarea>
                        <input type="checkbox" name="label5checkbox" value="checked">
                        <input type="text" name="label5no" size="2" maxlength="2"
                               value="<%= oscarVariables.getProperty("label.5no","1") %>"></td>
                </tr>
                <tr>
                    <td style="text-align:left" colspan="3"><br><input type="submit" name="Submit"
                                                                       class="btn btn-primary"
                                                                       value="<fmt:message key='demographic.demographiclabelprintsetting.btnPrintPreviewPrint'/>">
                        <input type="button" class="btn btn-link" name="button"
                               value="<fmt:message key='global.btnBack'/>"
                               onClick="window.close();"></td>
                </tr>
            </table>
        </div>
    </form>

    </body>
</html>