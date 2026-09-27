/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.www.tld;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.jsp.tagext.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("unit")
class ProgramExclusiveViewTagUnitTest {
    @Test
    void shouldNotRetainPreviousView_whenTagIsReused() throws Exception {
        ProviderDefaultProgramDao dao = mock(ProviderDefaultProgramDao.class);
        Program program = new Program();
        program.setExclusiveView("appointment");
        when(dao.findProgramsByProvider("1")).thenReturn(List.of(program));
        when(dao.findProgramsByProvider("2")).thenReturn(List.of());
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(ProviderDefaultProgramDao.class)).thenReturn(dao);
            var tag = new programExclusiveViewTag();
            tag.setValue("appointment");
            tag.setProviderNo("1");
            assertThat(tag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
            tag.setProviderNo("2");
            assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
            tag.setValue("no");
            assertThat(tag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
        }
    }

    @Test
    void shouldUseUnrestrictedDefault_whenViewIsAbsentOrEmpty() throws Exception {
        ProviderDefaultProgramDao dao = mock(ProviderDefaultProgramDao.class);
        Program program = new Program();
        when(dao.findProgramsByProvider("1")).thenReturn(List.of(program));
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(ProviderDefaultProgramDao.class)).thenReturn(dao);
            var tag = new programExclusiveViewTag();
            tag.setProviderNo("1");
            tag.setValue("no");
            program.setExclusiveView(null);
            assertThat(tag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
            program.setExclusiveView("");
            assertThat(tag.doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);
        }
    }
}
