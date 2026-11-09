/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.managers.AppointmentUpdatePage;
import io.github.carlos_emr.carlos.utility.AsciiStrings;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Stateless query positions, bound to the caller and complete query context. They grant no access. */
public final class ScheduleSyncHeaders {
    public static final String PROFILE = "X-Carlos-Schedule-Sync-Profile";
    public static final String CURSOR = "X-Carlos-Schedule-Cursor";
    public static final String NEXT = "X-Carlos-Schedule-Next-Cursor";
    public static final String MORE = "X-Carlos-Schedule-Has-More";
    static final String KEY = ScheduleSyncHeaders.class.getName();
    static final String RESULT = KEY + ".result";
    private ScheduleSyncHeaders() { }

    public record Selection(boolean keyset, long afterMillis, int afterId, int limit, byte[] context, String originalCursor) {
        public Selection { context = context.clone(); }
        @Override public byte[] context() { return context.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Selection value && keyset == value.keyset && afterMillis == value.afterMillis
                    && afterId == value.afterId && limit == value.limit
                    && java.util.Arrays.equals(context, value.context)
                    && java.util.Objects.equals(originalCursor, value.originalCursor);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(keyset, afterMillis, afterId, limit, java.util.Arrays.hashCode(context), originalCursor);
        }
        @Override public String toString() {
            return "Selection[keyset=" + keyset + ", afterMillis=" + afterMillis + ", afterId=" + afterId
                    + ", limit=" + limit + ", context=" + java.util.Arrays.toString(context)
                    + ", originalCursor=" + originalCursor + "]";
        }
        public String wireName() { return keyset ? "timestamp-id" : "legacy-timestamp"; }
    }
    record Result(boolean more, String next) { }

    public static Selection select(LoggedInInfo actor, String operation, Date initial, int limit, Object... filters) {
        if (initial == null || limit < 0 || limit > io.github.carlos_emr.carlos.commn.dao.AbstractDao.MAX_LIST_RETURN_SIZE) {
            throw client("Invalid schedule update timestamp or page size");
        }
        Message message = PhaseInterceptorChain.getCurrentMessage();
        String profile = header(message, PROFILE), cursor = header(message, CURSOR);
        boolean keyset = "timestamp-id".equals(profile);
        if (profile != null && !keyset && !"legacy-timestamp".equals(profile)) throw client("Unsupported schedule sync profile");
        if (!keyset && cursor != null) throw client("Schedule cursor requires the timestamp-id sync profile");
        if (keyset && limit == 0) throw client("Timestamp-id schedule page size must be positive");
        byte[] context = context(actor == null ? null : actor.getLoggedInProviderNo(), operation, initial.getTime(), filters);
        long after = initial.getTime(); int id = Integer.MAX_VALUE;
        if (cursor != null) {
            byte[] decoded;
            try { decoded = Base64.getUrlDecoder().decode(cursor); }
            catch (IllegalArgumentException invalid) { throw client("Invalid schedule cursor"); }
            if (decoded.length != 45 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(cursor)) {
                throw client("Invalid schedule cursor");
            }
            var buffer = ByteBuffer.wrap(decoded);
            if (buffer.get() != 1) throw client("Unsupported schedule cursor version");
            after = buffer.getLong(); id = buffer.getInt(); byte[] bound = new byte[32]; buffer.get(bound);
            if (after <= initial.getTime() || id < 1 || !MessageDigest.isEqual(context, bound)) {
                throw client("Schedule cursor does not match this query");
            }
        }
        var selection = new Selection(keyset, after, id, limit, context, cursor);
        if (message != null && message.getExchange() != null) message.getExchange().put(KEY, selection);
        return selection;
    }

    public static <T> void complete(Selection selection, AppointmentUpdatePage<T> page,
            Function<T, Date> timestamp, Function<T, Integer> identity) {
        if (page == null || page.items().size() > selection.limit() || page.hasMore() && page.items().size() != selection.limit()) {
            throw new IllegalStateException("Invalid schedule sync result");
        }
        long time = selection.afterMillis(); int id = selection.afterId();
        for (T row : page.items()) {
            if (row == null || timestamp.apply(row) == null || identity.apply(row) == null || identity.apply(row) < 1) {
                throw new IllegalStateException("Schedule sync result has no stable position");
            }
            long next = timestamp.apply(row).getTime(); int nextId = identity.apply(row);
            if (next < time || next == time && nextId <= id) throw new IllegalStateException("Schedule sync result is not ordered");
            time = next; id = nextId;
        }
        String next = selection.originalCursor();
        if (!page.items().isEmpty()) {
            if (!Long.valueOf(time).equals(page.updatedMillis()) || !Integer.valueOf(id).equals(page.afterId())) {
                throw new IllegalStateException("Schedule sync result position is inconsistent");
            }
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(45).put((byte) 1).putLong(time).putInt(id).put(selection.context()).array());
        }
        Message message = PhaseInterceptorChain.getCurrentMessage();
        if (message != null && message.getExchange() != null) message.getExchange().put(RESULT, new Result(page.hasMore(), next));
    }

    private static byte[] context(String actor, String operation, long initial, Object[] filters) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.DataOutputStream(bytes)) {
                output.writeUTF(operation); output.writeLong(initial); writePart(output, actor);
                for (Object filter : filters) writePart(output, filter);
            }
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Schedule cursor context is unavailable", failure);
        }
    }
    private static void writePart(java.io.DataOutputStream output, Object value) throws java.io.IOException {
        if (value == null) output.writeByte(0);
        else if (value instanceof Integer number) { output.writeByte(1); output.writeInt(number); }
        else if (value instanceof String string) {
            byte[] utf8 = string.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.writeByte(2); output.writeInt(utf8.length); output.write(utf8);
        } else if (value instanceof Boolean flag) { output.writeByte(3); output.writeBoolean(flag); }
        else throw new IllegalArgumentException("Unsupported schedule query context type");
    }
    @SuppressWarnings("unchecked")
    private static String header(Message message, String name) {
        if (message == null) return null;
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        if (headers == null) return null;
        boolean found = false; String result = null;
        for (var entry : headers.entrySet()) {
            if (!AsciiStrings.equalsIgnoreCase(name, entry.getKey())) continue;
            if (found || entry.getValue() == null || entry.getValue().size() != 1) throw client("Duplicate or invalid schedule sync header");
            found = true; result = entry.getValue().getFirst();
            if (result == null || result.isEmpty() || result.length() > 128) throw client("Invalid schedule sync header");
        }
        return result;
    }
    private static SoapFault client(String message) { return new SoapFault(message, SoapFault.FAULT_CODE_CLIENT); }
}
