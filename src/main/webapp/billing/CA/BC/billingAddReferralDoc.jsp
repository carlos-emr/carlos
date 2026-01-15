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

--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.billing,_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page
        import="java.util.*,ca.openosp.openo.billing.ca.bc.data.*,ca.openosp.openo.billing.ca.bc.pageUtil.*,org.apache.commons.beanutils.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Billingreferral" %>
<%@page import="ca.openosp.openo.commn.dao.BillingreferralDao" %>
<%
    BillingreferralDao billingReferralDao = (BillingreferralDao) SpringUtils.getBean(BillingreferralDao.class);
%>

<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<html>

    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageReferralDoc"/></title>


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

            function checkBillingNumber() {
                if (document.AddReferralDocForm.referral_no.value.length == 0) {
                    alert("You must enter a Billing Number");
                    return false;
                } else if (document.AddReferralDocForm.referral_no.value.length != 5) {
                    if (document.AddReferralDocForm.referral_no.value.length < 5) {
                        //need to addzeros
                        document.AddReferralDocForm.referral_no.value = forwardZero(document.AddReferralDocForm.referral_no.value, 5);
                    } else {
                        alert("Billing Number must be digits");
                        return false;
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
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManageReferralDoc"/></h3>

    <div class="container-fluid well">
        <% if (request.getAttribute("Error") != null) { %>
        <div class="alert alert-error">
            <button type="button" class="close" data-dismiss="alert">&times;</button>
            <%=request.getAttribute("Error") %>
        </div>
        <% }%>

        <form action="${pageContext.request.contextPath}/billing/CA/BC/AddReferralDoc.do" method="post"
              onsubmit="return checkBillingNumber();" style="addReferralDocform">
            <%
                String id = request.getParameter("id");
            %>
            <fieldset>
                <legend><%=(id == null) ? "Add" : "Update"%> Referral Doctor</legend>
                Billing #:<input type="text" name="referral_no" id="referral_no" /><br>
                Last Name:<input type="text" name="last_name" id="last_name" /> First Name:<input type="text" name="first_name" id="first_name" /><br/>
                Specialty:<input type="text" name="specialty" id="specialty" /></br>
                Address 1:<input type="checkbox" name="address1" size="30" /><br/>
                Address 2:<input type="checkbox" name="address2" size="30" /><br/>
                City:<input type="text" name="city" id="city" />
                Province:<input type="text" name="province" id="province" /><br/>
                Postal:<input type="text" name="postal" id="postal" /><br/>
                Phone:<input type="text" name="phone" id="phone" />
                Fax:<input type="text" name="fax" id="fax" /><br/>
                <input class="btn btn-primary" type="submit" value="Save"/>
            </fieldset>
        </form>
    </div>
    <script>
        registerFormSubmit('addReferralDocform', 'dynamic-content');
    </script>
    </body>
</html>
