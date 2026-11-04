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
package io.github.carlos_emr.carlos.sms.viewmodel;

import io.github.carlos_emr.carlos.sms.SmsProviderType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The page each message list on the SMS queue page is on. Every list of every SMS provider is paged on its
 * own, so moving through one list leaves the others where they are.
 * <p>
 * Each list has its own request parameter, {@code page_<provider>_<list>} (for example
 * {@code page_VOIPMS_failed=2}), and only those names are read: a parameter for an unknown SMS provider or
 * list is never looked at. A page is a plain whole number from 1 up, written without a sign or leading zero
 * and with at most {@value #MAX_PAGE_DIGITS} digits; anything else, or the same parameter sent twice, is
 * page 1. Page 1 is never written into a link. The page actually shown can still be lower than the one asked
 * for: the page is cut back to the list's last page ({@link #lastPage(long)}), for a link followed after the
 * list got shorter.
 * <p>
 * Page numbers rather than row offsets go into links, so a page always starts at a multiple of
 * {@link #PAGE_SIZE} and no hand-made link can shift the rows of a page.
 *
 * @since 2026-10-16
 */
public final class SmsQueuePages {

    /** How many messages one page of a list covers. */
    public static final int PAGE_SIZE = 20;

    /**
     * The most digits a page number may have. With {@link #PAGE_SIZE} rows a page, the first row of the
     * largest page this allows still fits in an {@code int}.
     */
    static final int MAX_PAGE_DIGITS = 7;

    private static final String PARAMETER_PREFIX = "page_";
    private static final Pattern PAGE_NUMBER = Pattern.compile("[1-9][0-9]{0," + (MAX_PAGE_DIGITS - 1) + "}");
    // Before FIRST: the constructor sorts by it, and static fields are set in the order they are written.
    private static final Comparator<Key> PAGE_ORDER =
            Comparator.comparing(Key::provider).thenComparing(Key::list);

    /** Every list on its first page: the page as opened without page parameters. */
    public static final SmsQueuePages FIRST = new SmsQueuePages(Map.of());

    /** The lists not on their first page, in SMS provider order, then list order. */
    private final Map<Key, Integer> pages;

    private SmsQueuePages(Map<Key, Integer> pages) {
        TreeMap<Key, Integer> sorted = new TreeMap<>(PAGE_ORDER);
        sorted.putAll(pages);
        this.pages = Collections.unmodifiableMap(sorted);
    }

    /**
     * @param parameterValues the request's parameter values by name, as
     *                        {@code HttpServletRequest.getParameterValues}; {@code null} for a missing name
     * @return the pages asked for; a list whose parameter is missing or not a valid page is on page 1
     */
    public static SmsQueuePages fromParameters(Function<String, String[]> parameterValues) {
        Objects.requireNonNull(parameterValues, "parameterValues is required");
        Map<Key, Integer> pages = new TreeMap<>(PAGE_ORDER);
        for (SmsProviderType provider : SmsProviderType.values()) {
            for (SmsQueueList list : SmsQueueList.values()) {
                String[] values = parameterValues.apply(parameterName(provider, list));
                if (values != null && values.length == 1 && values[0] != null
                        && PAGE_NUMBER.matcher(values[0]).matches()) {
                    int page = Integer.parseInt(values[0]);
                    if (page > 1) {
                        pages.put(new Key(provider, list), page);
                    }
                }
            }
        }
        return pages.isEmpty() ? FIRST : new SmsQueuePages(pages);
    }

    /** @return the name of the request parameter that holds that list's page */
    public static String parameterName(SmsProviderType provider, SmsQueueList list) {
        return PARAMETER_PREFIX + provider.name() + "_" + list.parameterValue();
    }

    /**
     * @param rows how many messages the list has, hidden ones included
     * @return the number of the list's last page; 1 for an empty list
     */
    public static int lastPage(long rows) {
        // Divided first, so that no count, however large, overflows.
        long pageCount = rows <= 0 ? 1 : rows / PAGE_SIZE + (rows % PAGE_SIZE == 0 ? 0 : 1);
        return (int) Math.min(pageCount, Integer.MAX_VALUE);
    }

    /** @return the page that list is on: 1 or more */
    public int page(SmsProviderType provider, SmsQueueList list) {
        return pages.getOrDefault(new Key(provider, list), 1);
    }

    /** @return these pages with that list on the given page; a page below 2 is the first page */
    public SmsQueuePages with(SmsProviderType provider, SmsQueueList list, int page) {
        Map<Key, Integer> changed = new TreeMap<>(PAGE_ORDER);
        changed.putAll(pages);
        Key key = new Key(Objects.requireNonNull(provider), Objects.requireNonNull(list));
        if (page > 1) {
            changed.put(key, page);
        } else {
            changed.remove(key);
        }
        return changed.isEmpty() ? FIRST : new SmsQueuePages(changed);
    }

    /**
     * @return the pages to keep when another time period is chosen: the lists that cover the chosen period
     *         change and start again on their first page, and the others stay where they are
     */
    public SmsQueuePages forOtherWindow() {
        Map<Key, Integer> kept = new TreeMap<>(PAGE_ORDER);
        pages.forEach((key, page) -> {
            if (!key.list().inWindow()) {
                kept.put(key, page);
            }
        });
        return kept.isEmpty() ? FIRST : new SmsQueuePages(kept);
    }

    /** @return the parameters of the lists not on their first page, in a fixed order */
    public List<SmsQueueViewModel.Parameter> parameters() {
        List<SmsQueueViewModel.Parameter> parameters = new ArrayList<>();
        pages.forEach((key, page) -> parameters.add(new SmsQueueViewModel.Parameter(
                parameterName(key.provider(), key.list()), String.valueOf(page))));
        return List.copyOf(parameters);
    }

    /**
     * @return the query string that opens the page in this time period with these pages, without the
     *         leading {@code ?}. It holds only the period's own parameter value and the names and numbers
     *         built here, never text the browser sent.
     */
    public String query(SmsQueueWindow window) {
        StringJoiner query = new StringJoiner("&");
        query.add("window=" + window.parameterValue());
        for (SmsQueueViewModel.Parameter parameter : parameters()) {
            query.add(parameter.name() + "=" + parameter.value());
        }
        return query.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SmsQueuePages that && pages.equals(that.pages);
    }

    @Override
    public int hashCode() {
        return pages.hashCode();
    }

    @Override
    public String toString() {
        StringJoiner text = new StringJoiner(" ");
        parameters().forEach(parameter -> text.add(parameter.name() + "=" + parameter.value()));
        return text.toString();
    }

    private record Key(SmsProviderType provider, SmsQueueList list) {
    }
}
