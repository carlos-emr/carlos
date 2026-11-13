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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.Date;
import java.util.Properties;
import org.springframework.stereotype.Service;

/** Compatibility entry point for the administrator's retention-controlled audit purge. */
@Service
public class AuditLogManager {
    /**
     * Backs up and removes rows through the selected calendar date. Configuration
     * is resolved only when invoked: a plain db_name must never break bean startup.
     * Failed/uncertain attempts retain protected backups and report failure.
     */
    public int purgeAuditLog(LoggedInInfo loggedInInfo, Date endDateToPurge) throws Exception {
        CarlosProperties configured = CarlosProperties.getInstance();
        Properties settings = new Properties();
        for (String name : new String[] {"log.purge.minDays", "log.purge.mysqldump", "log.purge.outputdir", "DOCUMENT_DIR"}) {
            String value = configured.getProperty(name);
            if (value != null && !value.isBlank()) settings.setProperty(name, value);
        }
        return SpringUtils.getBean(AuditLogPurgeService.class).purge(settings, endDateToPurge,
                () -> LogAction.addLogStrict(loggedInInfo, "AuditLogManager.purgeAuditLog", null, null, null,
                        java.time.Instant.ofEpochMilli(endDateToPurge.getTime())
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()));
    }
}
