<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
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
                            <div class="border bg-white p-2 footer-editor-mail" id="clinicFooterCurrent"
                                 data-footer-html="<carlos:encode value='${clinicFooterCurrent}' context='htmlAttribute'/>"></div>
                        </c:otherwise>
                    </c:choose>
                </div>
            </c:if>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=false%>">
                <%-- Edited in the shared Edit footer window (footerEditorModal.jspf), as on the email screen;
                     the window writes the cleaned HTML into the hidden field and redraws the preview. --%>
                <form action="${ctx}/admin/saveClinicEmailFooter" method="post">
                    <input type="hidden" name="clinicFooterFingerprint" value="${carlos:forHtmlAttribute(clinicFooterFingerprint)}"/>
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <span class="form-label mb-0" id="clinicFooterLabel"><fmt:message key="admin.configureEmail.footer.label"/></span>
                        <button type="button" class="btn btn-outline-primary btn-sm" id="clinicFooterEdit"
                                data-bs-toggle="modal" data-bs-target="#footerEditorModal"
                                data-footer-editor-target="clinicFooter" data-footer-editor-preview="clinicFooterPreview"
                                aria-describedby="clinicFooterHelp">
                            <fmt:message key="email.footerEditor.open"/></button>
                    </div>
                    <input type="hidden" id="clinicFooter" name="clinicFooter" value="<carlos:encode value='${clinicFooter}' context='htmlAttribute'/>"/>
                    <div id="clinicFooterPreview" class="border bg-white p-2 footer-editor-mail" aria-labelledby="clinicFooterLabel"
                         data-empty-text="<fmt:message key='email.footerEditor.none'/>"></div>
                    <div id="clinicFooterHelp" class="form-text">
                        <fmt:message key="admin.configureEmail.footer.help"/></div>
                    <button type="submit" class="btn btn-primary mt-2">
                        <fmt:message key="admin.configureEmail.footer.save"/></button>
                </form>
            </security:oscarSec>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
                <div class="border bg-white p-2 footer-editor-mail" id="clinicFooterReadOnly"
                     data-footer-html="<carlos:encode value='${clinicFooter}' context='htmlAttribute'/>"
                     data-empty-text="<fmt:message key='email.footerEditor.none'/>"></div>
            </security:oscarSec>
        </div>
    </div>
    <%-- The clinic's email footer logo (issue #3981): shown above the footer in patient emails and
         carried inside each email. Only _admin writers get the form; everyone else sees the logo. --%>
    <div class="card shadow-sm rounded mt-4 mb-5" id="clinicLogoCard">
        <div class="card-body">
            <h4 class="card-title"><fmt:message key="admin.configureEmail.logo.heading"/></h4>
            <p class="card-text"><fmt:message key="admin.configureEmail.logo.intro"/></p>
            <c:if test="${param.logoSaved eq 'true'}">
                <div class="alert alert-success" role="status" id="clinicLogoSaved"><fmt:message key="admin.configureEmail.logo.saved"/></div>
            </c:if>
            <c:if test="${param.logoRemoved eq 'true'}">
                <div class="alert alert-success" role="status" id="clinicLogoRemoved"><fmt:message key="admin.configureEmail.logo.removed"/></div>
            </c:if>
            <%-- One branch per known reason code; the parameter itself is never shown or used as a key. --%>
            <c:choose>
                <c:when test="${param.logoError eq 'EMPTY'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorEmpty"/></div>
                </c:when>
                <c:when test="${param.logoError eq 'TOO_BIG'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorTooBig"/></div>
                </c:when>
                <c:when test="${param.logoError eq 'NOT_AN_IMAGE'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorNotImage"/></div>
                </c:when>
                <c:when test="${param.logoError eq 'TOO_LARGE'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorTooLarge"/></div>
                </c:when>
                <c:when test="${param.logoError eq 'COPY_TOO_BIG'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorCopyTooBig"/></div>
                </c:when>
            </c:choose>
            <c:choose>
                <c:when test="${clinicLogoSet}">
                    <img src="${ctx}/email/clinicEmailLogo" alt="<fmt:message key='admin.configureEmail.logo.alt'/>"
                         class="border bg-white p-2 d-block" style="max-width: 100%;" id="clinicLogoImage">
                    <p class="small text-muted mt-1" id="clinicLogoSize">
                        <fmt:message key="admin.configureEmail.logo.size">
                            <fmt:param value="${clinicLogoWidth}"/>
                            <fmt:param value="${clinicLogoHeight}"/>
                        </fmt:message>
                    </p>
                </c:when>
                <c:otherwise>
                    <p class="text-muted" id="clinicLogoNone"><fmt:message key="admin.configureEmail.logo.none"/></p>
                </c:otherwise>
            </c:choose>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=false%>">
                <form action="${ctx}/admin/saveClinicEmailLogo" method="post" enctype="multipart/form-data" class="mt-3" id="clinicLogoForm">
                    <label for="logoFile" class="form-label"><fmt:message key="admin.configureEmail.logo.choose"/></label>
                    <input class="form-control" type="file" id="logoFile" name="logoFile" accept="image/png,image/jpeg"
                           aria-describedby="clinicLogoHelp">
                    <div id="clinicLogoHelp" class="form-text"><fmt:message key="admin.configureEmail.logo.help"/></div>
                    <div class="d-flex flex-wrap gap-2 mt-2">
                        <button type="submit" name="logoAction" value="upload" class="btn btn-primary">
                            <fmt:message key="admin.configureEmail.logo.upload"/></button>
                        <c:if test="${clinicLogoSet}">
                            <button type="submit" name="logoAction" value="remove" class="btn btn-outline-danger">
                                <fmt:message key="admin.configureEmail.logo.remove"/></button>
                        </c:if>
                    </div>
                </form>
            </security:oscarSec>
        </div>
    </div>
</div>
<script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
<c:set var="footerEditorScopeKey" value="email.footerEditor.scopeClinic"/>
<c:set var="footerEditorApplyKey" value="email.footerEditor.applyFooter"/>
<%@ include file="/WEB-INF/jsp/email/footerEditorModal.jspf" %>
</body>
