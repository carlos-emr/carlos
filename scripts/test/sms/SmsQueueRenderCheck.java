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
 */
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel.*;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Runs the real queue JSP and includes in an isolated Tomcat, using synthetic view models only. */
public final class SmsQueueRenderCheck {
    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(args[0]);
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        Context context = tomcat.addWebapp("/carlos", Path.of(args[1]).toAbsolutePath().toString());
        context.setParentClassLoader(SmsQueueRenderCheck.class.getClassLoader());
        Tomcat.addServlet(context, "fixture", new FixtureServlet());
        context.addServletMappingDecoded("/admin/SmsQueue", "fixture");
        try {
            tomcat.start();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/carlos/admin/SmsQueue";
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                int checks = 0;
                for (String mode : List.of("empty", "full", "hidden", "withoutDemographics")) {
                    for (String window : List.of("7d", "30d", "90d", "all", "junk")) {
                        String html = get(client, base + "?mode=" + mode + "&window=" + window);
                        String selected = SmsQueueWindow.fromParameter(window).parameterValue();
                        require(html.matches("(?s).*value=\"" + selected + "\"\\s+selected.*"), "selected window");
                        require(!html.contains("???"), "all labels resolve");
                        require(html.contains("smsQueueScheduler"), "scheduler renders");
                        if (mode.equals("empty")) {
                            require(html.contains("No outbound text messages are recorded for this SMS provider."), "empty queue message");
                            require(!html.contains("***1212"), "empty queue has no rows");
                        } else {
                            require(html.contains("Blocked by consent"), "combined consent status");
                            require(html.contains("Rows hidden: 2"), "hidden row count");
                            require(html.contains("All time: 80"), "all-time failure total");
                            if (mode.equals("hidden")) {
                                require(!html.contains("***1212") && !html.contains("VISIBLE_CODE"), "hidden-only list");
                            } else {
                                require(html.contains("***1212") && html.contains("VISIBLE_CODE"), "visible redacted row");
                                require(html.contains("&lt;script&gt;probe&lt;/script&gt;"), "HTML encoding");
                                require(!html.contains("<script>probe</script>"), "no unescaped model text");
                                require(html.contains("987654") != mode.equals("withoutDemographics"), "demographic column gate");
                            }
                        }
                        checks++;
                    }
                }
                System.out.println("PASS: " + checks + " real JSP renders (empty/full/hidden rows, demographic column, windows, labels, encoding)");
            }
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private static String get(HttpClient client, String url) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url))
                .header("Accept-Language", "en").timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 200, "HTTP status " + response.statusCode());
        return response.body();
    }

    private static void require(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError(description);
        }
    }

    private static final class FixtureServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            String mode = request.getParameter("mode");
            boolean empty = "empty".equals(mode);
            boolean hidden = "hidden".equals(mode);
            boolean showDemographics = !"withoutDemographics".equals(mode);
            Row row = new Row("12345", "STUB", "FAILED", "2026-01-01 10:00", "2026-01-02 10:00",
                    "", "", 1, "<script>probe</script>", "", "987654", "***1212");
            RowList rows = empty ? RowList.EMPTY : new RowList(hidden ? List.of() : List.of(row), 2);
            List<CodeCount> codes = empty || hidden ? List.of() : List.of(new CodeCount("VISIBLE_CODE", 1));
            ProviderQueue provider = new ProviderQueue("STUB", empty ? 0 : 100,
                    List.of(new StatusCount("FAILED", 80), new StatusCount("BLOCKED_BY_CONSENT", 20)),
                    empty ? 0 : 3, rows, empty ? 0 : 3, rows,
                    empty ? 0 : 3, 80, codes, rows, empty ? 0 : 3, 20, codes, rows);
            Scheduler scheduler = new Scheduler(!empty, !empty, !empty, false,
                    empty ? "" : "2026-01-02 10:00", empty ? "" : "2026-01-02 10:01", empty ? "" : "COMPLETED", 3);
            request.setAttribute("smsQueue", new SmsQueueViewModel("2026-01-02 10:02", 5, 5, 50,
                    showDemographics, SmsQueueWindow.fromParameter(request.getParameter("window")).parameterValue(),
                    SmsQueueWindow.parameterValues(), scheduler, List.of(provider)));
            request.getRequestDispatcher("/WEB-INF/jsp/admin/smsQueue.jsp").forward(request, response);
        }
    }
}
