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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsImportCli;
import io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsSpringRuntime;

/** Supported WAR-runtime CLI adapter shared by host and container launchers. */
public final class CdsImport {
    private CdsImport() { }
    public static void main(String[] args) {
        try {
            int outcome = CdsImportCli.run(args, System.getenv(), System.out, System.err, CdsSpringRuntime::new);
            System.exit(outcome);
        } catch (VirtualMachineError fatal) {
            // An exhausted or broken VM cannot safely allocate a report or run
            // normal cleanup. Physical connections close with the process;
            // durable holds/receipts remain authoritative for reconciliation.
            try {System.err.println("CDS_FATAL: JVM stopped; inspect the durable run before resuming"); }
            finally {Runtime.getRuntime().halt(4); }
        } catch (LinkageError incompatible) {
            System.err.println("CDS_RUNTIME: installed runtime is incompatible; inspect durable run state");
            System.exit(2);
        } catch (Error invariant) {
            System.err.println("CDS_FATAL: execution invariant failed; inspect the durable run before resuming");
            System.exit(4);
        }
    }
}
