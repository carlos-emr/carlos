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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Request checks shared by the Rx favourite writes (edit, AJAX edit, delete).
 *
 * <p>A favourite belongs to one provider. Its id comes from the request, so every write loads the
 * favourite and refuses it unless the logged-in provider owns it; ids are small sequential
 * integers, so without the check a caller could edit or delete another provider's favourites
 * (#3908). The writes are also POST-only, and a malformed id or a missing favourite is a 400 or
 * 404 rather than a 500.</p>
 *
 * @since 2026-09-24
 */
final class RxFavoriteAccess {

    private RxFavoriteAccess() {
    }

    /**
     * Answers a non-POST request with 405 and {@code Allow: POST}.
     *
     * @return {@code true} when the request was refused
     * @throws IOException when the error cannot be sent
     */
    static boolean refuseUnlessPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("POST".equals(request.getMethod())) {
            return false;
        }
        response.setHeader("Allow", "POST");
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return true;
    }

    /**
     * The favourite {@code rawId} names, when the logged-in provider owns it; otherwise answers
     * 400 (malformed id), 404 (no such favourite) or 403 (another provider's) and returns
     * {@code null}.
     *
     * @param request  the current request
     * @param response the response the refusal is written to
     * @param rawId    the request's favourite id
     * @return the owned favourite, or {@code null} after an error response
     * @throws IOException when the error cannot be sent
     */
    static RxPrescriptionData.Favorite loadOwned(HttpServletRequest request, HttpServletResponse response,
                                                 String rawId) throws IOException {
        if (rawId == null || !rawId.trim().matches("\\d{1,9}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        RxPrescriptionData.Favorite favorite = new RxPrescriptionData().getFavorite(Integer.parseInt(rawId.trim()));
        if (favorite == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String caller = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        if (caller == null || !caller.equals(favorite.getProviderNo())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }
        return favorite;
    }

    /**
     * Parses a favourite's repeat count; {@code null} when it is missing or malformed.
     *
     * @param raw the request value
     * @return the count, or {@code null}
     */
    static Integer parseRepeat(String raw) {
        if (raw == null || !raw.trim().matches("\\d{1,4}")) {
            return null;
        }
        return Integer.valueOf(raw.trim());
    }
}
