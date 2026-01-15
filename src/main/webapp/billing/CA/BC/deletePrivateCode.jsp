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

<%@page
        import="java.util.*,ca.openosp.openo.billings.ca.bc.data.BillingCodeData,ca.openosp.openo.billing.ca.bc.pageUtil.*" %>
<%
    String serviceCode = request.getParameter("code") == null ? "-1" : request.getParameter("code");
    BillingCodeData data = new BillingCodeData();
    data.deleteBillingCode(serviceCode);
    response.sendRedirect("billingPrivateCodeAdjust.jsp");
%>
