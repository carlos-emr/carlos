/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.app;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.owasp.csrfguard.CsrfGuard;
import org.owasp.csrfguard.CsrfGuardException;
import org.owasp.csrfguard.action.IAction;
import org.owasp.csrfguard.http.InterceptRedirectResponse;
import org.owasp.csrfguard.log.LogLevel;
import org.owasp.csrfguard.util.RandomGenerator;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import io.github.carlos_emr.OscarProperties;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/**
 * Oscar OscarCsrfGuardFilter
 * A CsrfGuardFilter implementation that supports detecting and paring multipart/form-data requests in addition to
 * the existing support
 */
public class OscarCsrfGuardFilter implements Filter {

    private FilterConfig filterConfig = null;

    @Override
    public void destroy() {
        filterConfig = null;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        CsrfGuard csrfGuard = CsrfGuard.getInstance();

        //maybe the short circuit to disable is set
        if (!csrfGuard.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        /* only work with HttpServletRequest objects */
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpSession session = httpRequest.getSession(false);

            /*
             * if there is no session and we aren't validating when no session exists.
             * OR if this page request is indicated as unprotected in the CsrfGuard properties.
             * This mainly applies to uploads coming through the authenticated SOAP or REST API.
             * If true: short circuit the process.
             */
            if ((session == null && !csrfGuard.isValidateWhenNoSessionExists())
                    || !csrfGuard.isProtectedPage(httpRequest.getRequestURI())) {
                filterChain.doFilter(httpRequest, response);
                return;
            }

            MiscUtils.getLogger().debug(String.format("CsrfGuard Filter analyzing request %s", httpRequest.getRequestURI()));

            // Default to not redirect unless csrf_do_redirect is set
            // TODO: reverse this when there are more pages by csrf covered 
            boolean doRedirect = false;
            if (OscarProperties.getInstance().getProperty("csrf_do_redirect") != null) {
                doRedirect = OscarProperties.getInstance().isPropertyActive("csrf_do_redirect");
            }
            if (!doRedirect) {
                IAction redirectActionToRemove = null;
                for (IAction action : csrfGuard.getActions()) {
                    if ("Redirect".equals(action.getName())) {
                        redirectActionToRemove = action;
                        break;
                    }
                }
                csrfGuard.getActions().remove(redirectActionToRemove);
            }

            InterceptRedirectResponse httpResponse = new InterceptRedirectResponse((HttpServletResponse) response, httpRequest, csrfGuard);

            if ((session != null && session.isNew()) && csrfGuard.isUseNewTokenLandingPage()) {
                csrfGuard.writeLandingPage(httpRequest, httpResponse);
            } else if (ServletFileUpload.isMultipartContent(httpRequest)) {
                MultiReadHttpServletRequest multiReadHttpRequest = new MultiReadHttpServletRequest(httpRequest);
                if (isValidMultipartRequest(multiReadHttpRequest, httpResponse)) {
                    filterChain.doFilter(multiReadHttpRequest, httpResponse);
                } else if (!doRedirect) {
                    filterChain.doFilter(multiReadHttpRequest, httpResponse);
                }
            } else if (csrfGuard.isValidRequest(httpRequest, httpResponse)) {
                filterChain.doFilter(httpRequest, httpResponse);
            } else if (!doRedirect) {
                filterChain.doFilter(httpRequest, httpResponse);
            }

            /* update tokens */
            csrfGuard.updateTokens(httpRequest);

        } else {
            filterConfig.getServletContext().log(String.format("[WARNING] CsrfGuard does not know how to work with requests of class %s ", request.getClass().getName()));

            filterChain.doFilter(request, response);
        }
    }

    @Override
    public void init(@SuppressWarnings("hiding") FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }

    private boolean isValidMultipartRequest(MultiReadHttpServletRequest request, HttpServletResponse response) {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        boolean valid = !csrfGuard.isProtectedPageAndMethod(request);
        HttpSession session = request.getSession(true);
        String tokenFromSession = (String) session.getAttribute(csrfGuard.getSessionKey());

        /** sending request to protected resource - verify token **/
        if (tokenFromSession != null && !valid) {
            try {
                if (csrfGuard.isAjaxEnabled() && isAjaxRequest(request)) {
                    String tokenFromRequest = request.getHeader(csrfGuard.getTokenName());

                    if (tokenFromRequest == null) {
                        /** FAIL: token is missing from the request **/
                        throw new CsrfGuardException("required token is missing from the request");
                    } else {
                        //if there are two headers, then the result is comma separated
                        if (!tokenFromSession.equals(tokenFromRequest)) {
                            if (tokenFromRequest.contains(",")) {
                                tokenFromRequest = tokenFromRequest.substring(0, tokenFromRequest.indexOf(',')).trim();
                            }
                            if (!tokenFromSession.equals(tokenFromRequest)) {
                                /** FAIL: the request token does not match the session token **/
                                throw new CsrfGuardException("request token does not match session token");
                            }
                        }
                    }
                } else if (csrfGuard.isTokenPerPageEnabled()) {
                    verifyPageToken(request);
                } else {
                    verifySessionToken(request);
                }
                valid = true;
            } catch (CsrfGuardException csrfe) {
                callActionsOnError(request, response, csrfe);
            }

            /** rotate session and page tokens **/
            if (!isAjaxRequest(request) && csrfGuard.isRotateEnabled()) {
                rotateTokens(request);
            }
            /** expected token in session - bad state and not valid **/
        } else if (tokenFromSession == null && !valid) {
            try {
                throw new CsrfGuardException("CsrfGuard expects the token to exist in session at this point");
            } catch (CsrfGuardException csrfe) {
                callActionsOnError(request, response, csrfe);

            }
        } else {
            /** unprotected page - nothing to do **/
        }

        return valid;
    }

