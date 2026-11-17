// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.annotation;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("document")
class AnnotatedDocumentAuthorizationUnitTest {
    @Test
    void shouldDenyBeforeCompositionOrFiling_whenASecondLinkedPatientIsRestricted() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo info = mock(LoggedInInfo.class);
        AnnotatedDocumentComposer composer = mock(AnnotatedDocumentComposer.class);
        CtlDocumentDao links = mock(CtlDocumentDao.class);
        CtlDocument first = new CtlDocument();
        first.setId(new CtlDocumentPK("demographic", 10, 42));
        CtlDocument second = new CtlDocument();
        second.setId(new CtlDocumentPK("demographic", 20, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(first, second));
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 10)).thenReturn(true);
        try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class);
             MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            beans.when(() -> SpringUtils.getBean(CtlDocumentDao.class)).thenReturn(links);
            AnnotatedDocumentService service = new AnnotatedDocumentService(security, composer, "/unused");
            assertThatThrownBy(() -> service.save(info, 42, List.of(), "unused"))
                    .isInstanceOf(SecurityException.class);
            documents.verifyNoInteractions();
            verifyNoInteractions(composer);
        }
    }
}
