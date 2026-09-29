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

/**
 * Single enforcement point for "this Rx favorite belongs to the provider driving this request".
 *
 * <p>Rx favorites are a per-provider list: every screen that renders them
 * ({@code SideLinks*Favorites*.jsp}, {@code EditFavorites2.jsp}) reads them through
 * {@link RxPrescriptionData#getFavorites(String)} keyed on the session provider. The mutating and
 * consuming actions, however, receive a bare {@code favoriteId} request parameter, so without an
 * explicit owner check a provider could delete, rename, or prescribe from another provider's
 * favorite by guessing an id.
 *
 * <p>The check lives here rather than being repeated per action so the session attribute holding
 * the provider number and the rejection status stay in one place; drift between copies of this
 * logic would be an authorization hole, not a style problem.
 *
 * @since 2026-09-14
 */
final class RxFavoriteOwnership {

    /**
     * Session attribute holding the logged-in provider number. This is the same key
     * {@code RxChoosePatient2Action} uses to seed {@code RxSessionBean.providerNo}, which is what
     * the favorites list screens filter on, so ownership here matches what the user can see.
     */
    private static final String SESSION_PROVIDER_ATTRIBUTE = "user";

    private RxFavoriteOwnership() {
        // static enforcement point; not instantiable
    }

    /**
     * Returns the logged-in provider number, or {@code null} when the session carries none.
     *
     * @param request current request, used only for its session
     * @return the session provider number, possibly {@code null}
     */
    static String sessionProviderNo(HttpServletRequest request) {
        return (String) request.getSession().getAttribute(SESSION_PROVIDER_ATTRIBUTE);
    }

    /**
     * Loads a favorite only when it belongs to the provider in session.
     *
     * <p>On failure the response is committed with {@code 403 Forbidden} and {@code null} is
     * returned; callers must stop processing and return {@code NONE} so Struts does not resolve a
     * result on top of the error response. Missing and non-owned favorites are deliberately
     * indistinguishable to the caller so the endpoint cannot be used to probe for valid ids.
     *
     * @param request    current request, used to read the session provider
     * @param response   current response, used to send the rejection
     * @param favoriteId favorite id supplied by the client
     * @return the owned favorite, or {@code null} when the request was rejected
     * @throws IOException if the error response cannot be written
     */
    static RxPrescriptionData.Favorite requireOwnedFavorite(HttpServletRequest request,
                                                            HttpServletResponse response,
                                                            int favoriteId) throws IOException {
        RxPrescriptionData.Favorite favorite =
                new RxPrescriptionData().getFavorite(favoriteId, sessionProviderNo(request));
        if (favorite == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        return favorite;
    }
}
