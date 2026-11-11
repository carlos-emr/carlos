// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded rows and total for the simple, DAO-owned table queries used by server pagination. */
public record QueryPage<T>(List<T> rows, long total, int page, int size) {
    public List<T> getRows() { return rows; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    // Jakarta's RecordELResolver calls property(), including computed properties.
    public long end() { return (long) page * size + rows.size(); }
    public int pageCount() { return (int) Math.max(1, (total + size - 1) / size); }
    public boolean previous() { return page > 0; }
    public boolean next() { return page + 1 < pageCount(); }
    public long getEnd() { return end(); }
    public int getPageCount() { return pageCount(); }
    public boolean isPrevious() { return previous(); }
    public boolean isNext() { return next(); }

    /**
     * The supplied query must be a DAO-owned single-root SELECT with no outer ORDER BY.
     * The order is an application allowlist expression, never a request value. Count and data
     * share the exact predicates and bindings. Other unpaged DAO methods keep their contracts.
     */
    @SuppressWarnings("unchecked")
    public static <T> QueryPage<T> load(EntityManager em, Query source, String text, int requestedPage,
            int pageSize, String order, boolean nativeSql, Class<T> type) {
        if (pageSize <= 0 || requestedPage < 0) throw new IllegalArgumentException("Invalid page");
        var from = Pattern.compile("(?i)\\bFROM\\b").matcher(text);
        if (!from.find()) throw new IllegalArgumentException("Page query has no root");
        String countText = "SELECT COUNT(*) " + text.substring(from.start());
        Query count = nativeSql ? em.createNativeQuery(countText) : em.createQuery(countText);
        Query data = nativeSql ? em.createNativeQuery(text + " ORDER BY " + order, type)
                : em.createQuery(text + " ORDER BY " + order, type);
        for (var parameter : source.getParameters()) {
            Object value = source.getParameterValue(parameter);
            if (parameter.getName() != null) {
                count.setParameter(parameter.getName(), value);
                data.setParameter(parameter.getName(), value);
            } else {
                count.setParameter(parameter.getPosition(), value);
                data.setParameter(parameter.getPosition(), value);
            }
        }
        long total = ((Number) count.getSingleResult()).longValue();
        int last = Math.toIntExact(Math.max(0, (total - 1) / pageSize));
        int page = Math.min(requestedPage, last);
        data.setFirstResult(Math.multiplyExact(page, pageSize));
        data.setMaxResults(pageSize);
        return new QueryPage<>(List.copyOf(data.getResultList()), total, page, pageSize);
    }
}
