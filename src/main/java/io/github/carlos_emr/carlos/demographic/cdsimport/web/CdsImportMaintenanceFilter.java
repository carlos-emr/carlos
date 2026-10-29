/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 * Modifications made by CARLOS Contributors in 2026: enforce CDS maintenance and disclosure compatibility.
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.ClinicalStartupCoordinator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

/**
 * While durable maintenance/holds exist, clinical HTTP reads/egress stay closed as well as writes.
 * Existing authentication and importer actions retain their own authorization/CSRF checks.
 * Pool-level admission independently excludes background/non-HTTP clinical SQL.
 */
public final class CdsImportMaintenanceFilter implements Filter {
    private static final String SESSION_HEARTBEAT = "/status/SessionHeartbeat";
    private static final Set<String> ROUTES = Set.of("/", "/index", "/index.jsp", "/login", "/login.do",
            "/logout", "/logout.do", "/logoutPage", "/loginfailed", "/forcepasswordreset",
            "/forcepasswordresetSubmit", "/location", "/select_facility", "/csrfguard",
            "/mfa/loginMfa", "/securityRecord/mfa", "/form/importUpload", "/form/cdsUpload", "/form/cdsImportSubmit", "/form/cdsImportStatus", "/form/cdsImportDiagnostic",
            "/form/cdsImportControl", "/form/cdsImportReport", "/form/importLogDownload", SESSION_HEARTBEAT);
    private static final String[] STATIC = {"/css/", "/js/", "/images/", "/fonts/", "/library/", "/loginResource/"};
    // Exact dependencies of the native login's global-head.jspf. Other shared
    // resources remain subject to maintenance admission.
    private static final Set<String> LOGIN_ASSETS = Set.of("/share/css/global.css", "/share/css/searchBox.css",
            "/share/css/transitions.css", "/share/javascript/Oscar.js", "/share/javascript/carlos-ajax.js");
    private enum UnavailableReason { STARTUP_PENDING, IMPORT_MAINTENANCE }
    private final Supplier<CdsImportMaintenanceService> maintenance;

    /** Servlet-container compatibility boundary; dependency resolution is deferred until context startup completes. */
    public CdsImportMaintenanceFilter() { this(() -> SpringUtils.getBean(CdsImportMaintenanceService.class)); }
    public CdsImportMaintenanceFilter(Supplier<CdsImportMaintenanceService> maintenance) { this.maintenance = maintenance; }

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest http) || !(response instanceof HttpServletResponse reply)) {
            chain.doFilter(request, response); return;
        }
        String path = http.getRequestURI().substring(http.getContextPath().length());
        // The heartbeat reads only this browser's session, never clinical data.
        // Shared login scripts must still be able to observe session expiry
        // while clinical access and background writers remain closed.
        boolean loginAssetRead = !(LOGIN_ASSETS.contains(path) || SESSION_HEARTBEAT.equals(route(path)))
                || "GET".equals(http.getMethod()) || "HEAD".equals(http.getMethod());
        if (allowed(path) && loginAssetRead) { chain.doFilter(request, response); return; }
        boolean blocked;
        try { blocked = maintenance.get().isClinicalAccessBlocked(); }
        catch (RuntimeException unavailable) { blocked = true; }
        if (!blocked) {
            Object startup = http.getServletContext().getAttribute(ClinicalStartupCoordinator.ATTRIBUTE);
            if (!(startup instanceof ClinicalStartupCoordinator coordinator) || coordinator.isReady()) {
                chain.doFilter(request, response); return;
            }
            unavailable(reply, UnavailableReason.STARTUP_PENDING);
            return;
        }
        unavailable(reply, UnavailableReason.IMPORT_MAINTENANCE);
    }

    private static void unavailable(HttpServletResponse reply, UnavailableReason reason) throws IOException {
        reply.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        reply.setHeader("Cache-Control", "no-store");
        reply.setHeader("Retry-After", "60");
        reply.setContentType("text/plain;charset=UTF-8");
        // Administration pages are also closed during maintenance. Give a direct
        // recovery route without reflecting request data or assuming a root deployment.
        switch (reason) {
            case STARTUP_PENDING -> reply.getWriter().write("Clinical startup initialization is pending. "
                    + "Authorized operators can open the CDS import page by adding "
                    + "/form/importUpload to the application base address.");
            case IMPORT_MAINTENANCE -> reply.getWriter().write(
                    "Clinical access is unavailable during CDS import maintenance or chart reconciliation. "
                    + "Authorized operators can open the CDS import page by adding "
                    + "/form/importUpload to the application base address.");
            default -> throw new IllegalStateException("Unsupported clinical availability reason");
        }
    }

    static boolean allowed(String path) {
        if (path == null || path.contains("%") || path.contains(";") || path.contains("\\")
                || path.contains("//") || path.contains("/../") || path.contains("/./")) return false;
        if (ROUTES.contains(route(path))) return true;
        if (LOGIN_ASSETS.contains(path)) return true;
        if (path.startsWith("/WEB-INF/jsp/login/") || path.startsWith("/WEB-INF/jsp/mfa/")) return true;
        if (path.equals("/WEB-INF/jsp/demographic/demographicImport.jsp")) return true;
        for (String prefix : STATIC) if (path.startsWith(prefix)) return true;
        return false;
    }

    private static String route(String path) {
        return path.endsWith(".do") ? path.substring(0,path.length()-3)
                : path.endsWith(".action") ? path.substring(0,path.length()-7) : path;
    }
}
