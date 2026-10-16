<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
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

    <div class="card shadow-sm rounded mt-4" id="clinicFooterCard">
        <div class="card-body">
            <h3><fmt:message key="admin.configureEmail.footer.policyHeading"/></h3>
            <p><fmt:message key="admin.configureEmail.footer.policyHelp"/></p>
            <c:if test="${clinicFooterMissing}">
                <p class="alert alert-warning"><fmt:message key="email.compose.footer.clinicRequired"/></p>
            </c:if>
            <c:if test="${clinicFooterOff}">
                <p class="alert alert-info" id="clinicFooterOffNote"><fmt:message key="admin.configureEmail.footer.offNote"/></p>
            </c:if>
            <c:if test="${not empty param.clinicFooterOutcome}">
                <p class="alert alert-info" role="status">
                    <c:choose>
                        <c:when test="${param.clinicFooterOutcome eq 'saved'}"><fmt:message key="admin.configureEmail.footer.policySaved"/></c:when>
                        <c:when test="${param.clinicFooterOutcome eq 'unchanged'}"><fmt:message key="admin.configureEmail.footer.policyUnchanged"/></c:when>
                        <c:when test="${param.clinicFooterOutcome eq 'settingsSaved'}"><fmt:message key="admin.configureEmail.footer.settingsSaved"/></c:when>
                        <c:when test="${param.clinicFooterOutcome eq 'stale'}"><fmt:message key="admin.configureEmail.footer.policyStale"/></c:when>
                        <c:when test="${param.clinicFooterOutcome eq 'conflict'}"><fmt:message key="admin.configureEmail.footer.policyConflict"/></c:when>
                        <c:otherwise><fmt:message key="admin.configureEmail.footer.policyInvalid"/></c:otherwise>
                    </c:choose>
                </p>
            </c:if>
            <%-- Footer text typed but not saved before the page reloaded (a logo upload or a refused save)
                 is back in the editor below; ViewConfigureEmail2Action puts it there once. --%>
            <c:if test="${clinicFooterDraftKept}">
                <p class="alert alert-warning" role="status" id="clinicFooterDraftKept"><fmt:message key="admin.configureEmail.footer.draftKept"/></p>
            </c:if>
            <c:if test="${not empty clinicFooterAttempted}">
                <div class="alert alert-secondary" id="clinicFooterAttemptedBox">
                    <p class="mb-1"><fmt:message key="admin.configureEmail.footer.attempted"/></p>
                    <div id="clinicFooterAttempted" class="border bg-white p-2" style="white-space: pre-wrap;"><carlos:encode value="${clinicFooterAttempted}"/></div>
                </div>
            </c:if>
            <div class="d-flex align-items-start" style="gap: 12px;">
                <c:if test="${clinicLogoShown}">
                    <img src="${ctx}/email/clinicEmailLogo" alt=""
                         style="display: block; max-width: 160px; height: auto; flex-shrink: 0;"/>
                </c:if>
                <div id="clinicFooterPreview" class="footer-editor-mail" style="white-space: pre-wrap; min-width: 0;"
                     data-footer-html="${carlos:forHtmlAttribute(clinicFooter)}"><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(io.github.carlos_emr.carlos.email.core.EmailFooterHtml.toPlainText((String) request.getAttribute("clinicFooter"))) %></div>
            </div>
            <p class="small mt-2 mb-0" id="clinicFooterModeShown"><span class="fw-semibold"><fmt:message key="admin.configureEmail.footer.modeLegend"/></span>:
                <fmt:message key="${clinicFooterOff ? 'admin.configureEmail.footer.modeOff' : 'admin.configureEmail.footer.modeRequired'}"/></p>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=false%>">
                <form action="${ctx}/admin/saveClinicEmailFooter" method="post" id="clinicFooterForm" class="mt-3">
                    <input type="hidden" name="clinicFooter" id="clinicFooter" value="${carlos:forHtmlAttribute(clinicFooter)}"/>
                    <input type="hidden" name="clinicFooterFingerprint" value="${carlos:forHtmlAttribute(clinicFooterFingerprint)}"/>
                    <%-- The window's Save footer sends this form, with the Required/Off choice and the logo
                         switch it holds: one save, no second button. --%>
                    <button type="button" class="btn btn-outline-primary" data-bs-toggle="modal" data-bs-target="#footerEditorModal"
                            data-footer-editor-target="clinicFooter" data-footer-editor-preview="clinicFooterPreview"
                            data-footer-editor-save="clinicFooterForm">
                        <fmt:message key="email.footerEditor.open"/></button>
                </form>
            </security:oscarSec>
        </div>
    </div>

    <%-- The clinic's email footer logo (issue #3981): shown left of the footer text in patient emails and
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
                <c:when test="${param.logoError eq 'UPLOAD_FAILED'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorUploadFailed"/></div>
                </c:when>
                <c:when test="${param.logoError eq 'COPY_TOO_BIG'}">
                    <div class="alert alert-danger" role="alert" id="clinicLogoError"><fmt:message key="admin.configureEmail.logo.errorCopyTooBig"/></div>
                </c:when>
            </c:choose>
            <%-- What emails carry: the clinic's own logo, else the CARLOS logo, unless turned off. --%>
            <c:choose>
                <c:when test="${not clinicLogoShown}">
                    <p class="text-muted" id="clinicLogoHidden"><fmt:message key="admin.configureEmail.logo.hidden"/></p>
                </c:when>
                <c:otherwise>
                    <img src="${ctx}/email/clinicEmailLogo" alt="<fmt:message key='admin.configureEmail.logo.alt'/>"
                         class="border bg-white p-2 d-block" style="max-width: 100%;" id="clinicLogoImage">
                    <c:choose>
                        <c:when test="${clinicLogoSet}">
                            <p class="small text-muted mt-1" id="clinicLogoSize">
                                <fmt:message key="admin.configureEmail.logo.size">
                                    <fmt:param value="${clinicLogoWidth}"/>
                                    <fmt:param value="${clinicLogoHeight}"/>
                                </fmt:message>
                            </p>
                        </c:when>
                        <c:otherwise>
                            <p class="small text-muted mt-1" id="clinicLogoDefault"><fmt:message key="admin.configureEmail.logo.carlosDefault"/></p>
                        </c:otherwise>
                    </c:choose>
                </c:otherwise>
            </c:choose>
            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=false%>">
                <%-- clinicLogoForm.js checks a picture against these limits before it is sent, so a picture
                     CARLOS would refuse never reloads the page; the server still checks everything. --%>
                <c:set var="logoCheckTooBig"><fmt:message key="admin.configureEmail.logo.checkTooBig"/></c:set>
                <c:set var="logoCheckTooLarge"><fmt:message key="admin.configureEmail.logo.checkTooLarge"/></c:set>
                <c:set var="logoCheckNotImage"><fmt:message key="admin.configureEmail.logo.errorNotImage"/></c:set>
                <div class="alert alert-danger d-none mt-3" role="alert" id="clinicLogoCheck"></div>
                <form action="${ctx}/admin/saveClinicEmailLogo" method="post" enctype="multipart/form-data" class="mt-3" id="clinicLogoForm"
                      data-max-bytes="<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_BYTES %>"
                      data-max-width="<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_WIDTH %>"
                      data-max-height="<%= io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.MAX_HEIGHT %>"
                      data-too-big="${carlos:forHtmlAttribute(logoCheckTooBig)}"
                      data-too-large="${carlos:forHtmlAttribute(logoCheckTooLarge)}"
                      data-not-image="${carlos:forHtmlAttribute(logoCheckNotImage)}">
                    <%-- Footer text put back after a refused save and not saved yet travels with the logo, so
                         the page can put it back again after this reload (ClinicFooterDraft). The script fills
                         these on submit. --%>
                    <input type="hidden" name="clinicFooterDraft" value=""/>
                    <input type="hidden" name="clinicFooterDraftFingerprint" value=""/>
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
    <%@ include file="/WEB-INF/jsp/admin/portalFooterAudit.jspf" %>
</div>
<script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
<script src="${ctx}/share/javascript/email/clinicLogoForm.js"></script>
<c:set var="footerEditorScopeKey" value="email.footerEditor.scopeClinic"/>
<c:set var="footerEditorApplyKey" value="email.footerEditor.save"/>
<c:set var="footerEditorClinicSettings" value="${true}"/>
<%@ include file="/WEB-INF/jsp/email/footerEditorModal.jspf" %>
</body>
