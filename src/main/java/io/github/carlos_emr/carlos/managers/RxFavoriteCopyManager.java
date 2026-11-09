/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.FavoritesDao;
import io.github.carlos_emr.carlos.commn.dao.FavoritesPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Favorites;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Copies a complete selection atomically; new templates begin Ungrouped in the destination. */
@Service
public class RxFavoriteCopyManager {
    private final FavoritesDao favorites;
    private final FavoritesPrivilegeDao privileges;
    public RxFavoriteCopyManager(FavoritesDao favorites, FavoritesPrivilegeDao privileges) {
        this.favorites = favorites; this.privileges = privileges;
    }

    @Transactional(rollbackFor = Exception.class)
    public void copy(String source, String destination, List<Favorites> selected) {
        if (source == null || source.isBlank() || destination == null || destination.isBlank()
                || selected == null || selected.isEmpty()) throw new IllegalArgumentException("Invalid favourite copy");
        if (!source.equals(destination)) {
            var share = privileges.findByProviderNo(source);
            if (share == null || !share.isOpenToPublic()) throw new SecurityException("missing required sec object (_rx)");
        }
        for (Favorites favorite : selected) {
            if (favorite == null || !source.equals(favorite.getProviderNo())) throw new SecurityException("missing required sec object (_rx)");
        }
        for (Favorites favorite : selected) {
            Favorites copy = new Favorites();
            BeanUtils.copyProperties(favorite, copy);
            copy.setProviderNo(destination);
            copy.setId(null);
            favorites.persist(copy);
        }
    }
}
