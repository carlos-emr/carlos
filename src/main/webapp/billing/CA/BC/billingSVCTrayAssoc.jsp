<%@page import="java.sql.*" errorPage="/errorpage.jsp" %>
<%@taglib uri="http://displaytag.sf.net" prefix="display" %>
<%@taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <script>
            //Global variables


            function popup(height, width, url, windowName) {
                var page = url;
                windowprops = "height=" + height + ",width=" + width + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(url, windowName, windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
                popup.focus();
                return false;
            }

            function popFeeItemList(form, field) {
                var width = 575;
                var height = 400;
                var str = document.forms[form].elements[field].value;
                var url = '<rewrite:reWrite jspPage="support/billingfeeitem.jsp"/>' + '?form=' + form + '&field=' + field + '&searchStr=' + str;
                var windowName = field;
                popup(height, width, url, windowName);
            }

            function setMode(mode) {
                document.forms[0].actionMode.value = mode;
            }

            function editAssociation(primary, secondary) {
                var frm = document.forms[0];
                frm.primaryCode.value = primary;
                frm.secondaryCode.value = secondary;
            }

            function deleteAssociation(id) {
                var frm = document.forms[0];
                if (confirm("Do you really want to delete this entry?")) {
                    frm.actionMode.value = "delete";
                    frm.id.value = id;
                    frm.submit();
                }
            }
        </script>
        <title>Manage Procedure and Tray Fee Associations</title>
    </head>
    <body>
    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <form action="${pageContext.request.contextPath}/billing/CA/BC/supServiceCodeAssocAction.do" method="post">
        <input type="hidden" name="actionMode" id="actionMode"/>
        <input type="hidden" name="id" id="id"/>
        <fieldset>
            <legend> Edit Procedure/Tray Fee
                Associations
            </legend>
            <p><label for="primaryCode"> Procedure Fee Code: </label>
                <input type="text" name="primaryCode" id="primaryCode"/> <a href="#"
                                                                      onClick="popFeeItemList('supServiceCodeAssocActionForm','primaryCode'); return false;">Search</a>
            </p>
            <p><label for="secondaryCode"> Tray Fee Code: </label>
                <input type="text" name="secondaryCode" id="secondaryCode"/> <a href="#"
                                                                          onClick="popFeeItemList('supServiceCodeAssocActionForm','secondaryCode'); return false;">Search</a>
            </p>
            <input type="submit" name="submitButton" value="Save Association"
                   onclick="setMode('edit');"/> <input type="reset" value="Clear"/></fieldset>
    </form>
    <p/><display:table class="displayGrid" name="list" pagesize="50"
                       defaultsort="1" defaultorder="descending"
                       decorator="ca.openosp.openo.billings.ca.bc.pageUtil.BillCodesTableWrapper">
        <display:column property="billingServiceNo" title="Procedure Fee Code"/>
        <display:column property="billingServiceTrayNo" title="Tray Fee Code"/>
        <display:column property="associationStatus" title="Options"/>
    </display:table>
    </body>
</html>
