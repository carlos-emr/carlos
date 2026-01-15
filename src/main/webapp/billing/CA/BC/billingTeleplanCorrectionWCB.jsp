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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.billing.ca.bc.data.*,ca.openosp.*" %>
<%@page import="java.util.*,java.io.*,ca.openosp.openo.billing.ca.bc.MSP.*,ca.openosp.openo.billing.ca.bc.administration.*,java.sql.*" %>

<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.ClinicLocationDao" %>
<%@page import="ca.openosp.openo.commn.model.ClinicLocation" %>
<%@ page import="ca.openosp.openo.commn.model.DiagnosticCode" %>
<%@ page import="ca.openosp.openo.commn.dao.DiagnosticCodeDao" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.billing.CA.model.BillingDetail" %>
<%@ page import="ca.openosp.openo.billing.CA.dao.BillingDetailDao" %>
<%@ page import="ca.openosp.openo.entities.Billingmaster" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO" %>
<%@ page import="ca.openosp.openo.billing.CA.BC.model.TeleplanC12" %>
<%@ page import="ca.openosp.openo.billing.CA.BC.dao.TeleplanC12Dao" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.administration.TeleplanCorrectionFormWCB" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.MSP.MspErrorCodes" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingFormData" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    DiagnosticCodeDao diagnosticCodeDao = SpringUtils.getBean(DiagnosticCodeDao.class);
    ClinicLocationDao clinicLocationDao = (ClinicLocationDao) SpringUtils.getBean(ClinicLocationDao.class);
    BillingDetailDao billingDetailDao = SpringUtils.getBean(BillingDetailDao.class);
    BillingmasterDAO billingMasterDao = SpringUtils.getBean(BillingmasterDAO.class);
    TeleplanC12Dao teleplanC12Dao = SpringUtils.getBean(TeleplanC12Dao.class);
%>


