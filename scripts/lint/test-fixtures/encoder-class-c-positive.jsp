<%--
  POSITIVE fixture for scripts/lint/check-encoder-null-safety.py.
  This file MUST trigger a Class C violation — `forHtmlContent` used inside
  an HTML attribute value context. If the lint stops flagging this, the
  d2db61d4 bug class can recur silently.

  Used only by scripts/lint/test-encoder-null-safety.sh. Not deployed.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<input type="hidden" name="demoNo" value="${carlos:forHtmlContent(model.demoNo)}"/>
