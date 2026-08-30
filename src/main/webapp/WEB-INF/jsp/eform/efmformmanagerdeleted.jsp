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
<%@ page import="io.github.carlos_emr.carlos.eform.data.*, io.github.carlos_emr.carlos.eform.*, java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%
    String orderByRequest = request.getParameter("orderby");
    String orderBy = "";
    if (orderByRequest == null) orderBy = EFormUtil.DATE;
    else if (orderByRequest.equals("form_subject")) orderBy = EFormUtil.SUBJECT;
    else if (orderByRequest.equals("form_name")) orderBy = EFormUtil.NAME;
    else if (orderByRequest.equals("file_name")) orderBy = EFormUtil.FILE_NAME;
%>
<!DOCTYPE html>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css">
        <link rel="stylesheet" href="<%= request.getContextPath() %>/css/fontawesome-all.min.css">
<%@ include file="eformBootstrapScript.jspf" %>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="eform.uploadhtml.title"/></title>

        <script>
            // Resolve the CSRF token seeded by /WEB-INF/jspf/csrf-token.jspf.
            // CSRFGuard's injector only visits forms present when it runs, and
            // it works through a MutationObserver -- a microtask -- so a form
            // built and submitted synchronously at click time is gone before
            // the observer fires. Retry once so a single transient bootstrap
            // failure does not leave every Restore on the page broken.
            // Returns the token, or null if it could not be obtained.
            async function csrfToken() {
                try {
                    if (window.csrfTokenReady) {
                        await window.csrfTokenReady;
                    }
                } catch (e) {
                    // Bootstrap fetch failed; fall through to the retry below.
                }
                var csrf = document.querySelector('input[name="CSRF-TOKEN"]');
                if (csrf && csrf.value) {
                    return csrf.value;
                }
                try {
                    await fetchCsrfToken('<%= request.getContextPath() %>');
                } catch (e) {
                    return null;
                }
                csrf = document.querySelector('input[name="CSRF-TOKEN"]');
                return (csrf && csrf.value) ? csrf.value : null;
            }

            async function restoreEForm(fid) {
                // This form is built after page load, so CSRFGuard never
                // injects the token and it has to be copied in by hand.
                // Without it CarlosCsrfGuardFilter answers 403 and Restore
                // silently does nothing -- the same defect the delete control
                // on efmformmanager.jsp was fixed for. Say so plainly rather
                // than submitting a request that cannot succeed.
                var token = await csrfToken();
                if (!token) {
                    alert("<fmt:message key="eform.calldeletedformdata.restoreTokenUnavailable"/>");
                    return;
                }
                var form = document.createElement('form');
                form.method = 'post';
                form.action = '<%= request.getContextPath() %>/eform/restoreEForm';
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'fid';
                input.value = fid;
                form.appendChild(input);
                var tokenInput = document.createElement('input');
                tokenInput.type = 'hidden';
                tokenInput.name = 'CSRF-TOKEN';
                tokenInput.value = token;
                form.appendChild(tokenInput);
                document.body.appendChild(form);
                form.submit();
            }
        </script>
    </head>
    <script language="javascript">
        function checkFormAndDisable() {
            if (document.forms[0].formHtml.value == "") {
                alert("<fmt:message key="eform.uploadhtml.msgFileMissing"/>");
            } else {
                document.forms[0].subm.value = "<fmt:message key="eform.uploadimages.processing"/>";
                document.forms[0].subm.disabled = true;
                document.forms[0].submit();
            }
        }

        function newWindow(url, id) {
            Popup = window.open(url, id, 'toolbar=no,location=no,status=yes,menubar=no, scrollbars=yes,resizable=yes,width=700,height=600,left=200,top=0');
        }
    </script>
    <body>


    <%@ include file="efmTopNav.jspf" %>

    <%-- Seeds the CSRF-TOKEN input that restoreEForm() copies into the POST it
         builds at click time. This page has no static POST form, so without
         the include there is no token on the page at all and every Restore is
         rejected with 403. --%>
    <%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>

    <h3><fmt:message key="eform.calldeletedformdata.title"/></h3>


    <%-- thead/tbody are required, not cosmetic: DataTables counts columns from
         table > thead > tr > th. Without a thead it registered zero columns
         against these six-cell rows and aborted init with
         "DataTables warning: table id=tblDeletedEforms - Incorrect column
         count" (tn/18), reported by a tester on 2026.08.0-alpha9. Every other
         DataTables-backed eForm list already has the wrappers. --%>
    <table class="table table-sm table-striped table-hover" id="tblDeletedEforms">
        <thead>
        <tr>
            <th><a href="<%= request.getContextPath() %>/eform/efmformmanagerdeleted?orderby=form_name"
                   class="contentLink"><fmt:message key="eform.uploadhtml.btnFormName"/></a></th>
            <th><a href="<%= request.getContextPath() %>/eform/efmformmanagerdeleted?orderby=form_subject"
                   class="contentLink"><fmt:message key="eform.uploadhtml.btnSubject"/></a></th>
            <th><a href="<%= request.getContextPath() %>/eform/efmformmanagerdeleted?orderby=file_name"
                   class="contentLink"><fmt:message key="eform.uploadhtml.btnFile"/></a></th>
            <th><a href="<%= request.getContextPath() %>/eform/efmformmanagerdeleted?"
                   class="contentLink"><fmt:message key="eform.uploadhtml.btnDate"/></a></th>
            <th><fmt:message key="eform.uploadhtml.btnTime"/></th>
            <th><fmt:message key="eform.uploadhtml.msgAction"/></th>
        </tr>
        </thead>
        <tbody>
        <%
            ArrayList<HashMap<String, ? extends Object>> eForms = EFormUtil.listEForms(orderBy, EFormUtil.DELETED);
            for (int i = 0; i < eForms.size(); i++) {
                HashMap<String, ? extends Object> curForm = eForms.get(i);
        %>
        <tr>
            <td><a href="#" class="viewEform"
                   onclick="newWindow('<%= request.getContextPath() %>/eform/efmshowform_data?fid=<%=SafeEncode.forJavaScript((String) curForm.get("fid"))%>', '<%="FormD"+i%>'); return false;"><%=SafeEncode.forHtmlContent((String) curForm.get("formName"))%>
            </a></td>
            <td><%=SafeEncode.forHtmlContent((String) curForm.get("formSubject"))%>&nbsp;</td>
            <td><%=SafeEncode.forHtmlContent((String) curForm.get("formFileName"))%>
            </td>
            <td><%=SafeEncode.forHtmlContent((String) curForm.get("formDate"))%>
            </td>
            <td><%=SafeEncode.forHtmlContent((String) curForm.get("formTime"))%>
            </td>
            <td><a href='javascript:void(0);' onclick="restoreEForm('<%=SafeEncode.forJavaScript((String) curForm.get("fid"))%>');"
                   class="contentLink">
                <fmt:message key="eform.calldeletedformdata.btnRestore"/>
            </a>
            </td>
        </tr>
        <% } %>
        </tbody>
    </table>

    <%@ include file="efmFooter.jspf" %>

    <%-- No drawCallback here: it used to call registerHref(), which is defined
         nowhere in the codebase and never has been. That was harmless only
         because this table had no thead, so DataTables aborted init before it
         ever drew and the callback was unreachable. Giving the table its
         thead/tbody made init succeed -- and would have made every draw throw
         "ReferenceError: registerHref is not defined" out of _fnCallbackFire,
         which has no try/catch, aborting the rest of _fnDraw and _fnInitialise
         and taking the footer's dropdown re-init down with it. The viewEform
         anchors need no binding: they carry their own onclick ... return false. --%>
    <script>
        // Guarded because this page loads no jQuery of its own: it works only
        // because the Administration shell that injects it provides one. Opened
        // standalone the bare call threw "ReferenceError: $ is not defined".
        // Sorting is an enhancement, so degrade to the plain table rather than
        // throwing -- the rows, the links and Restore all work without it.
        if (window.jQuery && jQuery.fn && jQuery.fn.DataTable) {
            $('#tblDeletedEforms').DataTable({
                "order": [[0, "asc"]]
            });
        }
    </script>
    </body>
</html>
