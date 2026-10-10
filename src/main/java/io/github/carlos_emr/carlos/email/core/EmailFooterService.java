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
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Optional personal defaults only. Clinic text is owned by ClinicEmailFooterService. */
@Service
public class EmailFooterService {
    static final String USER_FOOTER = "email_footer";
    private final UserPropertyDAO properties;

    public EmailFooterService(UserPropertyDAO properties) {
        this.properties = properties;
    }

    /** No personal value, including a legacy empty row, means no personal layer. */
    public String ownFooter(String providerNo) {
        if (providerNo == null || providerNo.isBlank()) {
            return "";
        }
        return properties.getAllProperties(USER_FOOTER, List.of(providerNo)).stream()
                .min(Comparator.comparing(UserProperty::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(row -> EmailFooterHtml.clean(row.getValue())).orElse("");
    }

    /** Serialize only this user's saves, including first creation; clinic settings are never read. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void saveOwnFooter(String providerNo, String footer) {
        if (providerNo == null || providerNo.isBlank() || footer == null) {
            throw new IllegalArgumentException("Personal footer owner and value are required");
        }
        String cleaned = withinLimit(footer);
        properties.lockPersonalEmailFooterOwner(providerNo);
        var rows = properties.findPersonalEmailFooterForUpdate(providerNo);
        if (cleaned.isEmpty()) {
            rows.forEach(row -> properties.deletePersonalEmailFooterRow(providerNo, row.getId()));
            return;
        }
        UserProperty row = rows.isEmpty() ? new UserProperty() : rows.get(0);
        boolean changed = row.getId() == null || !cleaned.equals(row.getValue()) || !USER_FOOTER.equals(row.getName());
        row.setProviderNo(providerNo);
        row.setName(USER_FOOTER);
        row.setValue(cleaned);
        if (changed) properties.savePersonalEmailFooterRow(providerNo, row);
        rows.stream().skip(1).forEach(duplicate -> properties.deletePersonalEmailFooterRow(providerNo, duplicate.getId()));
    }

    static String withinLimit(String footer) {
        if (footer != null && footer.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH) {
            throw new FooterTooLongException();
        }
        String cleaned = EmailFooterHtml.clean(footer);
        if (cleaned.length() > EmailFooterHtml.MAX_HTML_LENGTH
                || EmailFooterHtml.visibleLength(cleaned) > EmailData.FOOTER_MAX_LENGTH) {
            throw new FooterTooLongException();
        }
        return cleaned;
    }

    public static final class FooterTooLongException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        FooterTooLongException() { super("Personal footer exceeds its length limit"); }
    }
}
