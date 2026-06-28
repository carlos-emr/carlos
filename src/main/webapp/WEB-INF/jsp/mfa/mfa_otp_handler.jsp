<%--

   Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
   This software is published under the GPL GNU General Public License.
   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   <p>
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.
   <p>
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
   <p>
   This software was written for
   Centre for Research on Inner City Health, St. Michael's Hospital,
   Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib prefix="csrf" uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:message key="mfa.otp.handler.placeholder" var="otpPlaceholder"/>
<fmt:message key="mfa.otp.handler.verify.button" var="otpVerifyButton"/>

<html>
<head>
  <style>
    :where([autocomplete=one-time-code]) {
      --otp-digits: 6; /* length */
      --otp-ls: 2ch;
      --otp-gap: 1.25;
      /* private consts */
      --_otp-bgsz: calc(var(--otp-ls) + 1ch);
      --_otp-digit: 0;
    
      all: unset;
      background:   linear-gradient(90deg, 
        var(--otp-bg, #BBB) calc(var(--otp-gap) * var(--otp-ls)),
        transparent 0),
        linear-gradient(90deg, 
        var(--otp-bg, #EEE) calc(var(--otp-gap) * var(--otp-ls)),
        transparent 0
      );
      background-position: calc(var(--_otp-digit) * var(--_otp-bgsz)) 0, 0 0;
      background-repeat: no-repeat, repeat-x;
      background-size: var(--_otp-bgsz) 100%;
      caret-color: var(--otp-cc, #222);
      caret-shape: block;
      clip-path: inset(0% calc(var(--otp-ls) / 2) 0% 0%);
      font-family: ui-monospace, monospace !important;
      font-size: var(--otp-fz, 2.5em);
      inline-size: calc(var(--otp-digits) * var(--_otp-bgsz));
      letter-spacing: var(--otp-ls);
      padding-block: var(--otp-pb, 1ch);
      padding-inline-start: calc(((var(--otp-ls) - 1ch) / 2) * var(--otp-gap));
      padding-left: 0.9ch !important;
    }

    .selector {
      background-position: 
        calc(var(--_otp-digit, 0) * var(--_otp-bgsz)) 0;
    }

  </style>

</head>
<body>
<div class="card-body d-flex align-items-center justify-content-center">

    <form action="<%= request.getContextPath() %>/mfa/loginMfa" method="post">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>">
        <input type="hidden" name="mfaRegistrationFlow" value="${requestScope.mfaRegistrationRequired}">
        <div class="">
            <div class="row mx-3 mx-md-3 mx-lg-3 px-3 px-md-3 px-lg-3 mb-3" style="display:flex; justify-content:center;">
                <input class="${not empty requestScope.mfaValidateCodeErr ? 'is-invalid' : ''}"
                       type="text" name="code" id="otpInput" autofocus
                       required
                       autocomplete="one-time-code"
                       inputmode="numeric"
                       maxlength="6"
                       pattern="\d{6}"
                       style="width: 16.6ch;"
                       oninput="if(this.value.length===6)this.form.submit();">
                <div id="otpInputFeedback" class="invalid-feedback">
                    ${carlos:forHtml(requestScope.mfaValidateCodeErr)}
                </div>
            </div>

            <div class="row mx-3 mx-md-3 mx-lg-3 px-3 px-md-3 px-lg-3 mb-3">
                <input name="btnSubmit" type="submit" class="btn btn-success btn-sm w-100"
                       id="verifyButton" value="${carlos:forHtmlAttribute(otpVerifyButton)}"/>
            </div>
        </div>

        <div class="px-3 mt-3">
            <span class="text-muted"><small><fmt:message key="mfa.otp.handler.instruction"/></small></span>
        </div>
    </form>
</div>
  <script>


const input = document.querySelector('[autocomplete=one-time-code');
input.addEventListener('input', () => input.style.setProperty('--_otp-digit', input.selectionStart));


  </script>
</body>

</html>
