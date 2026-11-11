// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.NoSuchElementException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/** Same-origin CSRF-protected POST; success changes no unsaved clinical form fields. */
public class ConsultationHrmDetach2Action extends ActionSupport {
    @Override
    public String execute() throws Exception {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(405);
            return NONE;
        }
        try {
            int requestId = positiveId(request.getParameter("requestId"));
            int associationId = positiveId(request.getParameter("associationId"));
            var info = LoggedInInfo.getLoggedInInfoFromSession(request);
            var owner = SpringUtils.getBean(ConsultationRequestDao.class).find(requestId);
            if (owner == null || owner.getDemographicId() == null) throw new NoSuchElementException();
            if (!SpringUtils.getBean(SecurityInfoManager.class).hasPrivilege(
                    info, "_con", SecurityInfoManager.WRITE, owner.getDemographicId().intValue())) {
                throw new SecurityException("missing required sec object (_con)");
            }
            SpringUtils.getBean(ConsultationHrmAttachmentPersister.class).detach(info, requestId, associationId);
            response.setStatus(204);
        } catch (SecurityException denied) {
            response.sendError(403);
        } catch (NoSuchElementException missing) {
            response.sendError(404);
        } catch (IllegalArgumentException invalid) {
            response.sendError(400);
        }
        return NONE;
    }

    private static int positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
        return Integer.parseInt(value);
    }
}
