<%@ taglib prefix="oscar" uri="/oscarPropertiestag" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@page import="java.util.*,ca.openosp.openo.util.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.commn.model.SystemPreferences" %>
<%@ page import="ca.openosp.openo.commn.dao.PropertyDao" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Property" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.commn.dao.SystemPreferencesDao" %>
<%@ page import="ca.openosp.openo.clinic.ClinicData" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreference" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingPreferencesDAO" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%
    SystemPreferencesDao systemPreferencesDao = SpringUtils.getBean(SystemPreferencesDao.class);
    PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
    BillingPreferencesDAO billingPreferencesDAO = SpringUtils.getBean(BillingPreferencesDAO.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    BillingPreference billingPreference = billingPreferencesDAO.getUserBillingPreference(provider.getProviderNo());
    boolean autoPopulateRefer = propertyDao.isActiveBooleanProperty(Property.PROPERTY_KEY.auto_populate_refer, provider.getProviderNo());

%>
<!DOCTYPE HTML>
<html>
    <head>
        <title></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"
                type="text/javascript"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"
                type="text/javascript"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
              type="text/css"/>
        <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>


        <style>
            table {
                width: 100%;
            }

        </style>
    </head>
    <body class="BodyStyle">
    <div class="container">
        <form action="${pageContext.request.contextPath}/billing/CA/BC/saveBillingPreferencesAction.do" method="POST">
            <input type="hidden" name="providerNo" id="providerNo"/>
            <h2>Billing Preferences</h2>
            <table class="table-condensed" id="scrollNumber1">

                <tr>
                    <td class="MainTableRightColumn">
                        <div class="form-group">
                            <label for="defaultBillingProvider">Select Default Billing Provider:</label>
                            <select name="defaultBillingProvider" class="form-control"
                                         id="defaultBillingProvider">
                                <c:forEach var="billing" items="${billingProviderList}">
                                    <option value="${billing.providerNo}">
                                            ${billing.fullName}
                                    </option>
                                </c:forEach>
                            </select>
                            <div id="default-provider-alert" class="alert alert-warning" style="display:none;">Warning:
                                all remaining billing preferences are now overridden by the preferences of this selected
                                Default Billing Provider. Select "Clinic Default" to use your own Billing Preference
                                Settings
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td class="MainTableRightColumn">
                        <label for="referral"> Select Default Referral Type:</label>
                        <select id="referral" class="form-control" name="referral">
                        <option value="1">Refer To</option>
                        <option value="2">Refer By</option>
                        <option value="3">Neither</option>
                        </select>
                </tr>
                <tr>

                    <%
                        // Check for a global setting that overrides any per-providers setting
                        boolean globalAutoPopulateRefer = false;
                        List<Property> propertyList = propertyDao.findGlobalByName("auto_populate_refer");
                        if (!propertyList.isEmpty()) {
                            for (Property property : propertyList) {
                                if (property.getValue().equalsIgnoreCase("true")) {
                                    globalAutoPopulateRefer = true;
                                    break;
                                }
                            }
                        }
                    %>
                    <td class="MainTableRightColumn">
                        <label for="autoPopulateRefer" class="checkbox-inline">
                            <input type="checkbox" id="autoPopulateRefer" name="autoPopulateRefer" disabled="<%=globalAutoPopulateRefer%>"/>
                            Auto-populate Referring Physician on Billing Form</label>
                        <% if (globalAutoPopulateRefer) { %>
                        <p class="text-warning">Note: The Auto-Populate option above cannot be changed per provider,
                            since it is already <b>enabled</b> for all providers.
                            Change this setting at <b>Administration - Billing - Settings.</b></p>
                        <% } %>
                    </td>
                </tr>
                <tr>

                    <td class="MainTableRightColumn">
                        <label for="defaultBillingForm">Select Default Billing Form:</label>
                        <select name="defaultBillingForm" class="form-control"
                                     id="defaultBillingForm">
                            <c:forEach var="billing" items="${billingFormList}">
                                <option value="${billing.formCode}">
                                        ${billing.description}
                                </option>
                            </c:forEach>
                        </select>
                    </td>
                </tr>
                <tr>

                    <td class="MainTableRightColumn"><label for="defaultServiceLocation">Select Default Service
                        Location:</label>
                        <select name="defaultServiceLocation" class="form-control"
                                     id="defaultServiceLocation">
                            <c:forEach var="serviceLocation" items="${serviceLocationList}">
                                <option value="${serviceLocation.visitType}">
                                        ${serviceLocation.description}
                                </option>
                            </c:forEach>
                        </select></td>
                </tr>
                <tr>
                    <td class="MainTableRightColumn">
                        <label for="payeeProviderNo">Select Default Payee (select "Custom" to set a custom
                            payee):</label>
                        <select name="payeeProviderNo" class="form-control" id="payeeProviderNo"
                                     onchange="defaultPayeeSelect()">
                            <c:forEach var="provider" items="${providerList}">
                                <option value="${provider.providerNo}">
                                        ${provider.fullName}
                                </option>
                            </c:forEach>
                        </select></td>
                </tr>
                <tr>
                    <td class="MainTableRightColumn">
                        <label for="invoicePayeeInfo">Set Your Payee Information:</label>
                        <textarea rows="4" cols="35" class="form-control" id="invoicePayeeInfo"
                                       name="invoicePayeeInfo">&amp;</textarea>
                    </td>
                </tr>
                <tr>
                    <td class="MainTableRightColumn">
                        <label for="invoicePayeeDisplayClinicInfo" class="checkbox-inline">
                            <checkbox id="invoicePayeeDisplayClinicInfo" name="invoicePayeeDisplayClinicInfo"></checkbox>
                            Display Clinic Information Under Payee</label>
                    </td>
                </tr>
                <tr>
                    <td class="MainTableRightColumn">
                        <label>Example Payee Information:</label>
                        <div class="tableHeader rowSpacing">(This is the payee info displayed on your private
                            invoices)
                        </div>
                        <table class="table-condensed" style="border:thin solid grey;">
                            <%
                                Provider payeeProvider = providerDao.getProvider(billingPreference != null ? "" + billingPreference.getDefaultPayeeNo() : null);
                                String payeeInfo;
                                if (billingPreference == null || "NONE".equals(billingPreference.getDefaultPayeeNo())) {
                                    payeeInfo = "";
                                } else {
                                    if ("CUSTOM".equals(billingPreference.getDefaultPayeeNo())) {
                                        List<Property> propList = propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_info, provider.getProviderNo());
                                        payeeInfo = !propList.isEmpty() ? propList.get(0).getValue() : "";
                                    } else {
                                        payeeInfo = (payeeProvider != null ? (payeeProvider.getFirstName() + " " + payeeProvider.getLastName()) : "");
                                    }
                                }

                                ClinicData clinic = new ClinicData();

                                String strPhones = clinic.getClinicDelimPhone();
                                if (strPhones == null) {
                                    strPhones = "";
                                }
                                String strFaxes = clinic.getClinicDelimFax();
                                if (strFaxes == null) {
                                    strFaxes = "";
                                }
                                Vector vecPhones = new Vector();
                                Vector vecFaxes = new Vector();
                                StringTokenizer st = new StringTokenizer(strPhones, "|");
                                while (st.hasMoreTokens()) {
                                    vecPhones.add(st.nextToken());
                                }
                                st = new StringTokenizer(strFaxes, "|");
                                while (st.hasMoreTokens()) {
                                    vecFaxes.add(st.nextToken());
                                }

                            %>

                            <tr class="secHead">
                                <td height="14">Please Make Cheque Payable To:</td>
                            </tr>
                            <% if (!StringUtils.isNullOrEmpty(payeeInfo)) { %>
                            <tr>
                                <td class="title4 payeeInfo"><%=Encode.forHtml(payeeInfo)%>
                                </td>
                            </tr>
                            <% }
                                //Default to true when not found
                                if (propertyDao.findByNameAndProvider(Property.PROPERTY_KEY.invoice_payee_display_clinic, provider.getProviderNo()).isEmpty() || propertyDao.isActiveBooleanProperty(Property.PROPERTY_KEY.invoice_payee_display_clinic, provider.getProviderNo())) {
                            %>

                            <tr>
                                <% SystemPreferences useCustomInvoiceClinicInfo = systemPreferencesDao.findPreferenceByName(SystemPreferences.GENERAL_SETTINGS_KEYS.invoice_use_custom_clinic_info);
                                    if (useCustomInvoiceClinicInfo == null || StringUtils.isNullOrEmpty(useCustomInvoiceClinicInfo.getValue())) { %>
                                <td class="title4">
                                    <%=Encode.forHtml(clinic.getClinicName())%>
                                </td>
                            </tr>
                            <tr>
                                <td class="address"><%=Encode.forHtml(clinic.getClinicAddress() + ", " + clinic.getClinicCity() + ", " + clinic.getClinicProvince() + " " + clinic.getClinicPostal())%>
                                </td>
                            </tr>
                            <tr>
                                <td class="address" id="clinicPhone">
                                    Telephone: <%=vecPhones.size() >= 1 ? vecPhones.elementAt(0) : Encode.forHtml(clinic.getClinicPhone())%>
                                </td>
                            </tr>
                            <tr>
                                <td class="address" id="clinicFax">
                                    Fax: <%=vecFaxes.size() >= 1 ? vecFaxes.elementAt(0) : Encode.forHtml(clinic.getClinicFax())%>
                                </td>
                                <% } else {
                                    SystemPreferences customInvoiceClinicInfo = systemPreferencesDao.findPreferenceByName(SystemPreferences.GENERAL_SETTINGS_KEYS.invoice_custom_clinic_info);
                                %>
                                <td class="payeeInfo"><%= Encode.forHtml(customInvoiceClinicInfo.getValue())%>
                                </td>

                                <% } %>
                            </tr>
                            <% } %>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td class="MainTableBottomRowRightColumn">
                        <input type="submit" name="submit" class="btn btn-primary pull-right" value="Save"/></td>
                </tr>
            </table>
        </form>
    </div>

    <script type="text/javascript">
        document.getElementsByName('autoPopulateRefer')[0].checked = <%= autoPopulateRefer %>;

        function defaultPayeeSelect() {
            let invoicePayeeInfoTA = document.getElementById("invoicePayeeInfo");
            let payeeProviderNoSelect = document.getElementById("payeeProviderNo");
            if (payeeProviderNoSelect.value === 'CUSTOM') {
                invoicePayeeInfoTA.disabled = false;
            } else if (payeeProviderNoSelect.value === 'NONE') {
                invoicePayeeInfoTA.disabled = true;
                invoicePayeeInfoTA.value = '';
            } else {
                invoicePayeeInfoTA.disabled = true;
                Array.prototype.forEach.call(payeeProviderNoSelect.options, function (option) {
                    if (option.value === payeeProviderNoSelect.value)
                        invoicePayeeInfoTA.value = option.text;
                });
            }
        }

        defaultPayeeSelect();

        $(document).ready(function () {

            const defaultValue = "<%= Property.PROPERTY_VALUE.clinicdefault.name() %>";

            $("#defaultBillingProvider").on("change", function () {
                let selected = $("#defaultBillingProvider option:selected").val();
                disableFields(selected);
            })

            function disableFields(selected) {
                // disable other settings whenever a default providers is selected to override.
                if (selected && selected !== defaultValue) {

                    // $("#referral").prop('disabled', true);
                    // $("#autoPopulateRefer").prop('disabled', true);
                    // $("#defaultBillingForm").prop('disabled', true);
                    // $("#defaultServiceLocation").prop('disabled', true);
                    // $("#payeeProviderNo").prop('disabled', true);
                    // $("#invoicePayeeInfo").prop('disabled', true);
                    // $("#invoicePayeeDisplayClinicInfo").prop('disabled', true);

                    $("#default-providers-alert").show();

                } else {

                    // $("#referral").prop('disabled', false);
                    // $("#autoPopulateRefer").prop('disabled', false);
                    // $("#defaultBillingForm").prop('disabled', false);
                    // $("#defaultServiceLocation").prop('disabled', false);
                    // $("#payeeProviderNo").prop('disabled', false);
                    // $("#invoicePayeeInfo").prop('disabled', false);
                    // $("#invoicePayeeDisplayClinicInfo").prop('disabled', false);

                    $("#default-providers-alert").hide();
                }
            }

            disableFields($("#defaultBillingProvider option:selected").val());
        })
    </script>
    </body>
</html>
