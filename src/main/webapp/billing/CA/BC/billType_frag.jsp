<%@page import="java.sql.*" errorPage="/errorpage.jsp" %>
<%@page
        import="java.math.*, java.util.*, java.sql.*, ca.openosp.*, java.net.*,ca.openosp.openo.billing.ca.bc.MSP.*,ca.openosp.openo.billing.ca.bc.data.*" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingFormData" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<!--
-
This jsp fragment displays the Bill Type drop down which is used by the MSP and WCB "corrections" screens.
TODO: Localize Strings
-->
<%
    BillingFormData billForm = new BillingFormData();
    Properties statusTypeProps = billForm.getStatusProperties(billForm.getStatusTypes(null));
    String[] codes = (String[]) request.getAttribute("codes");
    String BillType = request.getParameter("BillType");
    List statusTypes = billForm.getStatusTypes(codes);
    request.setAttribute("statusTypes", statusTypes);
%>
<style>
    label {
        font-weight: bold;
    }
</style>
<script type="text/javascript">
    function callToggleWCB() {
        if (toggleWCB) {
            toggleWCB();
        }
    }
</script>
<table>
    <tr>
        <td nowrap="nowrap"><label for="billtype">Billing Type: </label>
        </td>
        <td>
            <div id="billtype"><%=statusTypeProps.getProperty(BillType)%>
            </div>
        </td>
    </tr>
    <tr>
        <td><label for="status">Change Type:</label></td>
        <td><select id="status" name="status"
                         onchange="javascript:document.forms[0].xml_status.value = this.value;callToggleWCB();">
            <c:forEach var="statusType" items="${statusTypes}">
                <option value="${statusType.billingstatus}">
                        ${statusType.displayNameExt}
                </option>
            </c:forEach>
        </select></td>
    </tr>
</table>
<input type="hidden" name="xml_status" value="<%=BillType%>">
<br/>
