/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package carlostest5794;

import jakarta.servlet.ServletException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Disposable installed-test fixture; never included in the application WAR.
 *
 * <p>An ERROR dispatch sets the independent dispatcher witness header and emits
 * tainted HTML with status 500. A request with {@code mode=error} calls
 * {@code sendError(500)}; {@code mode=throw} throws a {@link ServletException}.
 * Other requests emit a private JSON 500 body for replacement by the filter.</p>
 *
 * @since 2026-11-07
 */
public final class ErrorSanitizationFaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            response.setStatus(500);
            response.setHeader("X-Carlos-Test-Error-Dispatch", "observed");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<html>java.lang.IllegalStateException: FAKE-private-error-5794</html>");
            return;
        }
        if ("error".equals(request.getParameter("mode"))) {
            response.sendError(500, "FAKE-private-error-5794");
            return;
        }
        if ("throw".equals(request.getParameter("mode"))) {
            throw new ServletException("FAKE-private-error-5794");
        }
        response.setStatus(500);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"FAKE-private-error-5794\"}");
    }
}
