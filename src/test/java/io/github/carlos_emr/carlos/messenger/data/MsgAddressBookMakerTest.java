/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.messenger.data;

import io.github.carlos_emr.carlos.commn.dao.GroupMembersDao;
import io.github.carlos_emr.carlos.commn.dao.GroupsDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class MsgAddressBookMakerTest {
    private static Provider provider(String number, String status) {
        var provider = new Provider();
        provider.setProviderNo(number);
        provider.setStatus(status);
        provider.setFirstName("Test");
        provider.setLastName("Member");
        return provider;
    }

    @Test
    void shouldPublishEachActiveRecipientOnce_despiteLegacyMembershipRows() throws Exception {
        var members = mock(GroupMembersDao.class);
        var groups = mock(GroupsDao.class);
        var locations = mock(OscarCommLocationsDao.class);
        var location = new OscarCommLocations();
        when(locations.findByCurrent1(1)).thenReturn(List.of(location));
        Provider active = provider("101", "1");
        when(members.findMembersByGroupId(0)).thenReturn(List.of(
                new Object[] {null, active}, new Object[] {null, active},
                new Object[] {null, provider("-101", "1")},
                new Object[] {null, provider("202", "0")}));
        try (var beans = mockStatic(SpringUtils.class)) {
            beans.when(() -> SpringUtils.getBean(GroupMembersDao.class)).thenReturn(members);
            beans.when(() -> SpringUtils.getBean(GroupsDao.class)).thenReturn(groups);
            beans.when(() -> SpringUtils.getBean(OscarCommLocationsDao.class)).thenReturn(locations);
            assertThat(new MsgAddressBookMaker().updateAddressBook()).isTrue();
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(location.getAddressBook())));
        var addresses = document.getElementsByTagName("address");
        assertThat(addresses.getLength()).isEqualTo(1);
        assertThat(addresses.item(0).getAttributes().getNamedItem("id").getNodeValue()).isEqualTo("101");
        verify(locations).merge(location);
    }
}
