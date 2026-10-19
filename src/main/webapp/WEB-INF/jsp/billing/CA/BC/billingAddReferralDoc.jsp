<!DOCTYPE html>
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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing,_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page
        import="java.util.*,io.github.carlos_emr.carlos.billing.ca.bc.data.*,io.github.carlos_emr.carlos.billing.ca.bc.pageUtil.*,org.apache.commons.beanutils.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Billingreferral" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.BillingreferralDao" %>
<%
    BillingreferralDao billingReferralDao = (BillingreferralDao) SpringUtils.getBean(BillingreferralDao.class);
%>

<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.admin.ManageReferralDoc"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

        <script type="text/javascript">

            function isNumeric(strString) {
                var validNums = "0123456789.";
                var strChar;
                var retval = true;

                for (i = 0; i < strString.length && retval == true; i++) {
                    strChar = strString.charAt(i);
                    if (validNums.indexOf(strChar) == -1) {
                        retval = false;
                    }
                }
                return retval;
            }

            function checkUnits() {
                if (!isNumeric(document.BillingAddCodeForm.value.value)) {
                    alert("Price has to be a numeric value");
                    document.BillingAddCodeForm.value.focus();
                    return false;
                }
                return true;
            }

            // Inside the Administration panel, registerFormSubmit posts this form by AJAX from its own submit
            // handler, which runs after this one; stopping the event here keeps a refused number from being posted.
            function refuseSubmit(event) {
                if (event) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
                return false;
            }

            function checkBillingNumber(event) {
                if (document.AddReferralDocForm.referral_no.value.length == 0) {
                    alert("You must enter a Billing Number");
                    return refuseSubmit(event);
                } else if (document.AddReferralDocForm.referral_no.value.length != 5) {
                    if (document.AddReferralDocForm.referral_no.value.length < 5) {
                        //need to addzeros
                        document.AddReferralDocForm.referral_no.value = forwardZero(document.AddReferralDocForm.referral_no.value, 5);
                    } else {
                        alert("Billing Number must be digits");
                        return refuseSubmit(event);
                    }

                }

                return true;
            }

            function forwardZero(str, len) {
                returnZeroValue = "";
                for (var i = str.length; i < len; i++) {
                    returnZeroValue += "0";
                }
                //return cutFrontString(returnZeroValue+y,x);
                return (returnZeroValue + str);
            }

        </script>
    </head>

    <body>
    <h3><fmt:message key="admin.admin.ManageReferralDoc"/></h3>

    <div class="container-fluid card card-body bg-body-tertiary">
        <% if (request.getAttribute("Error") != null) { %>
        <div class="alert alert-danger">
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            <%=request.getAttribute("Error") %>
        </div>
        <% }%>

        <%
            // The list's billing-number link opens this page with the doctor's id (ViewBillingAddReferralDoc
            // admits only a plain integer). The form shows that doctor and posts the id back, so AddReferralDoc
            // updates the row instead of adding another.
            String id = request.getParameter("id");
            Billingreferral doctor = (id != null && id.matches("\\d{1,9}")) ? billingReferralDao.getById(Integer.parseInt(id)) : null;
        %>
        <form name="AddReferralDocForm" id="addReferralDocform" action="${pageContext.request.contextPath}/billing/CA/BC/AddReferralDoc" method="post"
              onsubmit="return checkBillingNumber(event);">
            <% if (doctor != null) { %>
            <input type="hidden" name="id" value="<carlos:encode value='<%= String.valueOf(doctor.getBillingreferralNo()) %>' context="htmlAttribute"/>"/>
            <% } %>
            <fieldset>
                <legend><%=(doctor == null) ? "Add" : "Update"%> Referral Doctor</legend>
                <label for="referral_no">Billing #:</label><input type="text" name="referral_no" id="referral_no" value="<carlos:encode value='<%= doctor == null ? null : doctor.getReferralNo() %>' context="htmlAttribute"/>" /><br>
                <label for="last_name">Last Name:</label><input type="text" name="last_name" id="last_name" value="<carlos:encode value='<%= doctor == null ? null : doctor.getLastName() %>' context="htmlAttribute"/>" /> <label for="first_name">First Name:</label><input type="text" name="first_name" id="first_name" value="<carlos:encode value='<%= doctor == null ? null : doctor.getFirstName() %>' context="htmlAttribute"/>" /><br/>
                <label for="specialty">Specialty:</label><input type="text" name="specialty" id="specialty" value="<carlos:encode value='<%= doctor == null ? null : doctor.getSpecialty() %>' context="htmlAttribute"/>" /></br>
                <label for="address1">Address 1:</label><input type="text" name="address1" id="address1" size="30" value="<carlos:encode value='<%= doctor == null ? null : doctor.getAddress1() %>' context="htmlAttribute"/>" /><br/>
                <label for="address2">Address 2:</label><input type="text" name="address2" id="address2" size="30" value="<carlos:encode value='<%= doctor == null ? null : doctor.getAddress2() %>' context="htmlAttribute"/>" /><br/>
                <label for="city">City:</label><input type="text" name="city" id="city" value="<carlos:encode value='<%= doctor == null ? null : doctor.getCity() %>' context="htmlAttribute"/>" />
                <label for="province">Province:</label><input type="text" name="province" id="province" value="<carlos:encode value='<%= doctor == null ? null : doctor.getProvince() %>' context="htmlAttribute"/>" /><br/>
                <label for="postal">Postal:</label><input type="text" name="postal" id="postal" value="<carlos:encode value='<%= doctor == null ? null : doctor.getPostal() %>' context="htmlAttribute"/>" /><br/>
                <label for="phone">Phone:</label><input type="text" name="phone" id="phone" value="<carlos:encode value='<%= doctor == null ? null : doctor.getPhone() %>' context="htmlAttribute"/>" />
                <label for="fax">Fax:</label><input type="text" name="fax" id="fax" value="<carlos:encode value='<%= doctor == null ? null : doctor.getFax() %>' context="htmlAttribute"/>" /><br/>
                <input class="btn btn-primary" type="submit" value="Save"/>
            </fieldset>
        </form>
    </div>
    <script>
        // The Administration panel this page opens in defines registerFormSubmit; opened on its own, the form posts plainly.
        if (typeof registerFormSubmit === 'function') {
            registerFormSubmit('addReferralDocform', 'dynamic-content');
        }
    </script>
    </body>
</html>
