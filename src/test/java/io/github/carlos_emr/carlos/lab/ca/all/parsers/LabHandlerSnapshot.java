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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Renders everything a lab {@link MessageHandler} exposes about one HL7 message as stable text.
 *
 * <p>Built for the HAPI HL7 v2 1.0.1 to 2.6.0 upgrade (issue #5414): every public getter the
 * results display, PDF export, inbox routing and lab matching code can call is invoked over the
 * message's real OBR/OBX/comment index ranges, so a change in how HAPI splits fields, unescapes
 * text, counts repetitions or reports errors shows up as a line-level difference against a
 * snapshot recorded with the previous library. The snapshot holds only synthetic fixture data.</p>
 *
 * <p>Rules that keep the output deterministic across machines and runs:</p>
 * <ul>
 *   <li>Only {@code get*}/{@code is*} instance methods whose parameters are all {@code int} and
 *       whose result is text, a number, a boolean, a date or a collection of those are called.</li>
 *   <li>{@code getAge()} (today's date) and {@code getXML()} (unused serializer output) are
 *       skipped.</li>
 *   <li>Dates are shown as date-time text in the JVM zone and calendar, the ones handlers parse
 *       HL7 timestamps with. Callers pin the zone, because some handlers also convert timestamps
 *       that carry an offset into the server's local time.</li>
 *   <li>A thrown exception is recorded by class name, never by message text, which carries
 *       library-specific wording.</li>
 *   <li>Values longer than {@value #MAX_INLINE_CHARS} characters (embedded PDFs) are recorded as
 *       a SHA-256 digest and length.</li>
 * </ul>
 */
final class LabHandlerSnapshot {

    /** Upper bound on inlined value length; longer values are digested. */
    static final int MAX_INLINE_CHARS = 4096;

    /** Bounds that stop a malformed count from turning the snapshot into a runaway loop. */
    private static final int MAX_OBR = 200;
    private static final int MAX_OBX = 1000;
    private static final int MAX_COMMENTS = 200;

    /**
     * Getters left out of the snapshot: {@code getAge()} depends on today's date, and
     * {@code getXML()} has no production caller and only echoes the layout of HAPI's XML
     * serializer, which is not clinical data.
     */
    private static final Set<String> SKIPPED = Set.of("getAge", "getXML");

    private LabHandlerSnapshot() {
    }

    /**
     * Instantiates {@code handlerClass}, initialises it with {@code message} and renders every
     * observable value.
     *
     * @return the snapshot text, or a single {@code init threw ...} line when initialisation fails
     */
    static String render(Class<? extends MessageHandler> handlerClass, String message) {
        StringBuilder out = new StringBuilder();
        out.append("handler ").append(handlerClass.getSimpleName()).append('\n');
        MessageHandler handler;
        try {
            handler = handlerClass.getDeclaredConstructor().newInstance();
            handler.init(message);
        } catch (Throwable t) {
            return out.append("init threw ").append(describe(t)).append('\n').toString();
        }

        int obrCount = count(handler::getOBRCount, MAX_OBR);
        out.append("OBR count ").append(obrCount).append('\n');

        for (Method method : observableMethods(handlerClass)) {
            switch (method.getParameterCount()) {
                case 0 -> line(out, handler, method);
                case 1 -> {
                    for (int i = 0; i < obrCount; i++) {
                        line(out, handler, method, i);
                    }
                }
                case 2 -> {
                    for (int i = 0; i < obrCount; i++) {
                        final int obr = i;
                        int inner = "getOBRComment".equals(method.getName())
                                ? count(() -> handler.getOBRCommentCount(obr), MAX_COMMENTS)
                                : count(() -> handler.getOBXCount(obr), MAX_OBX);
                        for (int j = 0; j < inner; j++) {
                            line(out, handler, method, i, j);
                        }
                    }
                }
                case 3 -> {
                    for (int i = 0; i < obrCount; i++) {
                        final int obr = i;
                        int obxCount = count(() -> handler.getOBXCount(obr), MAX_OBX);
                        for (int j = 0; j < obxCount; j++) {
                            final int obx = j;
                            int comments = count(() -> handler.getOBXCommentCount(obr, obx), MAX_COMMENTS);
                            for (int k = 0; k < comments; k++) {
                                line(out, handler, method, i, j, k);
                            }
                        }
                    }
                }
                default -> throw new IllegalStateException("unexpected arity for " + method);
            }
        }
        return out.toString();
    }

    private static List<Method> observableMethods(Class<?> handlerClass) {
        return Arrays.stream(handlerClass.getMethods())
                .filter(m -> m.getDeclaringClass() != Object.class)
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getName().matches("(get|is)[A-Z].*"))
                .filter(m -> !SKIPPED.contains(m.getName()))
                .filter(m -> m.getParameterCount() <= 3)
                .filter(m -> Arrays.stream(m.getParameterTypes()).allMatch(int.class::equals))
                .filter(m -> isRenderable(m.getReturnType()))
                .sorted(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount))
                .toList();
    }

    private static boolean isRenderable(Class<?> type) {
        return type.isPrimitive() && type != void.class
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || Date.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || type == String[].class;
    }

    private static int count(Callable<Integer> counter, int max) {
        try {
            Integer value = counter.call();
            return value == null ? 0 : Math.max(0, Math.min(value, max));
        } catch (Exception e) {
            return 0;
        }
    }

    private static void line(StringBuilder out, MessageHandler handler, Method method, Object... args) {
        out.append(method.getName()).append('(')
                .append(Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(",")))
                .append(") = ");
        try {
            out.append(value(method.invoke(handler, args)));
        } catch (InvocationTargetException e) {
            out.append("threw ").append(describe(e.getCause()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            out.append("threw ").append(describe(e));
        }
        out.append('\n');
    }

    private static String describe(Throwable t) {
        Throwable current = t;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "null" : current.getClass().getName();
    }

    static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence text) {
            return quote(text.toString());
        }
        if (value instanceof Date date) {
            // Same calendar and zone the handlers parse with, so pre-1582 and LMT dates round-trip.
            return "Date(" + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(date) + ")";
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(LabHandlerSnapshot::value).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof String[] items) {
            return Arrays.stream(items).map(LabHandlerSnapshot::value).collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static String quote(String text) {
        if (text.length() > MAX_INLINE_CHARS) {
            return "sha256:" + sha256(text) + " (" + text.length() + " chars)";
        }
        StringBuilder out = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
