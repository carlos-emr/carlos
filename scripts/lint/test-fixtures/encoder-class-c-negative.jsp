<%--
  NEGATIVE fixture for scripts/lint/check-encoder-null-safety.py.
  This file uses the correct attribute-context encoder (`forHtmlAttribute`)
  and the body-context encoder (`forHtmlContent`) appropriately. Lint must
  NOT flag it.

  Used only by scripts/lint/test-encoder-null-safety.sh. Not deployed.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<input type="hidden" name="demoNo" value="${carlos:forHtmlAttribute(model.demoNo)}"/>
<span>${carlos:forHtmlContent(model.demoName)}</span>
