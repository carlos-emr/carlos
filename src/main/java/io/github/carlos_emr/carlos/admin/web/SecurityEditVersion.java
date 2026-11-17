/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.model.Security;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

/** Opaque original-state comparison; never exposes stored credentials or grants authorization. */
public final class SecurityEditVersion {
    public static final String PARAMETER = "securityEditVersion";
    private SecurityEditVersion() { }

    public static String of(Security row) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Object[] values = {row.getId(), row.getUserName(), row.getProviderNo(), row.getPassword(), row.getPin(),
                    row.getBExpireset(), row.getDateExpiredate(), row.getBLocallockset(), row.getBRemotelockset(),
                    row.isForcePasswordReset(), row.isUsingMfa(), row.getMfaSecret(), row.getPasswordUpdateDate(),
                    row.getPinUpdateDate(), row.getLastUpdateDate(), row.getLastUpdateUser(), row.getOneIdKey(),
                    row.getOneIdEmail(), row.getDelagateOneIdEmail()};
            for (Object value : values) {
                if (value == null) {
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                } else {
                    String text = value instanceof Date date ? Long.toString(date.getTime()) : value.toString();
                    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", e);
        }
    }
}
