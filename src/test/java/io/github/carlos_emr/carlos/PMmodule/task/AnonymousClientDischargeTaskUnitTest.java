/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.PMmodule.task;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.DbConnectionFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class AnonymousClientDischargeTaskUnitTest {
    @Test
    void injectedManagerWorksWithoutStaticSpringFactoryAndOnlyDischargesOldAdmissions() {
        AdmissionManager manager = mock(AdmissionManager.class);
        Admission old = admission(25);
        Admission recent = admission(23);
        when(manager.getActiveAnonymousAdmissions()).thenReturn(List.of(old, recent));
        AnonymousClientDischargeTask task = new AnonymousClientDischargeTask();
        task.setAdmissionManager(manager);
        try (var resources = mockStatic(DbConnectionFilter.class)) {
            task.run();
            verify(manager).saveAdmission(old);
            verify(manager, never()).saveAdmission(recent);
            assertThat(old.getAdmissionStatus()).isEqualTo(Admission.STATUS_DISCHARGED);
            assertThat(old.getDischargeNotes()).isEqualTo("Auto-Discharge");
            assertThat(old.getDischargeDate()).isNotNull();
            assertThat(recent.getAdmissionStatus()).isEqualTo(Admission.STATUS_CURRENT);
            resources.verify(DbConnectionFilter::releaseAllThreadDbResources);
        }
    }

    @Test
    void databaseFailureStillReleasesThreadResources() {
        AdmissionManager manager = mock(AdmissionManager.class);
        when(manager.getActiveAnonymousAdmissions()).thenThrow(new IllegalStateException("test unavailable"));
        AnonymousClientDischargeTask task = new AnonymousClientDischargeTask();
        task.setAdmissionManager(manager);
        try (var resources = mockStatic(DbConnectionFilter.class)) {
            task.run();
            verify(manager, never()).saveAdmission(any());
            resources.verify(DbConnectionFilter::releaseAllThreadDbResources);
        }
    }

    @Test
    void caisiConfigInjectsManagerAndSchedulesTaskOnlyOnceWithExistingDelayAndPeriod() {
        var factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).loadBeanDefinitions(new ClassPathResource("applicationContextCaisi.xml"));
        BeanDefinition task = factory.getBeanDefinition("scheduledAnonymousClientDischargeTask");
        assertThat(task.getPropertyValues().get("admissionManager").toString()).contains("admissionManager");
        BeanDefinition scheduler = factory.getBeanDefinition("schedulerCaisi");
        List<?> tasks = (List<?>) scheduler.getPropertyValues().get("scheduledExecutorTasks");
        assertThat(tasks).hasSize(1);
        var holder = (org.springframework.beans.factory.config.BeanDefinitionHolder) tasks.getFirst();
        var properties = holder.getBeanDefinition().getPropertyValues();
        assertThat(properties.get("delay").toString()).contains("6000000");
        assertThat(properties.get("period").toString()).contains("3600000");
        assertThat(properties.get("runnable").toString()).contains("scheduledAnonymousClientDischargeTask");
        long references = java.util.Arrays.stream(factory.getBeanDefinitionNames())
                .map(factory::getBeanDefinition)
                .map(def -> def.getPropertyValues().get("scheduledExecutorTasks"))
                .filter(List.class::isInstance).flatMap(value -> ((List<?>) value).stream())
                .map(org.springframework.beans.factory.config.BeanDefinitionHolder.class::cast)
                .map(def -> def.getBeanDefinition().getPropertyValues().get("runnable"))
                .filter(ref -> ref != null && ref.toString().contains("scheduledAnonymousClientDischargeTask"))
                .count();
        assertThat(references).isEqualTo(1);
    }

    private Admission admission(int hoursOld) {
        Admission admission = new Admission();
        admission.setAdmissionDate(Date.from(Instant.now().minus(hoursOld, ChronoUnit.HOURS)));
        admission.setAdmissionStatus(Admission.STATUS_CURRENT);
        return admission;
    }
}
