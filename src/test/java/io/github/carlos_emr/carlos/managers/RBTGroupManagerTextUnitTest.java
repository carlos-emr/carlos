/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.RBTGroupDao;
import io.github.carlos_emr.carlos.commn.model.RBTGroup;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
class RBTGroupManagerTextUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"O'Brien", "Zoë 中文", "literal &amp; \\ path", "\"<b>group</b> #&?"})
    void shouldPersistAndDeleteSameLiteralName_whenAuthorized(String name) {
        RBTGroupDao dao = mock(RBTGroupDao.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(login, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);
        RBTGroupManager manager = manager(dao, security);
        manager.addTemplateToGroup(login, " " + name + " ", 17);
        verify(dao).persist(argThat(model -> model instanceof RBTGroup group && name.equals(group.getGroupName()) && group.getTemplateId() == 17));
        manager.delTemplateGroup(login, name);
        verify(dao).deleteByName(name);
    }

    @Test
    void shouldNotPersist_whenAdminWriteIsDenied() {
        RBTGroupDao dao = mock(RBTGroupDao.class);
        RBTGroupManager manager = manager(dao, mock(SecurityInfoManager.class));
        assertThatThrownBy(() -> manager.addTemplateToGroup(mock(LoggedInInfo.class), "group", 0))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(dao);
    }

    private RBTGroupManager manager(RBTGroupDao dao, SecurityInfoManager security) {
        RBTGroupManager manager = new RBTGroupManager();
        ReflectionTestUtils.setField(manager, "rbtGroupDao", dao);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        return manager;
    }
}
