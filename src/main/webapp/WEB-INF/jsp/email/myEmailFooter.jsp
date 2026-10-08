<!DOCTYPE html>

<%--
  Purpose: The logged-in user's own footer for the emails they send to patients (follow-up to
  #3981). Every user, doctor or front desk, starts with the clinic footer and can save their own.
  Key features: Shows the footer the compose screen fills in, the clinic footer, and a notice when
  a clinic change replaced (or, with that rule switched off, kept) the user's own footer. Every
  change is a POST to email/saveMyEmailFooter; the provider comes from the session.
  Request attributes: myFooter, followsClinicDefault, clinicFooter, clinicChangeNotice,
  clinicChangeKeptOwnFooter, myFooterTooLong, myFooterSaveConflict (set by ViewMyEmailFooter2Action and
  SaveMyEmailFooter2Action). The page needs _email write, as saving does.
  Request parameters: saved (true after a change).
  @since 2026-10-07
--%>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.email.core.EmailData" %>
<fmt:setBundle basename="oscarResources"/>

<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="page"/>
    <link rel="icon" href="${ctx}/images/favicon.ico"/>
    <title><fmt:message key="email.myFooter.title"/></title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
</head>
<body>
<div class="container mt-4 mb-4" style="max-width: 50rem;">
    <h3><fmt:message key="email.myFooter.title"/></h3>
    <p><fmt:message key="email.myFooter.intro"/></p>

    <c:if test="${param.saved eq 'true' and not myFooterTooLong}">
        <div class="alert alert-success" role="status" id="myFooterSaved"><fmt:message key="email.myFooter.saved"/></div>
    </c:if>
    <c:if test="${myFooterTooLong}">
        <div class="alert alert-danger" role="alert" id="myFooterTooLong"><fmt:message key="email.myFooter.tooLong"/></div>
    </c:if>
    <c:if test="${myFooterSaveConflict}">
        <div class="alert alert-danger" role="alert" id="myFooterSaveConflict"><fmt:message key="email.footer.saveConflict"/></div>
    </c:if>

    <%-- The clinic changed its footer: show the footer this user had until then and let them choose. --%>
    <c:if test="${clinicChangeNotice != null}">
        <div class="alert alert-warning" role="alert" id="clinicChangeNotice">
            <c:choose>
                <c:when test="${clinicChangeKeptOwnFooter}">
                    <p class="mb-2"><fmt:message key="email.myFooter.noticeKept"/></p>
                </c:when>
                <c:otherwise>
                    <p class="mb-2"><fmt:message key="email.myFooter.noticeReplaced"/></p>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${empty clinicChangeNotice}">
                    <p class="fst-italic"><fmt:message key="email.myFooter.none"/></p>
                </c:when>
                <c:otherwise>
                    <pre class="border bg-light p-2" style="white-space: pre-wrap;"><carlos:encode value="${clinicChangeNotice}"/></pre>
                </c:otherwise>
            </c:choose>
            <form action="${ctx}/email/saveMyEmailFooter" method="post" class="d-flex flex-wrap gap-2">
                <c:choose>
                    <c:when test="${clinicChangeKeptOwnFooter}">
                        <button type="submit" name="footerAction" value="useClinicDefault" class="btn btn-sm btn-primary">
                            <fmt:message key="email.myFooter.useClinic"/></button>
                        <button type="submit" name="footerAction" value="keepCurrent" class="btn btn-sm btn-outline-secondary">
                            <fmt:message key="email.myFooter.keepMine"/></button>
                    </c:when>
                    <c:otherwise>
                        <%-- With no previous footer (no clinic footer was set) there is nothing to put back. --%>
                        <c:if test="${not empty clinicChangeNotice}">
                            <button type="submit" name="footerAction" value="restorePrevious" class="btn btn-sm btn-primary">
                                <fmt:message key="email.myFooter.restorePrevious"/></button>
                        </c:if>
                        <button type="submit" name="footerAction" value="keepCurrent" class="btn btn-sm btn-outline-secondary">
                            <fmt:message key="email.myFooter.keepClinic"/></button>
                    </c:otherwise>
                </c:choose>
            </form>
        </div>
    </c:if>

    <form action="${ctx}/email/saveMyEmailFooter" method="post" id="myFooterForm">
        <p class="small text-muted" id="myFooterSource">
            <c:choose>
                <c:when test="${followsClinicDefault}"><fmt:message key="email.myFooter.followsClinic"/></c:when>
                <c:otherwise><fmt:message key="email.myFooter.ownFooter"/></c:otherwise>
            </c:choose>
        </p>
        <label for="myFooter" class="form-label"><fmt:message key="email.myFooter.label"/></label>
        <textarea class="form-control" id="myFooter" name="myFooter" rows="4" maxlength="<%= EmailData.FOOTER_MAX_LENGTH %>"
                  aria-describedby="myFooterEmptyHelp myFooterHelp"><carlos:encode value="${myFooter}"/></textarea>
        <div id="myFooterEmptyHelp" class="form-text"><fmt:message key="email.myFooter.emptyUsesClinic"/></div>
        <div id="myFooterHelp" class="form-text"><fmt:message key="email.compose.footer.help"/></div>
        <div class="d-flex flex-wrap gap-2 mt-2">
            <button type="submit" name="footerAction" value="save" class="btn btn-primary">
                <fmt:message key="email.myFooter.save"/></button>
            <c:if test="${not followsClinicDefault}">
                <button type="submit" name="footerAction" value="useClinicDefault" class="btn btn-outline-secondary">
                    <fmt:message key="email.myFooter.useClinic"/></button>
            </c:if>
        </div>
    </form>

    <h5 class="mt-4"><fmt:message key="email.myFooter.clinicFooterLabel"/></h5>
    <c:choose>
        <c:when test="${empty clinicFooter}">
            <p class="text-muted" id="clinicFooter"><fmt:message key="email.myFooter.noClinicFooter"/></p>
        </c:when>
        <c:otherwise>
            <pre class="border bg-light p-2" style="white-space: pre-wrap;" id="clinicFooter"><carlos:encode value="${clinicFooter}"/></pre>
        </c:otherwise>
    </c:choose>
</div>
</body>
</html>
