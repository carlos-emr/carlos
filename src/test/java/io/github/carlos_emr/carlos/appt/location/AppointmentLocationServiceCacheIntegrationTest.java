/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.config.CacheConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The Location List service through a Spring proxy, as the application runs it (#3982): every
 * change runs in its own transaction and evicts the lookup-list cache once it commits. The lookup
 * list DAO evicts nothing when items change, and {@code findByName} is cached, so without the
 * eviction the booking dropdown would keep offering the old list.
 *
 * <p>The test runs outside a transaction so each change commits, and removes what it wrote.</p>
 */
@DisplayName("AppointmentLocationService transactions and cache eviction")
@Tag("integration")
@Tag("cache")
@Tag("appointment")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentLocationServiceCacheIntegrationTest extends CarlosTestBase {

    @Autowired
    private LookupListDao lookupListDao;
    @Autowired
    private LookupListItemDao lookupListItemDao;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ApplicationContext applicationContext;

    private TransactionTemplate transactionTemplate;
    private AppointmentLocationService service;
    private LoggedInInfo loggedInInfo;
    private Integer listId;

    /** Writes no audit row: the log table is not under test here. */
    static class QuietService extends AppointmentLocationService {
        QuietService(LookupListDao lookupListDao, LookupListItemDao lookupListItemDao,
                     SecurityInfoManager securityInfoManager, AppointmentLocationResolver resolver) {
            super(lookupListDao, lookupListItemDao, securityInfoManager, resolver);
        }

        @Override
        protected void audit(LoggedInInfo who, String operation, String data) {
            // The log table is outside this transaction/cache contract; unit tests cover auditing.
        }
    }

    @BeforeEach
    void setUpProxiedService() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        lookupListCache().clear();

        LookupList list = new LookupList();
        list.setName(AppointmentLocationResolver.LIST_NAME);
        list.setListTitle("Appointment Locations");
        list.setActive(true);
        list.setCreatedBy("oscar");
        list.setDateCreated(new Date());
        transactionTemplate.executeWithoutResult(status -> lookupListDao.persist(list));
        listId = list.getId();

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        AppointmentLocationService target = new QuietService(lookupListDao, lookupListItemDao, securityInfoManager,
                new AppointmentLocationResolver(lookupListDao, () -> false));
        // The context's post-processors wrap it exactly as they wrap the application's @Service bean.
        service = (AppointmentLocationService) applicationContext.getAutowireCapableBeanFactory()
                .initializeBean(target, "appointmentLocationServiceUnderTest");
    }

    @AfterEach
    void removeCommittedRows() {
        transactionTemplate.executeWithoutResult(status -> {
            List<LookupListItem> items = new ArrayList<>(lookupListItemDao.findByLookupListId(listId, true));
            items.addAll(lookupListItemDao.findByLookupListId(listId, false));
            items.forEach(lookupListItemDao::remove);
            LookupList list = lookupListDao.find(listId);
            if (list != null) {
                lookupListDao.remove(list);
            }
        });
        lookupListCache().clear();
    }

    @Test
    void shouldEvictLookupListCache_whenAddCommits() {
        assertThat(AopUtils.isAopProxy(service)).as("the service must be proxied for @Transactional and @CacheEvict").isTrue();
        transactionTemplate.executeWithoutResult(status ->
                assertThat(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).isNotNull());
        assertThat(cacheEntryCount()).isPositive();

        // Called outside any transaction: the list row lock it takes needs the service's own.
        service.addLocation(loggedInInfo, "Room 1");

        assertThat(cacheEntryCount()).as("adding a location must evict the lookup-list cache after commit").isZero();
        assertThat(service.load(loggedInInfo).getActiveItems()).extracting(LookupListItem::getLabel)
                .containsExactly("Room 1");
    }

    @Test
    void shouldOfferRenamedLocation_whenRenameCommits() {
        service.addLocation(loggedInInfo, "Room 1");
        Integer id = service.load(loggedInInfo).getActiveItems().get(0).getId();
        assertThat(cacheEntryCount()).isPositive();

        service.rename(loggedInInfo, id, "Room 2");

        assertThat(cacheEntryCount()).isZero();
        assertThat(service.load(loggedInInfo).getActiveItems()).extracting(LookupListItem::getLabel)
                .containsExactly("Room 2");
    }

    private org.springframework.cache.Cache lookupListCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CacheConfig.LOOKUP_LISTS);
        assertThat(cache).as("cache %s should be present in the test context", CacheConfig.LOOKUP_LISTS).isNotNull();
        return cache;
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount() {
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) lookupListCache().getNativeCache();
        nativeCache.cleanUp();
        return nativeCache.estimatedSize();
    }
}
