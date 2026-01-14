//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.casemgmt.dao;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import ca.openosp.openo.casemgmt.model.ClientImage;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.QueueCache;

/**
 * Data Access Object implementation for managing client (demographic) images.
 * 
 * <p>This DAO handles storage and retrieval of client profile images with built-in
 * caching to optimize performance when dealing with large image data. Images are
 * stored as binary data in the database and cached in memory for frequently accessed
 * images under 1MB in size.</p>
 * 
 * <p><b>Important:</b> Anyone modifying get and set methods should take note of the 
 * dataCache and add/remove items as appropriate to maintain cache consistency.</p>
 * 
 * <p>Cache behavior:</p>
 * <ul>
 *   <li>Cache capacity: 40 entries with 4 concurrent segments</li>
 *   <li>Cache TTL: 1 hour</li>
 *   <li>Only images smaller than 1MB are cached</li>
 *   <li>Cache is invalidated on image updates</li>
 * </ul>
 * 
 * @see ClientImageDAO
 * @see ClientImage
 */
@Repository
public class ClientImageDAOImpl implements ClientImageDAO {

    private static final Logger logger = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     * 
     * @return the current session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * This is a simple cache for image data because the images are excessively
     * large (relatively speaking). The Integer key is the demographic_no.
     */
    private static final QueueCache<Integer, ClientImage> dataCache = new QueueCache<Integer, ClientImage>(4, 40,
            DateUtils.MILLIS_PER_HOUR, null);

    /**
     * Saves or updates a client image in the database.
     * 
     * <p>If an existing image is found for the demographic, it updates the image data,
     * type, and timestamp. Otherwise, it creates a new record. The cache entry for this
     * client is removed to ensure fresh data is retrieved on next access.</p>
     * 
     * @param clientImage the client image to save or update
     */
    @Override
    public void saveClientImage(ClientImage clientImage) {
        ClientImage existing = getClientImage(clientImage.getDemographic_no());
        if (existing != null) {
            existing.setImage_data(clientImage.getImage_data());
            existing.setImage_type(clientImage.getImage_type());
            existing.setUpdate_date(new Date());
        }
        getSession().saveOrUpdate(clientImage);

        // update cache
        dataCache.remove(clientImage.getDemographic_no());
    }

    /**
     * Retrieves the most recent client image for a given demographic ID.
     * 
     * <p>This method first checks the in-memory cache for performance. If not cached,
     * it queries the database for the most recent image (ordered by update_date desc).
     * Images smaller than 1MB are automatically added to the cache.</p>
     * 
     * @param clientId the demographic number to retrieve the image for
     * @return the most recent ClientImage for the demographic, or null if none exists
     */
    @Override
    public ClientImage getClientImage(Integer clientId) {

        // check cache
        ClientImage clientImage = dataCache.get(clientId);
        if (clientImage == null) {
            logger.debug("dataCache miss : clientId=" + clientId);

            // get from database
            Query<ClientImage> query = getSession().createQuery(
                    "from ClientImage i where i.demographic_no=:clientId order by update_date desc", 
                    ClientImage.class);
            query.setParameter("clientId", clientId);
            List<ClientImage> results = query.list();
            
            if (results.size() > 0) {
                clientImage = results.get(0);

                // add to cache if it's less than ... say 1 megs
                if (clientImage.getImage_data() != null && clientImage.getImage_data().length < 1024000) {
                    dataCache.put(clientId, clientImage);
                    logger.debug("entry found in db, adding to dataCache : clientId=" + clientId);
                }
            }
        } else {
            logger.debug("dataCache hit : clientId=" + clientId);
        }

        return (clientImage);
    }
}
