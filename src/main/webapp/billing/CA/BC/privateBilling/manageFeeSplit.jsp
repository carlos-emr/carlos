<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.PropertyDao" %>
<%@ page import="ca.openosp.openo.commn.model.Property" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ taglib prefix="security" uri="/oscarSecuritytag" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.billing" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }

    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    String providerNo = request.getParameter("providerNo");
    String feeSplit = request.getParameter("feeSplit");
    boolean failedToSave = false;

    try {
        if (providerNo != null && feeSplit != null) {
            int feeSplitInt = Integer.parseInt(feeSplit);
            if (feeSplitInt > 100 || feeSplitInt < 0) {
                failedToSave = true;
                MiscUtils.getLogger().error("Invalid value for property 'fee_split' was passed for providers '" + providerNo + "' with value '" + feeSplit + "'");
            } else {
                List<Property> feeSplitProps = propertyDao.findByNameAndProvider("fee_split", providerNo);
                if (!feeSplitProps.isEmpty()) {
                    feeSplitProps.get(0).setValue(feeSplit);
                    propertyDao.merge(feeSplitProps.get(0));
                } else {
                    Property feeSplitProp = new Property();
                    feeSplitProp.setValue(feeSplit);
                    feeSplitProp.setProviderNo(providerNo);
                    feeSplitProp.setName("fee_split");
                    propertyDao.persist(feeSplitProp);
                }
            }
        }
    } catch (NumberFormatException e) {
        failedToSave = true;
        MiscUtils.getLogger().error("Invalid value for property 'fee_split' was passed for providers '" + providerNo + "' with value '" + feeSplit + "'", e);
    } catch (Exception e) {
        failedToSave = true;
        MiscUtils.getLogger().error("Exception thrown trying to save property 'fee_split' to providers '" + providerNo + "' with value '" + feeSplit + "'", e);
    }

    List<Property> feeSplitProps = propertyDao.findByName("fee_split");

%>

<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageFeeSplit"/></title>
    <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>
    <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
<h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageFeeSplit"/></h3>
<form action="manageFeeSplit.jsp">
    <label for="providerNo">Provider:</label>
    <select name="providerNo" id="providerNo" onchange="setSplitProvider()">
        <% for (Provider provider : providerDao.getBillableProvidersInBC()) { %>
        <option value="<%=Encode.forHtmlAttribute(provider.getProviderNo())%>" <%=provider.getProviderNo().equals(providerNo) ? "Selected" : ""%>>
            <%=Encode.forHtmlContent(provider.getFormattedName())%>
        </option>
        <% } %>
    </select>
    <label for="feeSplit">Fee Split:</label>
    <div class="input-append">
        <input type="range" name="feeSplit" id="feeSplit" min="0" max="100" oninput="setSplit()"/>
    </div>
    <br>
    CLINIC / PROVIDER
    <div class="input-append">
        <input type="text" class="span2" style="height:auto" onblur="setSplitText()" id="feeSplitText"/>
        <span class="add-on">%</span>
    </div>
    <br>
    <input class="btn btn-primary" type="submit" value="Save"/>
</form>
</body>
<footer>
    <script type="text/javascript">


        var providerSplits = {
            <% for(Property feeSplitProp : feeSplitProps) { %>
            '<%=Encode.forJavaScript(feeSplitProp.getProviderNo())%>': '<%=Encode.forJavaScript(feeSplitProp.getValue())%>',
            <% } %>
        };

        setSplitProvider();

        function setSplitProvider() {
            var providerNo = document.getElementById('providerNo').value;
            var feeSplit = parseInt(providerSplits[providerNo]);
            if (isNaN(feeSplit)) {
                feeSplit = 100;
            }
            var feeSplitRange = document.getElementById('feeSplit');
            feeSplitRange.value = feeSplit;
            setSplit();
        }

        function setSplit() {
            var feeSplitRange = document.getElementById('feeSplit');
            var feeSplitText = document.getElementById('feeSplitText');
            feeSplitText.value = (100 - feeSplitRange.value) + '/' + feeSplitRange.value;
        }

        function setSplitText() {
            var feeSplit = parseInt(document.getElementById('feeSplitText').value);
            var feeSplitRange = document.getElementById('feeSplit');
            feeSplitRange.value = 100 - feeSplit;
            setSplit();
        }

        //prevents enter key from submitting form
        document.getElementById("feeSplitText").onkeypress = function (e) {
            if (e.keyCode === 13 || e.key === 'Enter') {
                setSplitText();
                return false;
            }
        };

        <% if(failedToSave) { %>
        alert("Something went wrong saving the fee split");
        <% } %>

    </script>
</footer>
</html>
