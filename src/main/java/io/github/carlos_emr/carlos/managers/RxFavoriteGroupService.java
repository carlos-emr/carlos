/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provider-owned presentation groups; memberships never change the medication template itself. */
@Service
@Transactional
public class RxFavoriteGroupService {
    private final JdbcTemplate jdbc;
    public RxFavoriteGroupService(DataSource dataSource) { jdbc = new JdbcTemplate(dataSource); }

    public static final class GroupLimitException extends IllegalArgumentException {
        public GroupLimitException() { super("Too many favourite groups"); }
    }

    public record Group(int id, String name, int position) { }
    public record Favorite(int id, String name, List<Integer> groupIds) { }
    public record View(List<Group> groups, List<Favorite> favorites) { }

    @Transactional(readOnly = true)
    public View view(String provider) {
        requireProvider(provider);
        List<Group> groups = groups(provider, false);
        Map<Integer, List<Integer>> membership = new LinkedHashMap<>();
        jdbc.query("SELECT m.favorite_id,m.group_id FROM rx_favorite_group_member m "
                + "JOIN rx_favorite_group g ON g.id=m.group_id "
                + "JOIN favorites f ON f.favoriteid=m.favorite_id "
                + "WHERE g.provider_no=? AND f.provider_no=? ORDER BY g.position,g.id",
                rs -> { membership.computeIfAbsent(rs.getInt(1), ignored -> new ArrayList<>()).add(rs.getInt(2)); },
                provider, provider);
        List<Favorite> favorites = jdbc.query("SELECT favoriteid,favoritename FROM favorites "
                + "WHERE provider_no=? ORDER BY favoritename,favoriteid",
                (rs, row) -> new Favorite(rs.getInt(1), rs.getString(2),
                        List.copyOf(membership.getOrDefault(rs.getInt(1), List.of()))), provider);
        return new View(List.copyOf(groups), List.copyOf(favorites));
    }

    public void create(String provider, String name) {
        requireProvider(provider);
        // Serialize group creation for this provider, including creation of the first group.
        lockProvider(provider);
        List<Group> groups = groups(provider, true);
        if (groups.size() >= 100) throw new GroupLimitException();
        int position = groups.stream().mapToInt(Group::position).max().orElse(-1) + 1;
        jdbc.update("INSERT INTO rx_favorite_group(provider_no,name,position,lastUpdateUser) VALUES (?,?,?,?)",
                provider, requireName(name), position, provider);
    }

    public void rename(String provider, int group, String name) {
        lockProvider(provider);
        ownedGroup(provider, group);
        jdbc.update("UPDATE rx_favorite_group SET name=?,lastUpdateUser=?,lastUpdateDate=? WHERE id=? AND provider_no=?",
                requireName(name), provider, new java.sql.Timestamp(System.currentTimeMillis()), group, provider);
    }

    public void delete(String provider, int group) {
        lockProvider(provider);
        ownedGroup(provider, group);
        // The foreign key removes memberships, never favourites or prescriptions.
        jdbc.update("DELETE FROM rx_favorite_group WHERE id=? AND provider_no=?", group, provider);
    }

    public void move(String provider, int id, int direction) {
        requireProvider(provider);
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid group direction");
        lockProvider(provider);
        List<Group> groups = groups(provider, true);
        int index = -1;
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).id() == id) index = i;
        if (index < 0) throw new NoSuchElementException();
        int target = index + direction;
        if (target < 0 || target >= groups.size()) return;
        // Renumber the complete owned ordering so duplicate historical positions cannot make a move disappear.
        var reordered = new ArrayList<>(groups);
        java.util.Collections.swap(reordered, index, target);
        for (int i = 0; i < reordered.size(); i++) {
            jdbc.update("UPDATE rx_favorite_group SET position=?,lastUpdateUser=?,lastUpdateDate=? WHERE id=? AND provider_no=?",
                    i, provider, new java.sql.Timestamp(System.currentTimeMillis()), reordered.get(i).id(), provider);
        }
    }

    public void assign(String provider, int favorite, List<Integer> groupIds) {
        requireProvider(provider);
        if (groupIds == null || groupIds.size() > 100) throw new IllegalArgumentException("Invalid group selection");
        var unique = new TreeSet<Integer>();
        for (Integer id : groupIds) {
            if (id == null || id <= 0) throw new IllegalArgumentException("Invalid group");
            unique.add(id);
        }
        lockProvider(provider);
        // All ownership checks precede deletion. Sorted group locks avoid opposing assignment orders.
        for (Integer id : unique) {
            ownedGroup(provider, id);
        }
        List<Integer> favorites = jdbc.queryForList("SELECT favoriteid FROM favorites "
                + "WHERE favoriteid=? AND provider_no=? FOR UPDATE", Integer.class, favorite, provider);
        if (favorites.isEmpty()) throw new NoSuchElementException();
        var existing = new TreeSet<>(jdbc.queryForList(
                "SELECT group_id FROM rx_favorite_group_member WHERE favorite_id=?", Integer.class, favorite));
        for (Integer group : existing) {
            if (!unique.contains(group)) jdbc.update(
                    "DELETE FROM rx_favorite_group_member WHERE group_id=? AND favorite_id=?", group, favorite);
        }
        for (Integer group : unique) {
            if (!existing.contains(group)) jdbc.update(
                    "INSERT INTO rx_favorite_group_member(group_id,favorite_id,lastUpdateUser) VALUES (?,?,?)",
                    group, favorite, provider);
        }
    }

    private void ownedGroup(String provider, int group) {
        requireProvider(provider);
        if (group <= 0) throw new IllegalArgumentException("Invalid group");
        if (jdbc.queryForList("SELECT id FROM rx_favorite_group WHERE id=? AND provider_no=? FOR UPDATE",
                Integer.class, group, provider).isEmpty()) throw new NoSuchElementException();
    }

    private List<Group> groups(String provider, boolean lock) {
        return jdbc.query("SELECT id,name,position FROM rx_favorite_group WHERE provider_no=? "
                + "ORDER BY position,id" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Group(rs.getInt(1), rs.getString(2), rs.getInt(3)), provider);
    }

    private void lockProvider(String provider) {
        if (jdbc.queryForList("SELECT provider_no FROM provider WHERE provider_no=? FOR UPDATE",
                String.class, provider).isEmpty()) throw new NoSuchElementException();
    }

    private static void requireProvider(String provider) {
        if (provider == null || provider.isBlank() || provider.length() > 6) {
            throw new IllegalArgumentException("Invalid provider");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.strip().codePointCount(0, name.strip().length()) > 80) {
            throw new IllegalArgumentException("Invalid group name");
        }
        return name.strip();
    }
}
