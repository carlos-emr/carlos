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
package io.github.carlos_emr.carlos.login;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;

/** RFC 6238 SHA-1 TOTP with the former java-otp defaults (30 seconds, six digits). */
final class TotpGenerator {
    String getAlgorithm() {
        return "HmacSHA1";
    }

    Duration getTimeStep() {
        return Duration.ofSeconds(30);
    }

    String generateOneTimePasswordString(Key key, Instant timestamp) throws InvalidKeyException {
        final Mac mac;
        try {
            mac = Mac.getInstance(getAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required HmacSHA1 algorithm unavailable", e);
        }
        mac.init(key);
        // Millisecond division (including truncation toward zero before the epoch) matches java-otp.
        long counter = timestamp.toEpochMilli() / getTimeStep().toMillis();
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0F;
        int truncated = ByteBuffer.wrap(hash, offset, Integer.BYTES).getInt() & 0x7FFFFFFF;
        return String.format(Locale.getDefault(), "%06d", truncated % 1_000_000);
    }
}