<%
    List<Object[]> results = billingMasterDao.select_user_bill_report_wcb(Integer.parseInt(request.getParameter("billing_no")));
    TeleplanCorrectionFormWCB form = new TeleplanCorrectionFormWCB(results);
    Properties codes = new MspErrorCodes();
    BillingFormData billform = new BillingFormData();
    String billRegion = OscarProperties.getInstance().getProperty("billRegion", "BC");
    List<BillingFormData.BillingVisit> billvisit = billform.getVisitType(billRegion);
    request.setAttribute("billvisit", billvisit);
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title>OSCAR oscarBilling - Correction</title>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <link rel="stylesheet" href="<%= request.getContextPath() %>/share/css/oscar.css">
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"
                type="text/javascript"></script>
        <script src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"
                type="text/javascript"></script>

        <script type="text/javascript">
            function popFeeItemList(form, field) {

                var width = 575;

                var height = 400;

                var str = document.forms[form].elements[field].value;

                var url = "support/billingfeeitem.jsp?form=" + form + "&field=" + field + "&searchStr=" + str;

                var windowName = field;

                popup(height, width, url, windowName);

            }


            function popICD9List(form, field) {

                var width = 575;

                var height = 400;

                var str = document.forms[form].elements[field].value;

                var url = "support/icd9.jsp?form=" + form + "&field=" + field + "&searchStr=" + str;

                var windowName = field;

                popup(height, width, url, windowName);

            }


            function popBodyPartList(form, field) {

                var width = 650;

                var height = 400;

                var str = document.forms[form].elements[field].value;

                var url = "support/bodypart.jsp?form=" + form + "&field=" + field + "&searchStr=" + str;

                var windowName = field;

                popup(height, width, url, windowName);

            }


            function popNOIList(form, field) {

                var width = 800;

                var height = 400;

                var str = document.forms[form].elements[field].value;

                var url = "support/natureinjury.jsp?form=" + form + "&field=" + field + "&searchStr=" + str;

                var windowName = field;

                popup(height, width, url, windowName);

            }


        </script>
    </head>
    <body>
    <form action="${pageContext.request.contextPath}/billing/CA/BC/billingTeleplanCorrectionWCB.do" method="post">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr bgcolor="#000000">
                <td height="40" width="10%"></td>
                <td width="90%" align="left">
                    <p><font face="Verdana, Arial, Helvetica, sans-serif"
                             color="#FFFFFF"> <b> <font
                            face="Arial, Helvetica, sans-serif" size="4"> oscar <font
                            size="3">Billing - Correction</font> </font> </b> </font></p>
                </td>
            </tr>
        </table>
        Form Needed
        <input type="checkbox" value="1" name="formNeeded"
               onClick="isformNeeded();"/>
        <table width="100%">
            <tr>
                <td colspan="2" class="SectionHead"><a href=#
                                                       onClick="popup(700,900,'<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<%=form.getDemographicNumber()%>&displaymode=edit&dboperation=search_detail','
                                                           <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.popupPage2Window"/>');return false;"
                                                       title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgMasterFile"/>">Patient
                    Information</a> <input type="hidden" name="id" id="id" value="<%=form.getId()%>"/>
                    <input type="hidden"
                           name="demographicNumber" id="demographicNumber"
                        value="<%=form.getDemographicNumber()%>"/></td>
            </tr>
            <tr>
                <td width="50%" align="left" valign="top">
                    <table width="100%">
                        <tr>
                            <td class="FormLabel">First Name:</td>
                            <td><input type="text" name="firstName"
                                           value="<%=form.getFirstName()%>"/></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Middle Name:</td>
                            <td><input type="text" name="w_mname"
                                           value="<%=form.getW_mname()%>"/></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Last Name:</td>
                            <td><input type="text" name="lastName"
                                           value="<%=form.getLastName()%>"/></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Date Of Birth:</td>
                            <td><input type="text" name="yearOfBirth"
                                           value="<%=form.getYearOfBirth()%>" maxlength="4" size="4"/>
                                <input type="text" name="monthOfBirth" value="<%=form.getMonthOfBirth()%>"
                                    maxlength="2" size="2"/> <input type="text" name="dayOfBirth"
                                                                        value="<%=form.getDayOfBirth()%>" maxlength="2"
                                                                        size="2"/> Age:(
                                <%=form.getAge()%> )
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">HIN (PHN):</td>
                            <td><input type="checkbox" name="hin" value="<%=form.getHin()%>" /></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Gender:</td>
                            <td><input type="text" readonly="true" name="w_gender"
                                           value="<%=form.getW_gender()%>"/></td>
                        </tr>
                    </table>
                </td>
                <td width="50%" align="left" valign="top">
                    <table width="100%">
                        <tr>
                            <td width="100" class="FormLabel">Area:</td>
                            <td><input type="checkbox" name="w_area" value="<%=form.getW_area()%>" />
                            </td>
                        </tr>
                        <tr>
                            <td width="100" class="FormLabel">Phone:</td>
                            <td><input type="text" name="w_phone"
                                           value="<%=form.getW_phone()%>"/></td>
                        </tr>
                        <tr>
                            <td width="100" class="FormLabel">Address:</td>
                            <td><input type="text" name="address"
                                           value="<%=form.getAddress()%>"/></td>
                        </tr>
                        <tr>
                            <td width="100" class="FormLabel">City:</td>
                            <td><input type="checkbox" name="city" value="<%=form.getCity()%>" />
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Province:</td>
                            <td><input type="text" name="province"
                                           value="<%=form.getProvince()%>"/></td>
                        </tr>
                        <tr>
                            <td width="100" class="FormLabel">Postal:</td>
                            <td><input type="checkbox" name="postal" value="<%=form.getPostal()%>" />
                            </td>
                        </tr>
                    </table>
                </td>
                <!-- -->
            </tr>
        </table>
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr valign="top">
                <td width="50%">
                    <table width="100%">
                        <tr>
                            <td colspan="2" class="SectionHead">Employer Information</td>
                        </tr>
                        <tr>
                            <td width="50%" align="left" valign="top">
                                <table width="100%">
                                    <tr>
                                        <td class="FormLabel">Name:</td>
                                        <td><input type="text" name="w_empname"
                                                       value="<%=form.getW_empname()%>"/></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Area:</td>
                                        <td><input type="text" name="w_emparea"
                                                       value="<%=form.getW_emparea()%>"/></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Phone:</td>
                                        <td><input type="text" name="w_empphone"
                                                       value="<%=form.getW_empphone()%>"/></td>
                                    </tr>
                                </table>
                            </td>
                            <td width="50%" align="left" valign="top">
                                <table width="100%">
                                    <tr>
                                        <td width="175" class="FormLabel">Operating Address:</td>
                                        <td><input type="text" name="w_opaddress"
                                                       value="<%=form.getW_opaddress()%>"/></td>
                                    </tr>
                                    <tr>
                                        <td width="175" class="FormLabel">Operating City:</td>
                                        <td><input type="text" name="w_opcity"
                                                       value="<%=form.getW_opcity()%>"/></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
                <td>
                    <table width="100%">
                        <tr>
                            <td width="100%" colspan="4" class="SectionHead">Assessment</td>
                        </tr>
                        <tr>
                            <td>Capability:</td>
                            <td><select name="w_capability"
                                             value="<%=form.getW_capability()%>" id="w_capability">
                                <option value="Y">Yes</option>
                                <option value="N">No</option>
                            </select></td>
                            <td>Rehab:</td>
                            <td><select name="w_rehab" value="<%=form.getW_rehab()%>"
                                             id="w_rehab">
                                <option value="Y">Yes</option>
                                <option value="N">No</option>
                            </select></td>
                        </tr>
                        <tr>
                            <td>Rehab Type:</td>
                            <td><select name="w_rehabtype"
                                             value="<%=form.getW_rehabtype()%>" id="w_rehabtype">
                                <option value="C">Work Conditioning</option>
                                <option value="O">Other</option>
                            </select></td>
                            <td>To Follow:</td>
                            <td><select name="w_tofollow"
                                             value="<%=form.getW_tofollow()%>" id="w_tofollow">
                                <option value="Y">Yes</option>
                                <option value="N">No</option>
                            </select></td>
                        </tr>


            </tr>
            <td>Advisor:</td>
            <td><select name="w_wcbadvisor"
                             value="<%=form.getW_wcbadvisor()%>" id="w_wcbadvisor">
                <option value="Y">Yes</option>
                <option value="N">No</option>
            </select></td>
            </tr>
        </table>
        </td>
        </tr>
        </table>
        <table width="100%">
            <tr>
                <td colspan="2" class="SectionHead">Claim Information ( <%=form.getDataSequenceNo()%>
                    )
                </td>
            </tr>
            <tr>
                <td width="50%" align="left" valign="top">
                    <table width="100%">
                        <tr>
                            <td class="FormLabel">WCB Claim No:</td>
                            <td><input type="text" name="w_wcbno"
                                           value="<%=form.getW_wcbno()%>"/></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Billing Physician</td>
                            <td><select style="font-size:80%;" name="providerNo"
                                             value="<%=form.getProviderNo()%>">
                                <%
                                    String proFirst = "", proLast = "", proOHIP = "", proNo = "";
                                    for (Provider p : providerDao.getActiveProviders()) {
                                        if (p.getOhipNo() != null && !p.getOhipNo().isEmpty()) {
                                            proFirst = p.getFirstName();
                                            proLast = p.getLastName();
                                            proOHIP = p.getProviderNo();
                                %>
                                <option value="<%=proOHIP%>"><%=proOHIP%>                    |
                                    <%=proLast%>                    ,
                                    <%=proFirst%>
                                </option>
                                <%
                                        }
                                    }
                                %>
                            </select></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Practioner Num:</td>
                            <td><%=form.getW_pracno()%>
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Payee Num:</td>
                            <td><%=form.getW_payeeno()%>
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Regular Physician:</td>
                            <td><select name="w_rphysician"
                                             value="<%=form.getW_rphysician()%>" id="w_rphysician">
                                <option value="Y">Yes</option>
                                <option value="N">No</option>
                            </select></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Duration:</td>
                            <td><select name="w_duration"
                                             value="<%=form.getW_duration()%>" id="w_duration">
                                <option value="1">0-6 months</option>
                                <option value="2">7-12 months</option>
                                <option value="9"> &gt;
                                    12 months</option>
                            </select></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Billing Unit:</td>
                            <td><input type="text" name="billingUnit"
                                           value="<%=form.getBillingUnit()%>"/></td>
                        </tr>
                        <!--<tr>

					<td class="FormLabel">Billing Code:</td>

					<td><input type="checkbox" name="billingCode" value="<%=form.getBillingCode()%>" />

					<a onClick="popup('400', '600', 'support/billingcodes.jsp?form=TeleplanCorrectionFormWCB&field=billingCode', 'Code');">Service</a>

					</td>

				</tr>-->
                        <tr>
                            <td class="FormLabel">Bill Amount:</td>
                            <td><input type="text" name="billingAmount"
                                           value="<%=form.getBillingAmount()%>"/></td>
                        </tr>
                        <tr>
                            <td class="FormLabel" title="Internal Adjustment">Int. Adj.</td>
                            <td><label> Amount: <input name="adjAmount"
                                                       type="text" size="7" maxlength="7"> </label> <label> <input
                                    type="checkbox" name="adjType" value="1"/> debit </label></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">ICD 9:</td>
                            <td><input type="checkbox" name="w_icd9" value="<%=form.getW_icd9()%>" />
                                <a onClick="popICD9List('TeleplanCorrectionFormWCB','w_icd9');">Codes</a>
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Body Part:</td>
                            <td><input type="checkbox" name="w_bp" value="<%=form.getW_bp()%>" />
                                <a onClick="popBodyPartList('TeleplanCorrectionFormWCB','w_bp');">Codes</a>
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Side:</td>
                            <td colspan="2"><select name="w_side"
                                                         value="<%=form.getW_side()%>" id="w_side">
                                <option value="B">Left and Right</option>
                                <option value="L">Left</option>
                                <option value="N">Not Applicable</option>
                                <option value="R">Right</option>
                            </select></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Nature Of Injury:</td>
                            <td><input type="checkbox" name="w_noi" value="<%=form.getW_noi()%>" />
                                <a onClick="popNOIList('TeleplanCorrectionFormWCB','w_noi');">Codes</a>
                            </td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Fee Item:</td>
                            <td><input type="text" name="w_feeitem"
                                           value="<%=form.getW_feeitem()%>"/> <a
                                    onClick="popFeeItemList('TeleplanCorrectionFormWCB','w_feeitem');">Codes</a>
                            </td>
                        </tr>
                        <!--<tr>

					<td class="FormLabel">Fee Item:</td>

					<td><input type="checkbox" name="w_extrafeeitem" value="<%=form.getW_extrafeeitem()%>" />

					<a onClick="popup('400', '600', 'support/billingfeeitem.jsp?info=all&form=TeleplanCorrectionFormWCB&field=w_extrafeeitem', 'eFeeItem');">Codes</a></td>

				</tr>-->
                        <tr>
                            <td class="FormLabel">Service Location:</td>
                            <td><select name="serviceLocation"
                                             style="font-size:80%;" value="<%=form.getServiceLocation()%>">
                                <c:forEach var="bill" items="${billvisit}">
                                    <option value="${bill.visitType}">
                                            ${bill.description}
                                    </option>
                                </c:forEach>
                            </select></td>
                        </tr>
                        <tr>
                            <td class="FormLabel">Report Type:</td>
                            <td><input type="text" name="w_reporttype"
                                           value="<%=form.getW_reporttype()%>"/></td>
                        </tr>
                        <tr>


                            <td colspan="2">
                                <%
                                    String status = form.getStatus() != null ? form.getStatus() : "";
                                    String statusCodes[] = {"O", "P", "N", "B", "X", "T", "C", "D"};
                                    request.setAttribute("codes", statusCodes);

                                %>
                                <!-- includes the Billing Type Drop Down List -->
                                <jsp:include flush="false" page="billType_frag.jsp">
                                    <jsp:param name="BillType" value="<%=status%>"/>
                                </jsp:include>
                            </td>
                        </tr>
                    </table>
                </td>
                <td width="50%" align="left" valign="top">
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td>
                                <table width="100%">
                                    <tr>
                                        <td class="FormLabel">Disabled from Work:</td>
                                        <td>
                                            <select name="w_work" value="<%=form.getW_work()%>" property="w_work">
                                                <option value="Y">Yes</option>
                                                <option value="N">No</option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Date Of Injury:</td>
                                        <td>
                                            <input type="text" readonly="readonly" name="w_doi" value="<%=form.getW_doi()%>"
                                                       id="w_doi"/>
                                            <a id="hlIDate">Date</a>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Service Date:</td>
                                        <td>
                                            <input type="text" readonly="readonly" name="w_servicedate"
                                                       value="<%=form.getW_servicedate()%>" id="w_servicedate"/>
                                            <a id="hlSDate">Date</a>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Work Date:</td>
                                        <td>
                                            <input type="text" readonly="readonly" name="w_workdate"
                                                       value="<%=form.getW_workdate()%>" id="w_workdate"/>
                                            <a id="hlWDate">Date</a>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Estimate:</td>
                                        <td>
                                            <select name="w_estimate" value="<%=form.getW_estimate()%>">
                                                <option value="0">At Work</option>
                                                <option value="1">1-6 days</option>
                                                <option value="2">7-13 days</option>
                                                <option value="3">14-20 days</option>
                                                <option value="9"> &gt;
                                                    20 days
                                                </option>
                                            </select></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Estimate Date:</td>
                                        <td><input type="text" readonly="true" name="w_estimatedate"
                                                       value="<%=form.getW_estimatedate()%>" id="w_estimatedate"/>
                                            <a id="hlEDate">Date</a></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">First Treatment:</td>
                                        <td><input type="text" name="w_ftreatment"
                                                       value="<%=form.getW_ftreatment()%>"/></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Problem:</td>
                                        <td><textarea style="width:100%" name="w_problem" id="w_problem">
                                            <%=form.getW_problem()%>
                                        </textarea></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Diagnosis:</td>
                                        <td><textarea style="width:100%" name="w_diagnosis" id="w_diagnosis">
                                                           <%=form.getW_diagnosis()%>
                                        </textarea></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Clinical Info:</td>
                                        <td><textarea style="width:100%" name="w_clinicinfo" id="w_clinicinfo">
                                            <%=form.getW_clinicinfo()%>
                                        </textarea></td>
                                    </tr>
                                    <tr>
                                        <td class="FormLabel">Problem:</td>
                                        <td><textarea style="width:100%" name="w_capreason" id="w_capreason">
                                            <%=form.getW_capreason()%>
                                        </textarea></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr valign="top">
                <td width="50%">
                    <table width="100%" border="0">
                        <tr class="SectionHead">
                            <td>Error Report</td>
                        </tr>
                        <tr>
                            <td align="left" valign="top">
                                <ul>
                                    <%
                                        if ("" != request.getParameter("billing_no")) {

                                            String desc = null;
                                            for (TeleplanC12 result : teleplanC12Dao.select_c12_record("O", request.getParameter("billing_no"))) {

                                                String seqNum = result.getDataSeq();

                                                if (result.getExp1() != null && codes.get(result.getExp1()) != null && !((String) codes.get(result.getExp1())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp1() + " - " + codes.get(result.getExp1())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp2() != null && codes.get(result.getExp2()) != null && !((String) codes.get(result.getExp2())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp2() + " - " + codes.get(result.getExp2())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp3() != null && codes.get(result.getExp3()) != null && !((String) codes.get(result.getExp3())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp3() + " - " + codes.get(result.getExp3())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp4() != null && codes.get(result.getExp4()) != null && !((String) codes.get(result.getExp4())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp4() + " - " + codes.get(result.getExp4())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp5() != null && codes.get(result.getExp5()) != null && !((String) codes.get(result.getExp5())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp5() + " - " + codes.get(result.getExp5())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp6() != null && codes.get(result.getExp6()) != null && !((String) codes.get(result.getExp6())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp6() + " - " + codes.get(result.getExp6())%>
                                    </li>
                                    <%
                                        }
                                        if (result.getExp7() != null && codes.get(result.getExp7()) != null && !((String) codes.get(result.getExp7())).trim().equals("")) {
                                    %>
                                    <li><%=seqNum + " " + result.getExp7() + " - " + codes.get(result.getExp7())%>
                                    </li>
                                    <%
                                                }


                                            } //for results
                                        } //if billingNo

                                    %>
                                </ul>
                            </td>
                        </tr>
                    </table>
                </td>
                <td>
                    <jsp:include flush="false" page="billTransactions.jsp">
                        <jsp:param name="billNo" value="<%=form.getBillingNo()%>"/>
                    </jsp:include>
                </td>
            </tr>
        </table>
        <table width="100%">
        </table>
        <input type="hidden" name="billingNo" id="billingNo" value="<%=form.getBillingNo()%>"/>
        <input type="hidden" name="id" id="id" value="<%=form.getId()%>"/>
        <table width="100%">
            <tr>
                <td colspan="2" align="center" class="SectionHead"><a
                        href="billingTeleplanCorrectionWCB.jsp?billing_no=<%=form.getId()%>">Refresh</a>
                    | <input type="button" name="Button" value="Close"
                             onClick="window.close();"> | <input type="button"
                                                                 name="Button" value="Print" onClick="window.print();">
                    | <%if (!status.equals("S")) {%>
                    <input type="submit" name="submit" value="Submit" />
                    <input type="submit" name="submit" value="Settle Bill"/>
                    <%}%>
                </td>
            </tr>
        </table>
        <script language='javascript'>

            Calendar.setup({
                inputField: "w_servicedate",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "hlSDate",
                singleClick: true,
                step: 1
            });

            //Calendar.setup({inputField:"w_servidedate",ifFormat:"y-mm-dd",button:"hlSDate",align:"Bl",singleClick:true});

            //Calendar.setup({inputField:"w_doi",ifFormat:"y-mm-dd",button:"hlIDate",align:"Bl",singleClick:true});

            Calendar.setup({
                inputField: "w_doi",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "hlIDate",
                singleClick: true,
                step: 1
            });


            //Calendar.setup({inputField:"w_workdate",ifFormat:"y-mm-dd",button:"hlWDate",align:"Bl",singleClick:true});

            Calendar.setup({
                inputField: "w_workdate",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "hlWDate",
                singleClick: true,
                step: 1
            });

            //Calendar.setup({inputField:"w_estimatedate",ifFormat:"y-mm-dd",button:"hlEDate",align:"Bl",singleClick:true});

            Calendar.setup({
                inputField: "w_estimatedate",
                ifFormat: "%Y-%m-%d",
                showsTime: false,
                button: "hlEDate",
                singleClick: true,
                step: 1
            });

        </script>
    </form>
    </body>
</html>
<%!
    String checked(String val, String str, boolean dfault) {
        String retval = "";
        if (str == null || str.equals("null")) {
            str = "";
        }
        if (str.equals("") && dfault) {
            retval = "CHECKED";
        } else if (str != null && str.equalsIgnoreCase(val)) {
            retval = "CHECKED";
        }
        return retval;
    }
%>
