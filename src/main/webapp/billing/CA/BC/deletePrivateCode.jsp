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
        import="java.util.*,io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData,io.github.carlos_emr.carlos.billing.ca.bc.pageUtil.*" %>
<%
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
    String serviceCode = request.getParameter("code") == null ? "-1" : request.getParameter("code");
    BillingCodeData data = new BillingCodeData();
    data.deleteBillingCode(serviceCode);
    response.sendRedirect("billingPrivateCodeAdjust.jsp");
%>
