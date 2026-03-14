package io.github.carlos_emr.carlos.test.mocks;

import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;

/**
 * Spring FactoryBean that creates Mockito mocks for any interface or class.
 * Used in test XML context to create mock beans without constructor issues.
 *
 * @param <T> the type of mock to create
 */
public class MockBeanFactory<T> implements FactoryBean<T> {

    private Class<T> mockType;

    public void setMockType(Class<T> mockType) {
        this.mockType = mockType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        return Mockito.mock(mockType);
    }

    @Override
    public Class<T> getObjectType() {
        return mockType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
