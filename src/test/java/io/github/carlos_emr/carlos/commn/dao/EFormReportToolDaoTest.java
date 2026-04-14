package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EFormReportTool;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EFormReportToolDaoTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private EFormReportToolDaoImpl eFormReportToolDao;

    @Test
    public void testPopulateReportTableItem() {
        EFormReportTool eft = new EFormReportTool();
        eft.setTableName("test_table");

        EFormValue value1 = new EFormValue();
        value1.setVarName("field1");
        value1.setVarValue("value1");

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        eFormReportToolDao.populateReportTableItem(eft, Arrays.asList(value1), 1, 123, new Date(), "123456");

        verify(entityManager).createNativeQuery(argThat(sql -> {
            System.out.println("SQL: " + sql);
            return true;
        }));
        verify(query).executeUpdate();
    }
}
