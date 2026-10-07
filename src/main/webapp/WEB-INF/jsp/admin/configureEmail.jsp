<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.email.core.EmailData" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.configureEmail.title"/></title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
</head>
<body>
<div class="container mt-5">
    <div class="card shadow-sm rounded">
        <div class="card-body">
            <h3 class="card-title"><fmt:message key="admin.configureEmail.heading"/></h3>
            <p class="card-text mt-3"><fmt:message key="admin.configureEmail.help"/></p>
            <p><fmt:message key="admin.configureEmail.sampleData"/></p>
            <dl class="row">
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.emailType"/></dt>
                <dd class="col-sm-9">SMTP</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.emailProvider"/></dt>
                <dd class="col-sm-9">GMAIL</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.active"/></dt>
                <dd class="col-sm-9">1</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderFirstName"/></dt>
                <dd class="col-sm-9">Test</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderLastName"/></dt>
                <dd class="col-sm-9">Clinic</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderEmail"/></dt>
                <dd class="col-sm-9">do.not.email@test.clinic</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.configDetails"/></dt>
                <dd class="col-sm-9">
                    <code>
                        {
                        "host": "smtp.gmail.com",
                        "port": "587",
                        "username": "do.not.email@test.clinic",
                        "password": "1234567890"
                        }
                    </code>
                </dd>
            </dl>
            <p><fmt:message key="admin.configureEmail.sampleApiData"/></p>
            <dl class="row">
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.emailType"/></dt>
                <dd class="col-sm-9">API</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.emailProvider"/></dt>
                <dd class="col-sm-9">SENDGRID</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.active"/></dt>
                <dd class="col-sm-9">1</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderFirstName"/></dt>
                <dd class="col-sm-9">Test</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderLastName"/></dt>
                <dd class="col-sm-9">Clinic</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.senderEmail"/></dt>
                <dd class="col-sm-9">do.not.email@test.clinic</dd>
                <dt class="col-sm-3"><fmt:message key="admin.configureEmail.label.configDetails"/></dt>
                <dd class="col-sm-9">
                    <code>
                        {
                        "api_key": "1234512345123451234512345123451234512345",
                        "end_point": "https://api.example.com/mail/send"
                        }
                    </code>
                </dd>
            </dl>
        </div>
    </div>

    <%-- Clinic email footer (follow-up to #3981, issue #4093): every user starts with it and can save
         their own. Whether saving a changed footer replaces users' own footers or keeps them is
         EmailFooterService.REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE (ownFootersReplacedOnClinicChange
         picks the wording); either way the compose screen tells each user concerned on their next
         email, so the administrator is not asked to confirm. The form sends back the fingerprint of
         the footer it showed, so a save made after someone else changed the footer is shown again
         (clinicFooterChangedSinceShown, with clinicFooterCurrent) instead of overwriting it. --%>
    <div class="card shadow-sm rounded mt-4 mb-4" id="clinicEmailFooter">
        <div class="card-body">
            <h3 class="card-title"><fmt:message key="admin.configureEmail.footer.heading"/></h3>
            <p class="card-text mt-3" id="clinicFooterIntro">
                <c:choose>
                    <c:when test="${ownFootersReplacedOnClinicChange}"><fmt:message key="admin.configureEmail.footer.intro"/></c:when>
                    <c:otherwise><fmt:message key="admin.configureEmail.footer.introKeep"/></c:otherwise>
                </c:choose>
            </p>
            <c:if test="${param.clinicFooterSaved eq 'true' and not clinicFooterTooLong}">
                <div class="alert alert-success" role="status" id="clinicFooterSaved">
                    <fmt:message key="admin.configureEmail.footer.saved"/></div>
            </c:if>
            <c:if test="${param.clinicFooterUnchanged eq 'true'}">
                <div class="alert alert-info" role="status" id="clinicFooterUnchanged">
                    <fmt:message key="admin.configureEmail.footer.unchanged"/></div>
            </c:if>
            <c:if test="${clinicFooterTooLong}">
                <div class="alert alert-danger" role="alert" id="clinicFooterTooLong">
                    <fmt:message key="admin.configureEmail.footer.tooLong"/></div>
            </c:if>
            <c:if test="${clinicFooterSaveConflict}">
                <div class="alert alert-danger" role="alert" id="clinicFooterSaveConflict">
                    <fmt:message key="email.footer.saveConflict"/></div>
            </c:if>
            <c:if test="${clinicFooterChangedSinceShown}">
                <div class="alert alert-warning" role="alert" id="clinicFooterChangedSinceShown">
                    <p class="mb-2"><fmt:message key="admin.configureEmail.footer.changedSinceShown"/></p>
                    <c:choose>
                        <c:when test="${empty clinicFooterCurrent}">
                            <p class="fst-italic mb-0"><fmt:message key="email.myFooter.noClinicFooter"/></p>
                        </c:when>
                        <c:otherwise>
                            <pre class="border bg-light p-2 mb-0" style="white-space: pre-wrap;" id="clinicFooterCurrent"><carlos:encode value="${clinicFooterCurrent}"/></pre>
                        </c:otherwise>
                    </c:choose>
                </div>
            </c:if>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=false%>">
                <form action="${ctx}/admin/saveClinicEmailFooter" method="post">
                    <input type="hidden" name="clinicFooterFingerprint" value="${carlos:forHtmlAttribute(clinicFooterFingerprint)}"/>
                    <label for="clinicFooter" class="form-label">
                        <fmt:message key="admin.configureEmail.footer.label"/></label>
                    <textarea class="form-control" id="clinicFooter" name="clinicFooter" rows="4" maxlength="<%= EmailData.FOOTER_MAX_LENGTH %>"
                              aria-describedby="clinicFooterHelp"><carlos:encode value="${clinicFooter}"/></textarea>
                    <div id="clinicFooterHelp" class="form-text">
                        <fmt:message key="admin.configureEmail.footer.help"/></div>
                    <button type="submit" class="btn btn-primary mt-2">
                        <fmt:message key="admin.configureEmail.footer.save"/></button>
                </form>
            </security:oscarSec>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
                <pre class="border bg-light p-2" style="white-space: pre-wrap;"><carlos:encode value="${clinicFooter}"/></pre>
            </security:oscarSec>
        </div>
    </div>
</div>
</body>
