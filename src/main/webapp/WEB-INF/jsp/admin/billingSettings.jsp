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
<!DOCTYPE HTML>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PropertyDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Property" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.data.BillingFormData" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.clinic.ClinicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.SystemPreferences" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao" %>
<%@ page import="java.util.*" %>
<fmt:setBundle basename="oscarResources"/>


<jsp:useBean id="dataBean" class="java.util.Properties"/>
<%!
    PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
    BillingFormData billingFormData = new BillingFormData();
    SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
%>
<%


    String billRegion = CarlosProperties.getInstance().getProperty("billregion", "").trim();
    List<String> billingSettingsKeys = Arrays.asList("auto_populate_refer", "bc_default_service_location", "default_billing_form");

    /*
     * Save on page reload.
     * TODO: not really the best method, but will work until there is time to refactor.
     */
    if (request.getParameter("dboperation") != null && !request.getParameter("dboperation").isEmpty() && request.getParameter("dboperation").equals("Save")
            && "POST".equalsIgnoreCase(request.getMethod())) {

        request.setAttribute("success", false);

        // save billing settings into Properties table.
        for (String key : billingSettingsKeys) {
            List<Property> property = propertyDao.findGlobalByName(key);
            String newValue = request.getParameter(key);

            if (property.isEmpty()) {
                Property newProperty = new Property();
                newProperty.setName(key);
                newProperty.setValue(newValue);
                propertyDao.persist(newProperty);
            } else {
                for (Property p : property) {
                    p.setValue(newValue);
                    propertyDao.merge(p);
                }
            }
        }

        // save system settings
        for (SystemPreferences.GENERAL_SETTINGS_KEYS key : SystemPreferences.GENERAL_SETTINGS_KEYS.values()) {
            // do not override the enumerator!
            String newValue = request.getParameter(key.name());
            SystemPreferences currentValue = systemPreferencesDao.findPreferenceByName(key);
            if (currentValue == null) {
                SystemPreferences systemPreferences = new SystemPreferences();
                systemPreferences.setName(key.name());
                systemPreferences.setValue(newValue);
                systemPreferences.setUpdateDate(new Date());
                systemPreferencesDao.persist(systemPreferences);
            } else {
                // if the custom clinic info is set to off then the info should not be saved
                if (key.equals(SystemPreferences.GENERAL_SETTINGS_KEYS.invoice_custom_clinic_info)
                        && !"on".equals(request.getParameter(SystemPreferences.GENERAL_SETTINGS_KEYS.invoice_use_custom_clinic_info.name()))) {
                    continue;
                }
                currentValue.setValue(newValue);
                currentValue.setUpdateDate(new Date());
                systemPreferencesDao.merge(currentValue);
            }
        }

        request.setAttribute("success", true);
    }


    for (String key : billingSettingsKeys) {
        List<Property> properties = propertyDao.findGlobalByName(key);
        if (!properties.isEmpty() && properties.getFirst().getName() != null && properties.getFirst().getValue() != null) {
            dataBean.setProperty(properties.getFirst().getName(), properties.getFirst().getValue());
        }
    }

    List<SystemPreferences> preferences = systemPreferencesDao.findPreferencesByNames(SystemPreferences.GENERAL_SETTINGS_KEYS.class);
    for (SystemPreferences preference : preferences) {
        dataBean.setProperty(preference.getName(), preference.getValue());
    }

    pageContext.setAttribute("clinicData", new ClinicData());
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.billingSettings.title"/></title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script>
            function hasScrollbar(element_id) {
                var elem = document.getElementById(element_id);
                if (elem.clientHeight < elem.scrollHeight) {
                    document.getElementById("warning_text").style.visibility = "visible";
                } else {
                    document.getElementById("warning_text").style.visibility = "hidden";
                }
            }

            function setClinicInfo() {
                let useCustom = document.getElementById('invoice_use_custom_clinic_info');
                let clinicInfo = document.getElementById('invoice_custom_clinic_info');
                clinicInfo.disabled = useCustom === null || !useCustom.checked;
            }
        </script>
    </head>

    <body vlink="#0000FF" class="BodyStyle">

    <h4><fmt:message key="admin.billingSettings.heading"/></h4>
    <form name="billingSettingsForm" method="post" action="${pageContext.request.contextPath}/admin/BillingSettings">

        <input type="hidden" name="dboperation" value="">
        <table id="displaySettingsTable" class="table table-bordered table-striped table-hover table-sm">
            <tbody>
            <oscar:oscarPropertiesCheck property="billregion" value="BC">
                <tr>
                    <td><fmt:message key="admin.billingSettings.autoPopulateRefer"/>:</td>
                    <td>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" id="auto_populate_refer-true" type="radio" value="true" name="auto_populate_refer"
                                    <%=(dataBean.getProperty("auto_populate_refer", "false").equals("true")) ? "checked" : ""%> />
                            <label class="form-check-label" for="auto_populate_refer-true"><fmt:message key="global.yes"/></label>
                        </div>
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" id="auto_populate_refer-false" type="radio" value="false" name="auto_populate_refer"
                                    <%=(dataBean.getProperty("auto_populate_refer", "false").equals("false")) ? "checked" : ""%> />
                            <label class="form-check-label" for="auto_populate_refer-false"><fmt:message key="global.no"/></label>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td><label for="bc_default_service_location"><fmt:message key="admin.billingSettings.defaultServiceLocation"/></label></td>
                    <td>
                        <select id="bc_default_service_location" name="bc_default_service_location">
                            <%
                                List<BillingFormData.BillingVisit> billingVisits = billingFormData.getVisitType(billRegion);
                                String defaultServiceLocation = dataBean.getProperty("bc_default_service_location", "");
                                if (StringUtils.isNullOrEmpty(defaultServiceLocation)) {
                                    // Get the visittype property
                                    defaultServiceLocation = CarlosProperties.getInstance().getProperty("visittype");
                                }

                                // this captures and modifies any legacy codes that may be still hanging around
                                if (defaultServiceLocation.contains("|")) {
                                    defaultServiceLocation = defaultServiceLocation.split("\\|")[0].trim();
                                }

                                for (BillingFormData.BillingVisit billingVisit : billingVisits) {
                            %>
                            <option value="<carlos:encode value='<%= billingVisit.getVisitType() %>' context="htmlAttribute"/>" <%= billingVisit.getVisitType().equalsIgnoreCase(defaultServiceLocation) ? "selected" : ""%>>
                                <carlos:encode value='<%= billingVisit.getDescription() %>' context="html"/>
                            </option>
                            <% } %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td><label for="default_billing_form"><fmt:message key="admin.billingSettings.defaultBillingForm"/></label></td>
                    <td>
                        <select id="default_billing_form" name="default_billing_form">
                            <%
                                BillingFormData.BillingForm[] billformlist = billingFormData.getFormList();
                                String currentSelection = CarlosProperties.getInstance().getProperty("default_view");
                                String currentUserSetting = dataBean.getProperty("default_billing_form");
                                // current user setting overrides the carlos properties setting
                                if (currentUserSetting != null && !currentUserSetting.isEmpty()) {
                                    currentSelection = currentUserSetting;
                                }
                                currentSelection = currentSelection.trim();
                                for (BillingFormData.BillingForm billingForm : billformlist) {
                            %>
                            <option value="<carlos:encode value='<%= billingForm.getFormCode() %>' context="htmlAttribute"/>" <%= billingForm.getFormCode().equalsIgnoreCase(currentSelection) ? "selected" : "" %> >
                                <carlos:encode value='<%= billingForm.getDescription() %>' context="html"/>
                            </option>
                            <% } %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td><fmt:message key="admin.billingSettings.clinicInfo"/></td>
                    <td>
                        <div class="form-check">
                            <input type="checkbox" class="form-check-input" id="invoice_use_custom_clinic_info"
                                   name="invoice_use_custom_clinic_info"
                                   onclick="setClinicInfo()" ${ "on" eq dataBean["invoice_use_custom_clinic_info"] ? "checked" : ""} />
                            <label class="form-check-label" for="invoice_use_custom_clinic_info"><fmt:message key="admin.billingSettings.useCustom"/></label>
                        </div>

                        <br>
                        <textarea style="resize: none;" rows="5" id="invoice_custom_clinic_info"
                                  name="invoice_custom_clinic_info" maxlength="250"
                            ${empty dataBean["invoice_use_custom_clinic_info"] ? "disabled" : ""} >${carlos:forHtmlContent("on" eq dataBean["invoice_use_custom_clinic_info"] ? dataBean["invoice_custom_clinic_info"] : clinicData.label)}</textarea>
                    </td>
                </tr>
            </oscar:oscarPropertiesCheck>

            <oscar:oscarPropertiesCheck property="billregion" value="ON">
                <tr>
                    <td><fmt:message key="admin.billingSettings.noOptions"/></td>
                </tr>
            </oscar:oscarPropertiesCheck>
            </tbody>
        </table>
        <input type="button"
               onclick="document.forms['billingSettingsForm'].dboperation.value='Save'; document.forms['billingSettingsForm'].submit();"
               name="saveBillingSettings" value="<fmt:message key='global.save'/>"/>
        <%
            Boolean success = (Boolean) request.getAttribute("success");
            if (success != null && success) {
        %>
        <span style="color:green;"><fmt:message key="admin.billingSettings.saved"/></span>
        <%
            }
        %>
    </form>
    </body>
</html>