    private boolean isAjaxRequest(MultiReadHttpServletRequest request) {
        return request.getHeader("X-Requested-With") != null;
    }

    private void verifyPageToken(MultiReadHttpServletRequest request) throws CsrfGuardException {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        HttpSession session = request.getSession(true);
        @SuppressWarnings("unchecked")
        Map<String, String> pageTokens = (Map<String, String>) session.getAttribute(CsrfGuard.PAGE_TOKENS_KEY);

        String tokenFromPages = (pageTokens != null ? pageTokens.get(request.getRequestURI()) : null);
        String tokenFromSession = (String) session.getAttribute(csrfGuard.getSessionKey());
        MultipartHttpServletRequest multipartRequest = new CommonsMultipartResolver().resolveMultipart(request);
        String tokenFromRequest = multipartRequest.getParameter(csrfGuard.getTokenName());

        if (tokenFromRequest == null) {
            /** FAIL: token is missing from the request **/
            throw new CsrfGuardException("required token is missing from the request");
        } else if (tokenFromPages != null) {
            if (!tokenFromPages.equals(tokenFromRequest)) {
                /** FAIL: request does not match page token **/
                throw new CsrfGuardException("request token does not match page token");
            }
        } else if (!tokenFromSession.equals(tokenFromRequest)) {
            /** FAIL: the request token does not match the session token **/
            throw new CsrfGuardException("request token does not match session token");
        }
    }

    private void verifySessionToken(MultiReadHttpServletRequest request) throws CsrfGuardException {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        HttpSession session = request.getSession(true);
        String tokenFromSession = (String) session.getAttribute(csrfGuard.getSessionKey());
        MultipartHttpServletRequest multipartRequest = new CommonsMultipartResolver().resolveMultipart(request);
        String tokenFromRequest = multipartRequest.getParameter(csrfGuard.getTokenName());

        if (tokenFromRequest == null) {
            /** FAIL: token is missing from the request **/
            throw new CsrfGuardException("required token is missing from the request");
        } else if (!tokenFromSession.equals(tokenFromRequest)) {
            /** FAIL: the request token does not match the session token **/
            throw new CsrfGuardException("request token does not match session token");
        }
    }

    private void callActionsOnError(MultiReadHttpServletRequest request,
                                    HttpServletResponse response, CsrfGuardException csrfe) {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        for (IAction action : csrfGuard.getActions()) {
            try {
                action.execute(request, response, csrfe, csrfGuard);
            } catch (CsrfGuardException exception) {
                csrfGuard.getLogger().log(LogLevel.Error, exception);
            }
        }
    }

    private void rotateTokens(MultiReadHttpServletRequest request) {
        CsrfGuard csrfGuard = CsrfGuard.getInstance();
        HttpSession session = request.getSession(true);

        /** rotate master token **/
        String tokenFromSession = null;

        try {
            tokenFromSession = RandomGenerator.generateRandomId(csrfGuard.getPrng(), csrfGuard.getTokenLength());
        } catch (Exception e) {
            throw new RuntimeException(String.format("unable to generate the random token - %s", e.getLocalizedMessage()), e);
        }

        session.setAttribute(csrfGuard.getSessionKey(), tokenFromSession);

        /** rotate page token **/
        if (csrfGuard.isTokenPerPageEnabled()) {
            @SuppressWarnings("unchecked")
            Map<String, String> pageTokens = (Map<String, String>) session.getAttribute(CsrfGuard.PAGE_TOKENS_KEY);

            try {
                pageTokens.put(request.getRequestURI(), RandomGenerator.generateRandomId(csrfGuard.getPrng(), csrfGuard.getTokenLength()));
            } catch (Exception e) {
                throw new RuntimeException(String.format("unable to generate the random token - %s", e.getLocalizedMessage()), e);
            }
        }
    }
}
