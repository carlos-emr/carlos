<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE html>
<html>
<head>
    <title>Configure Email</title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link href="${ctx}/library/bootstrap/5.0.2/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
</head>
<body>
<div class="container mt-5">
    <div class="card shadow-sm rounded">
        <div class="card-body">
            <h3 class="card-title">Outgoing Email Accounts</h3>
            <p class="card-text mt-3">Please contact your server administrator for assistance. Outgoing email account
                configuration details must be added manually into the emailConfig table.</p>
            <p>Sample configuration data:</p>
            <dl class="row">
                <dt class="col-sm-3">emailType:</dt>
                <dd class="col-sm-9">SMTP</dd>
                <dt class="col-sm-3">emailProvider:</dt>
                <dd class="col-sm-9">GMAIL</dd>
                <dt class="col-sm-3">active:</dt>
                <dd class="col-sm-9">1</dd>
                <dt class="col-sm-3">senderFirstName:</dt>
                <dd class="col-sm-9">Test</dd>
                <dt class="col-sm-3">senderLastName:</dt>
                <dd class="col-sm-9">Clinic</dd>
                <dt class="col-sm-3">senderEmail:</dt>
                <dd class="col-sm-9">do.not.email@test.clinic</dd>
                <dt class="col-sm-3">configDetails:</dt>
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
            <p>Sample API configuration data:</p>
            <dl class="row">
                <dt class="col-sm-3">emailType:</dt>
                <dd class="col-sm-9">API</dd>
                <dt class="col-sm-3">emailProvider:</dt>
                <dd class="col-sm-9">SENDGRID</dd>
                <dt class="col-sm-3">active:</dt>
                <dd class="col-sm-9">1</dd>
                <dt class="col-sm-3">senderFirstName:</dt>
                <dd class="col-sm-9">Test</dd>
                <dt class="col-sm-3">senderLastName:</dt>
                <dd class="col-sm-9">Clinic</dd>
                <dt class="col-sm-3">senderEmail:</dt>
                <dd class="col-sm-9">do.not.email@test.clinic</dd>
                <dt class="col-sm-3">configDetails:</dt>
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
</div>
</body>