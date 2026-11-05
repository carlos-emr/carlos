/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import io.github.carlos_emr.carlos.commn.model.Tickler;

/** Original tickler state, including append-only history identities for same-timestamp writes. */
public final class TicklerEditVersion {
    public static final String PARAMETER = "ticklerEditVersion";

    private TicklerEditVersion() { }

    public static String of(Tickler tickler) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Object[] values = {tickler.getId(), tickler.getDemographicNo(), tickler.getProgramId(),
                    tickler.getMessage(), tickler.getStatus(), tickler.getCreateDate(), tickler.getUpdateDate(),
                    tickler.getServiceDate(), tickler.getCreator(), tickler.getPriority(), tickler.getTaskAssignedTo(),
                    tickler.getCategoryId(),
                    tickler.getComments().stream().map(comment -> String.valueOf(comment.getId())).sorted().toList(),
                    tickler.getUpdates().stream().map(update -> String.valueOf(update.getId())).sorted().toList()};
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
