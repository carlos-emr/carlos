/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package carlostest5794;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Disposable installed-test fixture; never included in the application WAR. */
public final class ErrorSanitizationFaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if ("throw".equals(request.getParameter("mode"))) {
            throw new ServletException("FAKE-private-error-5794");
        }
        response.setStatus(500);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"FAKE-private-error-5794\"}");
    }
}
