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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import java.io.IOException;

/** Thin process port; the authorized application facade owns durable intent, current permissions and clinical outcomes. */
public interface CdsExportCliRuntime extends AutoCloseable {
    String accept() throws IOException;
    String resolveRunId();
    CdsExportJobDto status(String runId);
    void resume(String runId);
    void cancel(String runId);
    CdsExportJobDto execute(String runId);
    /** Reports/downloads remain inaccessible until a separately verified artifact has actually been published. */
    void report(String runId) throws IOException;
    void download(String runId) throws IOException;
    void interruptWait();
    @Override void close();
}
