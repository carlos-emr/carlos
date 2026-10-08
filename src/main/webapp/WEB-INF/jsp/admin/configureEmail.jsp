<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
</body>
