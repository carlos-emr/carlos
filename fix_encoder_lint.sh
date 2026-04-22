cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp
@@ -281,7 +281,7 @@
         Element root = doc.getDocumentElement();
         if (root != null) {
             String safeRootLabel = root.getAttribute("label") == null ? "" : root.getAttribute("label");
-            out.print(spanStartRoot + Encode.forHtml(safeRootLabel) + spanEnd);
+            out.print(spanStartRoot + SafeEncode.forHtml(safeRootLabel) + spanEnd);
             NodeList records = root.getChildNodes();
             for (int i = 0; i < records.getLength(); i++) {
                 Node n = records.item(i);
@@ -296,7 +296,7 @@
             Element tbl = (Element) n;
             String spanStart = "<span style=\"color:#aa0000;font-size:12px;font-weight:bold;margin-left:20px;\">";
             String spanEnd = "</span><br>\n";
-            out.print(spanStart + Encode.forHtml(tbl.getAttribute("name")) + spanEnd);
+            out.print(spanStart + SafeEncode.forHtml(tbl.getAttribute("name")) + spanEnd);
             NodeList records = tbl.getChildNodes();
             for (int i = 0; i < records.getLength(); i++) {
                 Node node = records.item(i);
@@ -315,8 +315,8 @@
             Element item = (Element) n;
             String spanStart = "<span style=\"color:#0000aa;font-size:12px;margin-left:30px;\">";
             String spanEnd = "</span><br>\n";
-            out.print(Encode.forHtml(item.getAttribute("name")) + ": "
-                    + Encode.forHtml(item.getAttribute("value")) + spanEnd);
+            out.print(SafeEncode.forHtml(item.getAttribute("name")) + ": "
+                    + SafeEncode.forHtml(item.getAttribute("value")) + spanEnd);
         }

         public void doPrintForm(Node n) {
@@ -338,9 +338,9 @@
                         String spanStart = "<span style=\"color:#0000aa;font-size:12px;margin-left:30px;\">";
                         String spanEnd = "</span><br>\n";
                         out.print(spanStart);
-                        out.print(Encode.forHtml(fld.getAttribute("name")) + ": ");
+                        out.print(SafeEncode.forHtml(fld.getAttribute("name")) + ": ");
                         if (!fld.getAttribute("value").trim().equals("")) {
-                            out.print(Encode.forHtml(fld.getAttribute("value")));
+                            out.print(SafeEncode.forHtml(fld.getAttribute("value")));
                         } else {
                             out.print("_________________");
                         }
@@ -394,15 +394,15 @@
         <div class="NoPrint">
             <form name="print">
                 <input type="button" value="<%= SafeEncode.forHtmlAttribute(MsgCommxml.getLocalizedString("messenger.ViewAttachment.btnClose")) %>"
-                    onclick="if (confirm('<%= Encode.forJavaScript((String) pageContext.getAttribute("closeConfirmMsg")) %>')) { top.window.close(); }">
+                    onclick="if (confirm('<%= SafeEncode.forJavaScript((String) pageContext.getAttribute("closeConfirmMsg")) %>')) { top.window.close(); }">
                 <input type="button" value="<%= SafeEncode.forHtmlAttribute(MsgCommxml.getLocalizedString("messenger.ViewAttachment.btnImport")) %>"
                     onclick="document.importform.submit()"> <input type="button" value="<%= SafeEncode.forHtmlAttribute(MsgCommxml.getLocalizedString("messenger.ViewAttachment.btnPrint")) %>"
                     onclick="window.print()">
             </form>
             <form name="importform"
                 action="<%=request.getContextPath()%>/messenger/ImportAttachment.do" method="post">
-                <input type="hidden" name="xmldata"
-                    value="<%= Encode.forHtmlAttribute(MsgCommxml.encode64(MsgCommxml.toXML(root))) %>"/> <input
-                    type="hidden" name="id" value="<%= Encode.forHtmlAttribute(String.valueOf(request.getAttribute("attId"))) %>"/>
+                <input type="hidden" name="xmldata"
+                    value="<%= SafeEncode.forHtmlAttribute(MsgCommxml.encode64(MsgCommxml.toXML(root))) %>"/> <input
+                    type="hidden" name="id" value="<%= SafeEncode.forHtmlAttribute(String.valueOf(request.getAttribute("attId"))) %>"/>
             </form>
         </div>
INNER_EOF

cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp
@@ -174,7 +174,7 @@
                     if (attVector != null) {
                         for (int i = 0; i < attVector.size(); i++) {
                     %>
-                        <td><%= Encode.forHtml((String) attVector.get(i)) %>
+                        <td><%= SafeEncode.forHtml((String) attVector.get(i)) %>
                         </td>
                     <%
                         }
@@ -189,7 +189,7 @@
         <div class="col-12 p-3 text-center">
             <form method="post" action="ViewPDFAttachment.jsp" name="attachForm" id="attachForm">
                 <input type="hidden" name="msgId" id="msgId" value="<%= SafeEncode.forHtmlAttribute(msgId) %>"/>
-                <input type="hidden" name="attachment" id="attachment" value="<%= Encode.forHtmlAttribute(pdfAttch) %>"/>
+                <input type="hidden" name="attachment" id="attachment" value="<%= SafeEncode.forHtmlAttribute(pdfAttch) %>"/>
                 <input type="hidden" name="index" id="index" value="<%= SafeEncode.forHtmlAttribute(Integer.toString(index + 1)) %>"/>
                 <button type="submit" class="btn btn-primary shadow-sm" name="button" value="next">
                     <i class="fa-solid fa-arrow-right me-2"></i>
INNER_EOF

cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
@@ -57,7 +57,7 @@
         var url = '<%= SafeEncode.forJavaScript(url) %>';
     </script>
 </head>
-<html lang="${e:forHtmlAttribute(pageContext.request.locale.language)}">
+<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
 <frameset id="attachFrameset" cols="350px,*" border="0">
     <frame id="frmInbox" name="frmInbox" src=""
            noresize="noresize" scrolling="no" style="overflow: hidden;"/>
@@ -72,7 +72,7 @@
         <frame id="frmPreview" name="frmPreview" src="<%= SafeEncode.forHtmlAttribute(pdfPath) %>" noresize="noresize"/>
     <% } else { %>
         <frame id="frmPreview" name="frmPreview"
-               src="<%= request.getContextPath() %>/messenger/PreviewPDF?demographic_no=<%= Encode.forUriComponent(demographic_no) %>"
+               src="<%= request.getContextPath() %>/messenger/PreviewPDF?demographic_no=<%= SafeEncode.forUriComponent(demographic_no) %>"
                noresize="noresize"/>
     <% } %>
 </frameset>
INNER_EOF

cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
@@ -198,7 +198,7 @@
     %>
     <!DOCTYPE html>
-<html lang="${e:forHtmlAttribute(pageContext.request.locale.language)}">
+<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
 <head>
     <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
     <link rel="stylesheet" type="text/css"
@@ -207,7 +207,7 @@
     <script type="text/javascript">
         const PDFPreview = {
-            exitConfirm: '${e:forJavaScript(exitConfirmMsg)}'
+            exitConfirm: '${carlos:forJavaScript(exitConfirmMsg)}'
         };

@@ -349,7 +349,7 @@
                 </h4>
             </div>
             <div class="card-body">
-                ${e:forHtml(demoName)}
+                ${carlos:forHtml(demoName)}
                 <hr>
                 <button class="btn btn-outline-primary btn-sm mb-3" type="button"
                         data-bs-toggle="collapse"
@@ -385,18 +385,18 @@
                         <input type="hidden" name="type" value="demographic">
                         <input type="hidden" name="id" value="none">
                         <input type="hidden" name="uri" class="attachment-uri"
-                               value="<%=Encode.forHtmlAttribute(demoUri)%>"
+                               value="<%=SafeEncode.forHtmlAttribute(demoUri)%>"
                         >
                         <input type="hidden" name="title" class="attachment-title"
-                               value="${e:forHtmlAttribute(demoTitleValue)}"
+                               value="${carlos:forHtmlAttribute(demoTitleValue)}"
                         >
                         <div class="row align-items-center">
                             <div class="col">
-                        ${e:forHtml(demoName)}
+                        ${carlos:forHtml(demoName)}
                             </div>
                             <div class="col-auto">
                                 <button type="button" class="btn btn-sm btn-outline-secondary btn-preview-doc"
-                                data-preview-uri="<%=Encode.forHtmlAttribute(demoUri)%>"
+                                data-preview-uri="<%=SafeEncode.forHtmlAttribute(demoUri)%>"
                                 title="<spring:message code="global.btn.preview"/>">
                                     <i class="fa-solid fa-eye"></i>
                                 </button>
@@ -421,22 +421,22 @@
                         <input type="hidden" name="type" value="econsult">
                         <input type="hidden" name="id" value="<%=SafeEncode.forHtmlAttribute(String.valueOf(ec.getId()))%>">
                         <input type="hidden" name="uri" class="attachment-uri"
-                               value="<%=Encode.forHtmlAttribute(ecUri)%>"
+                               value="<%=SafeEncode.forHtmlAttribute(ecUri)%>"
                         >
                         <input type="hidden" name="title" class="attachment-title"
-                               value="${e:forHtmlAttribute(ecTitleValue)}"
+                               value="${carlos:forHtmlAttribute(ecTitleValue)}"
                         >
                         <div class="row align-items-center">
                             <div class="col">
-                    <td class="align-middle">${e:forHtml(ecTimestamp)}</td>
+                    <td class="align-middle">${carlos:forHtml(ecTimestamp)}</td>
                             </div>
                             <div class="col-auto">
                                 <button type="button" class="btn btn-sm btn-outline-secondary btn-preview-doc"
-                                data-preview-uri="<%=Encode.forHtmlAttribute(ecUri)%>"
+                                data-preview-uri="<%=SafeEncode.forHtmlAttribute(ecUri)%>"
                                 title="<spring:message code="global.btn.preview"/>">
                                     <i class="fa-solid fa-eye"></i>
                                 </button>
@@ -454,24 +454,24 @@
                         <input type="hidden" name="type" value="prescription">
                         <input type="hidden" name="id" value="none">
                         <input type="hidden" name="uri" class="attachment-uri"
-                               value="<%=Encode.forHtmlAttribute(rxUri)%>"
+                               value="<%=SafeEncode.forHtmlAttribute(rxUri)%>"
                         >
                         <input type="hidden" name="title" class="attachment-title"
-                               value="${e:forHtmlAttribute(currentPrescTitle)}"
+                               value="${carlos:forHtmlAttribute(currentPrescTitle)}"
                         >
                         <div class="row align-items-center">
                             <div class="col">
                                 Current Prescriptions
                             </div>
                             <div class="col-auto">
                                 <button type="button" class="btn btn-sm btn-outline-secondary btn-preview-doc"
-                                data-preview-uri="<%=Encode.forHtmlAttribute(rxUri)%>"
+                                data-preview-uri="<%=SafeEncode.forHtmlAttribute(rxUri)%>"
                                 title="<spring:message code="global.btn.preview"/>">
                                     <i class="fa-solid fa-eye"></i>
                                 </button>
@@ -502,13 +502,13 @@

                     <form id="attachments-form" name="attachments-form" action="${pageContext.request.contextPath}/messenger/PreviewPDF" method="post" style="display:none;">
                         <input type="hidden" id="attachmentCount" name="attachmentCount"
-                               value="<%=Encode.forHtmlAttribute(request.getParameter("attachmentCount") == null ? "0" : request.getParameter("attachmentCount"))%>"/>
+                               value="<%=SafeEncode.forHtmlAttribute(request.getParameter("attachmentCount") == null ? "0" : request.getParameter("attachmentCount"))%>"/>
                         <input type="hidden" id="demographic_no" name="demographic_no"
-                               value="<%=Encode.forHtmlAttribute(demographic_no != null ? demographic_no : "")%>"/>
+                               value="<%=SafeEncode.forHtmlAttribute(demographic_no != null ? demographic_no : "")%>"/>
                         <input type="hidden" id="isPreview" name="isPreview"
-                               value="<%=Encode.forHtmlAttribute(request.getParameter("isPreview") == null ? "false" : request.getParameter("isPreview"))%>"/>
+                               value="<%=SafeEncode.forHtmlAttribute(request.getParameter("isPreview") == null ? "false" : request.getParameter("isPreview"))%>"/>
                         <input type="hidden" id="isAttaching" name="isAttaching"
-                               value="<%=Encode.forHtmlAttribute(request.getParameter("isAttaching") == null ? "false" : request.getParameter("isAttaching"))%>"/>
+                               value="<%=SafeEncode.forHtmlAttribute(request.getParameter("isAttaching") == null ? "false" : request.getParameter("isAttaching"))%>"/>
                     </form>

                 </div>
@@ -529,7 +529,7 @@
         <c:set var="jsAttachingTemplate">
             <i class="fa-solid fa-spinner fa-spin me-2"></i><spring:message code="global.btn.attaching" javaScriptEscape="true"/>
         </c:set>
-            var attachingTemplate = '${e:forJavaScript(jsAttachingTemplate)}';
+            var attachingTemplate = '${carlos:forJavaScript(jsAttachingTemplate)}';
         </script>
         <script type="text/javascript"
                 src="${pageContext.request.contextPath}/js/global.js"></script>
INNER_EOF

patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp.patch
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp.patch
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch

bash scripts/lint/check-encoder-null-safety.sh
