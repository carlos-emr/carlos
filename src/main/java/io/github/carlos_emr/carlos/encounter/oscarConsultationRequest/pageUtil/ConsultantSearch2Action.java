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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Read-only JSON endpoint behind the Consultant type-ahead on the Consultations list
 * ({@code encounter/consultation/searchConsultants}).
 *
 * <p>Returns {@code [{"label": "Last, First", "value": specId}, ...]}: at most
 * {@value #MAX_RESULTS} specialists that at least one consultation request was sent to, whose
 * name contains every whitespace/comma separated token of {@code keyword}. Keywords shorter than
 * {@value #MIN_KEYWORD_LENGTH} characters (after trimming) or longer than
 * {@value #MAX_KEYWORD_LENGTH} return an empty array without querying.</p>
 *
 * <p>The response is specialist directory data, not PHI, so it is safe to serve over GET; the
 * action changes nothing and is not a mutator for the GET-rejection contract. It writes the body
 * directly and returns {@code NONE} (direct-response rule). The keyword is never logged.</p>
 *
 * @since 2026-09-30
 */
public class ConsultantSearch2Action extends ActionSupport {

    static final int MAX_RESULTS = 20;
    static final int MIN_KEYWORD_LENGTH = 2;
    static final int MAX_KEYWORD_LENGTH = 100;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SecurityInfoManager securityInfoManager;
    private final ConsultationRequestDao consultationRequestDao;

    public ConsultantSearch2Action(SecurityInfoManager securityInfoManager,
                                   ConsultationRequestDao consultationRequestDao) {
        this.securityInfoManager = securityInfoManager;
        this.consultationRequestDao = consultationRequestDao;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        String keyword = request.getParameter("keyword");
        keyword = keyword == null ? "" : keyword.trim();

        List<ConsultantOptionDto> consultants = Collections.emptyList();
        if (keyword.length() >= MIN_KEYWORD_LENGTH && keyword.length() <= MAX_KEYWORD_LENGTH) {
            consultants = consultationRequestDao.searchDistinctConsultants(keyword, MAX_RESULTS);
        }

        ArrayNode array = JSON_MAPPER.createArrayNode();
        int count = 0;
        for (ConsultantOptionDto consultant : consultants) {
            if (count++ >= MAX_RESULTS) {
                break;
            }
            array.addObject()
                    .put("label", consultant.label())
                    .put("value", consultant.id());
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            response.getWriter().print(array.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to write consultant search response ({})", e.getClass().getSimpleName());
        }
        return NONE;
    }
}
