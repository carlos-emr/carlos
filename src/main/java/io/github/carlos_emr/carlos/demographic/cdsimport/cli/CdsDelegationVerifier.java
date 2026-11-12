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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessDelegationVerifier;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessScope;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Import compatibility facade; the shared verifier always enforces the original fixed import ticket directory. */
public final class CdsDelegationVerifier {
    private CdsDelegationVerifier() { }
    public record Delegation(String operator, long operatorUid, String nonce) {
        @Override public String toString() { return "Delegation[protected]"; }
    }
    public static Delegation verify(Map<String, String> environment, CdsCliArguments arguments,
            String[] exactArguments, Instant now) throws IOException {
        return imported(CdsProcessDelegationVerifier.verify(environment, CdsProcessScope.IMPORT,
                arguments.provider(), arguments.operation(), exactArguments, now));
    }
    static Delegation verifyDocument(byte[] bytes, String filename, CdsCliArguments arguments,
            String[] exactArguments, Instant now) throws IOException {
        return imported(CdsProcessDelegationVerifier.verifyDocument(bytes, filename, arguments.provider(),
                arguments.operation(), exactArguments, now));
    }
    public static byte[] digest(String[] arguments) { return CdsProcessDelegationVerifier.digest(arguments); }
    private static Delegation imported(CdsProcessDelegationVerifier.Delegation shared) {
        return new Delegation(shared.operator(), shared.operatorUid(), shared.nonce());
    }
}
