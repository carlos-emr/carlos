<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ReceivePayment2Action" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="oscar.billing.CA.BC.title"/></title>
    <script type="javascript">
        function refreshParent() {
            opener.window.location.href = opener.window.location.href;
        }
    </script>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
</head>
<body>
<c:if test="${receivePaymentActionForm.paymentReceived}">
    <fieldset>
        <legend><fmt:message key="oscar.billing.CA.BC.received"/></legend>
        <div class="msgDisplay">
            <%
                ReceivePayment2Action frm = (ReceivePayment2Action) request.getAttribute("receivePaymentActionForm");
            %> <%=java.text.NumberFormat.getCurrencyInstance().format(new Double(frm.getAmountReceived()))%>
            <fmt:message key="oscar.billing.CA.BC.credit"/> &nbsp; <fmt:message key="oscar.billing.CA.BC.invoice"/> 
            ${carlos:forHtml(receivePaymentActionForm.billNo)} &nbsp; <fmt:message key="oscar.billing.CA.BC.lineNo"/> 
            ${carlos:forHtml(receivePaymentActionForm.billingmasterNo)}</div>
        <div align="center">
            <button
                    onclick="opener.window.location.reload();self.close();return false;">Close
            </button>
        </div>
    </fieldset>
</c:if>
<c:if test="${not receivePaymentActionForm.paymentReceived}">
    <form action="${pageContext.request.contextPath}/billing/CA/BC/receivePaymentAction" method="post">
        <input type="hidden" name="billingmasterNo" id="billingmasterNo"/>
        <input type="hidden" name="billNo" id="billNo"/>

        <fieldset>
            <legend><fmt:message key="oscar.billing.CA.BC.title"/></legend>
            <div class="msgDisplay">
                <p><fmt:message key="oscar.billing.CA.BC.invoice"/> 
                    ${carlos:forHtml(receivePaymentActionForm.billNo)}</p>
                <p><fmt:message key="oscar.billing.CA.BC.lineNo"/> 
                    ${carlos:forHtml(receivePaymentActionForm.billingmasterNo)}</p>
            </div>
            <p><label> <fmt:message key="oscar.billing.CA.BC.amount"/>
                <input type="text" maxlength="6" name="amountReceived" /><!--&nbsp;<input type="checkbox" name="isRefund" value="true"/>-->
            </label></p>
            <p>
                <label> <fmt:message key="oscar.billing.CA.BC.method"/>
                    <select name="paymentMethod" id="paymentMethod">
                        <c:forEach var="method" items="${receivePaymentActionForm.paymentMethodList}">
                            <option value="${method.id}">${method.paymentType}</option>
                        </c:forEach>
                    </select>
                </label>
            </p>
            <p><input type="submit" value="Submit"/></p>
        </fieldset>
    </form>
</c:if>
</body>
</html>
