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
package io.github.carlos_emr.carlos.demographic.cdsexport;

import io.github.carlos_emr.carlos.demographic.cdsexport.cli.CdsExportCli;
import io.github.carlos_emr.carlos.demographic.cdsexport.cli.CdsExportSpringRuntime;

/** WAR-runtime entry point used by the installed CDS export command adapter. */
public final class CdsExport {
    private CdsExport() { }
    public static void main(String[] arguments) {
        try { System.exit(CdsExportCli.run(arguments, System.getenv(), System.out, System.err, CdsExportSpringRuntime::new)); }
        catch (VirtualMachineError failed) {
            try { System.err.println("CDS_EXPORT_FATAL: JVM stopped; reconcile the durable job"); }
            finally { Runtime.getRuntime().halt(4); }
        } catch (LinkageError failed) {
            System.err.println("CDS_EXPORT_RUNTIME: installed runtime is incompatible"); System.exit(2);
        } catch (Error failed) {
            System.err.println("CDS_EXPORT_FATAL: execution invariant failed; reconcile the durable job"); System.exit(4);
        }
    }
}
