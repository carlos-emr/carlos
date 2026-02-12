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
<%
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = loggedInInfo.getLoggedInProviderNo();


    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@page import="io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.EForm" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.EncounterForm" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlBillingService" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="java.util.List" %>
<%@page import="java.util.ArrayList" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>

<%!
    CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    UserPropertyDAO propertyDao = SpringUtils.getBean(UserPropertyDAO.class);
%>

<html>

    <head>
        <c:set var="ctx" value="${pageContext.request.contextPath}"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.title"/></title>
        <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <link rel="stylesheet" href="${ctx}/library/bootstrap/5.0.2/css/bootstrap.min.css">
        <script src="${ctx}/library/bootstrap/5.0.2/js/bootstrap.bundle.min.js"></script>
        <script language="JavaScript">

            function setfocus() {
                this.focus();
                document.UPDATEPRE.mygroup_no.focus();
                document.UPDATEPRE.mygroup_no.select();
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function checkTypeNum(typeIn) {
                var typeInOK = true;
                var i = 0;
                var length = typeIn.length;
                var ch;
                // walk through a string and find a number
                if (length >= 1) {
                    while (i < length) {
                        ch = typeIn.substring(i, i + 1);
                        if (ch == ".") {
                            i++;
                            continue;
                        }
                        if ((ch < "0") || (ch > "9")) {
                            typeInOK = false;
                            break;
                        }
                        i++;
                    }
                } else typeInOK = false;
                return typeInOK;
            }

            function checkTypeIn(obj) {
                if (!checkTypeNum(obj.value)) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.msgMustBeNumber"/>");
                }
            }

            function checkTypeInAll() {
                var checkin = false;
                var s = 0;
                var e = 0;
                var i = 0;
                if (isNumeric(document.UPDATEPRE.start_hour.value) && isNumeric(document.UPDATEPRE.end_hour.value) && isNumeric(document.UPDATEPRE.every_min.value)) {
                    s = eval(document.UPDATEPRE.start_hour.value);
                    e = eval(document.UPDATEPRE.end_hour.value);
                    i = eval(document.UPDATEPRE.every_min.value);
                    if (e < 24) {
                        if (s < e) {
                            if (i <= (e - s) * 60 && i > 0) {
                                checkin = true;
                            } else {
                                alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.msgPositivePeriod"/>");
                                this.focus();
                                document.UPDATEPRE.every_min.focus();
                            }
                        } else {
                            alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.msgStartHourErlierEndHour"/>");
                            this.focus();
                            document.UPDATEPRE.start_hour.focus();
                        }
                    } else {
                        alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.msgHourLess24"/>");
                        this.focus();
                        document.UPDATEPRE.end_hour.focus();
                    }
                } else {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.msgTypeNumbers"/>");
                }
                return checkin;
            }

            function isNumeric(strString) {
                var validNums = "0123456789";
                var strChar;
                var retval = true;
                if (strString.length == 0) {
                    retval = false;
                }
                for (i = 0; i < strString.length && retval == true; i++) {
                    strChar = strString.charAt(i);
                    if (validNums.indexOf(strChar) == -1) {
                        retval = false;
                    }
                }
                return retval;
            }

            function showHideBillPref() {
                $("billingONpref").toggle();
            }

            function showHideERxPref() {
                //$("eRxPref").toggle();
            }
        </script>
        <style type="text/css">
            body { background: #f3f6fb; }
            .pref-shell { max-width: 1100px; margin: 1.5rem auto; }
            .pref-card { border: 0; border-radius: .85rem; box-shadow: 0 0.35rem 1rem rgba(15, 23, 42, .08); }
            .pref-header { background: linear-gradient(135deg, #5f73c8, #7f91de); color: #fff; border-radius: .85rem .85rem 0 0; }
            .preferenceTable td { border: 0; padding: .5rem .75rem; }
            .preferenceLabel { width: 35%; font-size: .92rem; font-weight: 600; color: #334155; vertical-align: top; }
            .preferenceUnits { font-size: .72rem; font-weight: 500; color: #64748b; }
            .preferenceValue { font-size: .92rem; color: #0f172a; }
            .preferenceValue input[type='text'], .preferenceValue input[type='password'], .preferenceValue select,
            .eRxTableCenter input[type='text'], .eRxTableCenter input[type='password'] { border: 1px solid #cbd5e1; border-radius: .375rem; padding: .25rem .5rem; }
            .pref-link-btn { display: inline-block; text-decoration: none; padding: .4rem .75rem; border-radius: .375rem; background: #e2e8f0; color: #1e293b; font-weight: 600; }
            .pref-link-btn:hover { background: #cbd5e1; color: #0f172a; }
            table.eRxTableCenter { width: 100%; margin: 0; }
        </style>
    </head>

    <%
        ProviderPreference providerPreference = ProviderPreferencesUIBean.getProviderPreference(providerNo);

        if (providerPreference == null) {
            providerPreference = new ProviderPreference();
        }

        String startHour = request.getParameter("start_hour") != null ? request.getParameter("start_hour") : providerPreference.getStartHour().toString();
        String endHour = request.getParameter("end_hour") != null ? request.getParameter("end_hour") : providerPreference.getEndHour().toString();
        String everyMin = request.getParameter("every_min") != null ? request.getParameter("every_min") : providerPreference.getEveryMin().toString();
        String myGroupNo = request.getParameter("mygroup_no") != null ? request.getParameter("mygroup_no") : providerPreference.getMyGroupNo();
        String newTicklerWarningWindow = request.getParameter("new_tickler_warning_window") != null ? request.getParameter("new_tickler_warning_window") : providerPreference.getNewTicklerWarningWindow();
        String ticklerProviderNo = request.getParameter("tklerproviderno");
        String defaultPMM = request.getParameter("default_pmm") != null ? request.getParameter("default_pmm") : providerPreference.getDefaultCaisiPmm();
        String caisiBillingNotDelete = request.getParameter("caisiBillingPreferenceNotDelete") != null ? request.getParameter("caisiBillingPreferenceNotDelete") : String.valueOf(providerPreference.getDefaultDoNotDeleteBilling());

        //TODO add proper user interface for this billing setting in Ontario?
        // String defaultBillingLocation = providerPreference.getDefaultBillingLocation()!=null?providerPreference.getDefaultBillingLocation():"no";
    %>

    <body onLoad="setfocus();showHideBillPref();showHideERxPref();">
    <div class="container-fluid pref-shell">
    <FORM NAME="UPDATEPRE" METHOD="post" ACTION="providerupdatepreference.jsp" onSubmit="return(checkTypeInAll())" class="card pref-card">

        <div class="card-header text-center fw-bold pref-header">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.description"/>
        </div>

        <div class="card-body bg-white"><table class="preferenceTable table align-middle" style="width:100%;border-collapse:separate;">
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.preference.formStartHour"/>
                    <span class="preferenceUnits">(0-23)</span>
                </td>
                <td class="preferenceValue">
                    <INPUT TYPE="TEXT" NAME="start_hour" VALUE='<%=startHour%>' size="2" maxlength="2">
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.preference.formEndHour"/>
                    <span class="preferenceUnits">(0-23)</span>
                </td>
                <td class="preferenceValue">
                    <INPUT TYPE="TEXT" NAME="end_hour" VALUE='<%=endHour%>' size="2" maxlength="2">
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.preference.formPeriod"/>
                    <span class="preferenceUnits"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.preference.min"/></span>
                </td>
                <td class="preferenceValue">
                    <INPUT TYPE="TEXT" NAME="every_min" VALUE='<%=everyMin%>' size="2" maxlength="2">
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.preference.formGroupNo"/>
                </td>
                <td class="preferenceValue">
                    <INPUT TYPE="TEXT" NAME="mygroup_no" VALUE='<%=myGroupNo%>' size="12" maxlength="10">
                    <a href="providerdisplaymygroup.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.viewedit"/></a>
                </td>
            </tr>
            <!-- ticklerPlus removed -->

            <!-- QR Code on prescriptions setting -->
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.qrCodeOnPrescriptions"/>
                </td>
                <td class="preferenceValue">
                    <%
                        boolean checked = PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider(providerNo);
                    %>
                    <input type="checkbox" name="prescriptionQrCodes" <%=checked ? "checked=\"checked\"" : ""%> />
                </td>
            </tr>

                <%-- links to display on the appointment screen --%>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.appointmentScreenLinkNameDisplayLength"/>
                </td>
                <td class="preferenceValue">
                    <input type="text" name="appointmentScreenFormsNameDisplayLength"
                           value='<%=providerPreference.getAppointmentScreenLinkNameDisplayLength()%>' size="2">
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.formsToDisplayOnAppointmentScreen"/>
                </td>
                <td class="preferenceValue">
                    <div style="height:10em;border:solid grey 1px;overflow:auto;white-space:nowrap;width:45em">
                        <%
                            List<EncounterForm> encounterForms = ProviderPreferencesUIBean.getAllEncounterForms();
                            Collection<String> checkedEncounterFormNames = ProviderPreferencesUIBean.getCheckedEncounterFormNames(providerNo);
                            for (EncounterForm encounterForm : encounterForms) {
                                String nameEscaped = StringEscapeUtils.escapeHtml4(encounterForm.getFormName());
                                String checkedString = (checkedEncounterFormNames.contains(encounterForm.getFormName()) ? "checked=\"checked\"" : "");
                        %>
                        <input type="checkbox" name="encounterFormName"
                               value="<%=nameEscaped%>" <%=checkedString%> /> <%=nameEscaped%>
                        <br/>
                        <%
                            }
                        %>
                    </div>
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.eFormsToDisplayOnAppointmentScreen"/>
                </td>
                <td class="preferenceValue">
                    <div style="height:10em;border:solid grey 1px;overflow:auto;white-space:nowrap;width:45em">
                        <%
                            List<EForm> eforms = ProviderPreferencesUIBean.getAllEForms();
                            Collection<ProviderPreference.EformLink> checkedEFormIds = ProviderPreferencesUIBean.getCheckedEFormIds(providerNo);
                            for (EForm eform : eforms) {
                                String checkedString = "";
                                inner:
                                for (ProviderPreference.EformLink eformLink : checkedEFormIds) {
                                    if (eform.getId().equals(eformLink.getAppointmentScreenEForm())) {
                                        checkedString = "checked";
                                        break inner;
                                    }
                                }

                        %>
                        <input type="checkbox" name="eformId"
                               value="<%=eform.getId()%>" <%=checkedString%> /> <%=StringEscapeUtils.escapeHtml4(eform.getFormName())%>
                        <br/>
                        <%
                            }
                        %>
                    </div>
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.quickLinksToDisplayOnAppointmentScreen"/>
                </td>
                <td class="preferenceValue">
                    <div style="height:10em;border:solid grey 1px;overflow:auto;white-space:nowrap;width:45em">
                        <%
                            Collection<ProviderPreference.QuickLink> quickLinks = ProviderPreferencesUIBean.getQuickLinks(providerNo);
                            for (ProviderPreference.QuickLink quickLink : quickLinks) {
                        %>
                        <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="REMOVE"/>"
                               onclick="document.location='providerPreferenceQuickLinksAction.jsp?action=remove&name='+escape('<%=StringEscapeUtils.escapeHtml4(quickLink.getName())%>')"/>
                        <%=StringEscapeUtils.escapeHtml4(quickLink.getName())%>
                        : <%=StringEscapeUtils.escapeHtml4(quickLink.getUrl())%>
                        <br/>
                        <%
                            }
                        %>
                    </div>
                    <table style="border:none;border-collapse:collapse">
                        <tr>
                            <td style="border:none;text-align:right"><fmt:setBundle basename="oscarResources"/><fmt:message key="NAME"/></td>
                            <td style="border:none"><input type="text" name="quickLinkName"/></td>
                        </tr>
                        <tr>
                            <td style="border:none;text-align:right;vertical-align:top"><fmt:setBundle basename="oscarResources"/><fmt:message key="URL"/></td>
                            <td style="border:none">
                                <input type="text" name="quickLinkUrl"/>
                                <div style="font-size:9px">(expanded tokens in the url are ${contextPath}
                                    and ${demographicId})
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td style="border:none"></td>
                            <td style="border:none">
                                <script type="text/javascript">
                                    function addQuickLink() {
                                        name = escape(document.UPDATEPRE.quickLinkName.value);
                                        url = escape(document.UPDATEPRE.quickLinkUrl.value);
                                        document.location = "providerPreferenceQuickLinksAction.jsp?action=add&name=" + name + "&url=" + url;
                                    }
                                </script>
                                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ADD"/>" onclick="addQuickLink()"/>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="preferenceLabel">
                    Show Weekends in Week View:
                </td>
                <td class="preferenceValue">
                    <%
                        UserProperty showWeekendsProp = propertyDao.getProp(providerNo, UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
                        boolean weekendsEnabled = showWeekendsProp == null || Boolean.parseBoolean(showWeekendsProp.getValue());
                    %>
                    <input type="checkbox" name="schedule.week_view_weekends"
                           value="true" <%=weekendsEnabled ? "checked=\"checked\"" : ""%> />
                </td>
            </tr>
            <tr>
                <%

                    UserProperty prop = propertyDao.getProp(providerNo, "rxInteractionWarningLevel");
                    String warningLevel = "0";
                    if (prop != null) {
                        warningLevel = prop.getValue();
                    }
                %>
                <td class="preferenceLabel">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.rxInteractionWarningLevel"/>
                </td>
                <td class="preferenceValue">
                    <select id="rxInteractionWarningLevel">
                        <option value="0" <%=(warningLevel.equals("0") ? "selected=\"selected\"" : "") %>>Not
                            Specified
                        </option>
                        <option value="1" <%=(warningLevel.equals("1") ? "selected=\"selected\"" : "") %>>Low</option>
                        <option value="2" <%=(warningLevel.equals("2") ? "selected=\"selected\"" : "") %>>Medium
                        </option>
                        <option value="3" <%=(warningLevel.equals("3") ? "selected=\"selected\"" : "") %>>High</option>
                        <option value="4" <%=(warningLevel.equals("4") ? "selected=\"selected\"" : "") %>>None</option>
                    </select>
                </td>
                <script>
                    Event.observe('rxInteractionWarningLevel', 'change', function (event) {
                        var value = $('rxInteractionWarningLevel').getValue();

                        new Ajax.Request('<c:out value="${ctx}"/>/provider/rxInteractionWarningLevel.do?method=update&value=' + value, {
                            method: 'get',
                            onSuccess: function (transport) {
                            }
                        });

                    });

                </script>
            </tr>

            <tr>
                <%
                    Integer h = 0;
                    Integer mins = 0;
                    prop = propertyDao.getProp(providerNo, UserProperty.OSCAR_MSG_RECVD);
                    if (prop != null) {
                        String[] tmp = prop.getValue().split(":");
                        h = Integer.valueOf(tmp[0]);
                        mins = Integer.valueOf(tmp[1]);
                    }
                %>
                <td class="preferenceLabel">
                    Select when you want to receive Review Messages

                </td>
                <td preferenceValue>
                    <select id="reviewMsg" name="reviewMsg">
                        <%
                            for (int hr = 0; hr < 24; ++hr) {
                                for (int min = 0; min < 60; min += 30) {
                        %>
                        <option value="<%=String.valueOf(hr)+":"+String.valueOf(min) %>" <%= hr == h && min == mins ? "selected" : ""%> ><%=String.valueOf(hr) + " : " + String.valueOf(min) + (min == 0 ? "0" : "") %>
                        </option>
                        <%
                                }
                            }
                        %>
                    </select>
                </td>
            </tr>
            <script>
                Event.observe('reviewMsg', 'change', function (event) {
                    var value = $('reviewMsg').getValue();

                    new Ajax.Request('<c:out value="${ctx}"/>/setProviderStaleDate.do?method=OscarMsgRecvd&value=' + value + '&provider_no=<%=providerNo%>', {
                        method: 'get',
                        onSuccess: function (transport) {
                        }
                    });

                });
            </script>
        </table>

        <div class="card-header text-center fw-bold pref-header">
            <INPUT TYPE="submit" VALUE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerpreference.btnSubmit"/>' SIZE="7">
            <INPUT TYPE="RESET" VALUE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>' onClick="window.close();">
        </div>

        <INPUT TYPE="hidden" NAME="color_template" VALUE='deepblue'>


        <table width="100%" BGCOLOR="eeeeee">

            <caisi:isModuleLoad moduleName="NEW_CME_SWITCH">
                <oscar:oscarPropertiesCheck property="TORONTO_RFQ" value="no">
                    <tr>
                        <TD align="center"><a href="<%= request.getContextPath() %>/casemgmt/newCaseManagementEnable.jsp" class="pref-link-btn">Enable
                            OSCAR CME UI</a> &nbsp;&nbsp;&nbsp;
                    </tr>
                </oscar:oscarPropertiesCheck>
            </caisi:isModuleLoad>

            <tr>
                <td align="center"><a href="providerDefaultDxCode.jsp?provider_no=<%=request.getParameter("provider_no") %>" class="pref-link-btn">Edit
                    Default Billing Diagnostic Code</a>&nbsp;&nbsp;&nbsp;
                </td>
            </tr>
            <tr>

                <TD align="center"><a href="providerchangepassword.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnChangePassword"/></a> &nbsp;&nbsp;&nbsp;
                </td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewDefaultSex" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetDefaultSex"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="providerSignature.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditSignature"/></a>
                </td>
            </tr>
            <oscar:oscarPropertiesCheck property="TORONTO_RFQ" value="no" defaultVal="true">
                <security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r">
                    <tr>
                        <td align="center">
                            <% String br = OscarProperties.getInstance().getProperty("billregion");
                                if (br.equals("BC")) { %>
                            <a href="<%=request.getContextPath()%>/billing/CA/BC/viewBillingPreferencesAction.do?providerNo=<%=providerNo%>" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnBillPreference"/></a>
                            <% } else { %>
                            <a href="#" class="pref-link-btn" onClick="showHideBillPref();return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnBillPreference"/></a>
                            <% } %>
                        </td>
                    </tr>
                    <tr>
                        <td align="center">
                            <div id="billingONpref">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.labelDefaultBillForm"/>:
                                <select name="default_servicetype">
                                    <option value="no">-- no --</option>
                                    <%
                                        if (providerPreference != null) {
                                            String def = providerPreference.getDefaultServiceType();
                                            for (Object[] result : ctlBillingServiceDao.getUniqueServiceTypes("A")) {

                                    %>
                                    <option value="<%=(String)result[0]%>"
                                            <%=((String) result[0]).equals(def) ? "selected" : ""%>>
                                        <%=(String) result[1]%>
                                    </option>
                                    <%
                                        }
                                    } else {
                                        for (Object[] result : ctlBillingServiceDao.getUniqueServiceTypes("A")) {
                                    %>
                                    <option value="<%=(String)result[0]%>"><%=(String) result[1]%>
                                    </option>
                                    <%
                                            }
                                        }
                                    %>
                                </select>
                            </div>
                        </td>
                    </tr>
                </security:oscarSec>
                <tr>
                    <td align="center"><a href="providerAddress.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditAddress"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="providerPhone.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditPhoneNumber"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="providerFax.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditFaxNumber"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="providerColourPicker.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditColour"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="providerPrinter.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetDefaultPrinter"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewRxPageSize" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetRxPageSize"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewUseRx3" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetRx3"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewCppSingleLine" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetCppSingleLine"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewShowPatientDOB" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetShowPatientDOB"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewDefaultQuantity" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.SetDefaultPrescriptionQuantity"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=view&provider_no=<%=providerNo%>" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditStaleDate"/></a></td>
                </tr>


                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewConsultationRequestCuffOffDate" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetConsultationCutoffTimePeriod"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewConsultationRequestTeamWarning" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetConsultationTeam"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewWorkLoadManagement" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetWorkLoadManagement"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewConsultPasteFmt" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetConsultPasteFmt"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewFavouriteEformGroup" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetEformGroup"/></a></td>
                </tr>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewHCType" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetHCType"/></a></td>
                </tr>
                <% if (OscarProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
                <tr>
                    <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewOntarioMDId" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetmyOntarioMD"/></a></td>
                </tr>
                <%}%>
            </oscar:oscarPropertiesCheck>

            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/provider/CppPreferences.do" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.cppPrefs"/></a></td>
            </tr>

            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewCommentLab" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnDisableAckCommentLab"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewLabRecall" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnLabRecallSettings"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewEncounterWindowSize" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditDefaultEncounterWindowSize"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewQuickChartSize" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditDefaultQuickChartSize"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewEDocBrowserInDocumentReport" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetEDocBrowserInDocumentReport"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewEDocBrowserInMasterFile" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetEDocBrowserInMasterFile"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewPatientNameLength" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditSetPatientNameLength"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%= request.getContextPath() %>/admin/displayDocumentDescriptionTemplate.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetDocumentDescriptionTemplate"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="clients.jsp" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditClients"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewDisplayDocumentAs" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnSetDisplayDocumentAs"/></a></td>
            </tr>
            <tr>
                <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewAppointmentCardPrefs" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnEditSetAppointmentCardPrefs"/></a></td>
            </tr>

            <oscar:oscarPropertiesCheck property="util.erx.enabled" value="true">
            <security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r">
            <tr>
                <td align="center">
                    <a href="#" class="pref-link-btn" onClick="showHideERxPref();return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.btnPrefLink"/></a>
                </td>
            </tr>
            <tr>
                <td align="center">
                    <div id="eRxPref">
                                <%
            	String eRxEnabledChecked="unchecked";
                String eRxTrainingModeChecked="unchecked";
                                        
				boolean eRxEnabled = false;
                String eRx_SSO_URL = "";
                String eRxUsername = "";
                String eRxPassword = "";
                String eRxFacility = "";
                boolean eRxTrainingMode = false;
                                                        
                if (providerPreference != null){                                       
                	eRxEnabled = providerPreference.isERxEnabled();
                    if(eRxEnabled) eRxEnabledChecked = "checked";
                                
                    eRx_SSO_URL = providerPreference.getERx_SSO_URL();
                    eRxUsername = providerPreference.getERxUsername();
                    eRxPassword = providerPreference.getERxPassword();
                    eRxFacility = providerPreference.getERxFacility();
                                
                    eRxTrainingMode = providerPreference.isERxTrainingMode();
                    if(eRxTrainingMode) eRxTrainingModeChecked = "checked";
                                
                    if(eRx_SSO_URL==null || "null".equalsIgnoreCase(eRx_SSO_URL)) eRx_SSO_URL=OscarProperties.getInstance().getProperty("util.erx.oscarerx_sso_url");
                    if(eRxUsername==null || "null".equalsIgnoreCase(eRxUsername)) eRxUsername="";
                    if(eRxPassword==null || "null".equalsIgnoreCase(eRxPassword)) eRxPassword="";
                    if(eRxFacility==null || "null".equalsIgnoreCase(eRxFacility)) eRxFacility="";
                }
                %>
                        <table class="eRxTableCenter">
                            <tr>
                                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelEnable"/>:</td>
                                <td><input name="erx_enable" title="Enable the External Prescriber"
                                           type="checkbox" <%=eRxEnabledChecked%> /></td>
                            </tr>
                            <tr>
                                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelUser"/>:</td>
                                <td><input name="erx_username" type="text" value="<%=eRxUsername%>"
                                           title="Username to access the External Prescriber"/></td>
                            </tr>
                            <tr>
                                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelPassword"/>:</td>
                                <td><input name="erx_password" type="password" value="<%=eRxPassword%>"
                                           title="Password to access the External Prescriber"/></td>
                            <tr>
                            </tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelFacility"/>:</td>
                            <td><input name="erx_facility" type="text" value="<%=eRxFacility%>"
                                       title="The Facility ID assigned to you by the External Prescriber"/><br></td>
                            <tr>
                            </tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelTrainingMode"/>:</td>
                            <td><input name="erx_training_mode" type="checkbox"
                                       title="Enable Training Mode" <%=eRxTrainingModeChecked%> /></td>
            </tr>
            <tr>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.eRx.labelURL"/>:</td>
                <td><input name="erx_sso_url" type="text" value="<%=eRx_SSO_URL%>"
                           title="The URL to access the Web Interface from OSCAR Rx"/></td>
            </tr>

        </table>
        </div>
        </td>
        </tr>
        </security:oscarSec>
        </oscar:oscarPropertiesCheck>
        <tr>
            <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewDashboardPrefs" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnViewDashboardPrefs"/></a></td>
        </tr>
        <tr>
            <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewPreventionPrefs" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnViewPreventionPrefs"/></a></td>
        </tr>

        <tr>
            <td align="center"><a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewLabMacroPrefs" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnViewLabMacroPrefs"/></a></td>
        </tr>
        <tr>
            <td align="center"><a href="<%=request.getContextPath()%>/setTicklerPreferences.do?method=viewTicklerTaskAssignee" class="pref-link-btn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.btnViewTicklerPreferences"/></a></td>
        </tr>
        </table></div>
    </FORM>
    </div>

    </body>
</html>
