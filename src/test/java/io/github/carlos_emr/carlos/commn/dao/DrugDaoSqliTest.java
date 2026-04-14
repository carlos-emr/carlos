package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrugDao SQL Injection Tests")
public class DrugDaoSqliTest extends CarlosTestBase {

    @Autowired
    private DrugDao drugDao;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("findByParameter should prevent SQL injection")
    void testFindByParameterSqlInjection() {
        Drug drug = new Drug();
        drug.setDemographicId(123);
        drug.setBrandName("SafeDrug");
        drug.setProviderNo("123");
        drug.setRegionalIdentifier("12345");
        drug.setSpecial("special test");
        drug.setSpecialInstruction("special instruction test");
        drugDao.addNewDrug(drug);
        entityManager.flush();

        // Safe parameter
        List<Object[]> safeResult = drugDao.findByParameter("BN", "SafeDrug");
        assertThat(safeResult).isNotEmpty();

        // Value SQL Injection
        List<Object[]> sqliResult = drugDao.findByParameter("BN", "SafeDrug' OR '1'='1");
        assertThat(sqliResult).isEmpty(); // It should treat "' OR '1'='1" as a literal string value
    }
}
