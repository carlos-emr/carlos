/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialists;
import io.github.carlos_emr.carlos.commn.model.ServiceSpecialistsPK;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("fast")
class ProfessionalSpecialistsManagerUnitTest {
    @Mock private ProfessionalSpecialistDao professionalSpecialistDao;
    @Mock private ConsultationServiceDao consultationServiceDao;
    @Mock private ServiceSpecialistsDao serviceSpecialistsDao;
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;
    @InjectMocks private ProfessionalSpecialistsManager manager;

    private ProfessionalSpecialist specialist() {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setSpecialtyType("7");
        return specialist;
    }

    private void authorize() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    private void activeService() {
        ConsultationServices service = new ConsultationServices();
        service.setActive(ConsultationServiceDao.ACTIVE);
        when(consultationServiceDao.find(Integer.valueOf(7))).thenReturn(service);
    }

    @Test
    void shouldPersistServiceAssignment_whenCreatingSpecialistWithSpecialty() {
        authorize();
        activeService();
        ProfessionalSpecialist specialist = specialist();
        doAnswer(invocation -> {
            ReflectionTestUtils.setField(specialist, "id", 123);
            return null;
        }).when(professionalSpecialistDao).persist(specialist);
        manager.saveProfessionalSpecialist(loggedInInfo, specialist);
        ArgumentCaptor<ServiceSpecialists> assignment = ArgumentCaptor.forClass(ServiceSpecialists.class);
        verify(serviceSpecialistsDao).persist(assignment.capture());
        assertThat(assignment.getValue().getId()).isEqualTo(new ServiceSpecialistsPK(7, 123));
        verify(professionalSpecialistDao, never()).merge(any());
    }

    @Test
    void shouldKeepExistingAssignments_whenUpdatingSpecialist() {
        authorize();
        activeService();
        ProfessionalSpecialist specialist = specialist();
        ReflectionTestUtils.setField(specialist, "id", 123);
        when(serviceSpecialistsDao.find(new ServiceSpecialistsPK(7, 123))).thenReturn(new ServiceSpecialists());
        manager.saveProfessionalSpecialist(loggedInInfo, specialist);
        verify(professionalSpecialistDao).merge(specialist);
        verify(serviceSpecialistsDao).find(new ServiceSpecialistsPK(7, 123));
        verifyNoMoreInteractions(serviceSpecialistsDao);
    }

    @Test
    void shouldRejectSaveBeforeWriting_whenServiceIsInactive() {
        authorize();
        ConsultationServices service = new ConsultationServices();
        service.setActive(ConsultationServiceDao.INACTIVE);
        when(consultationServiceDao.find(Integer.valueOf(7))).thenReturn(service);
        assertThatThrownBy(() -> manager.saveProfessionalSpecialist(loggedInInfo, specialist()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(professionalSpecialistDao, serviceSpecialistsDao);
    }

    @Test
    void shouldRejectSaveBeforeWriting_whenServiceDoesNotExist() {
        authorize();
        assertThatThrownBy(() -> manager.saveProfessionalSpecialist(loggedInInfo, specialist()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(professionalSpecialistDao, serviceSpecialistsDao);
    }

    @Test
    void shouldRejectSaveBeforeLookup_whenUserCannotWriteConsultations() {
        assertThatThrownBy(() -> manager.saveProfessionalSpecialist(loggedInInfo, specialist()))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(professionalSpecialistDao, consultationServiceDao, serviceSpecialistsDao);
    }

    @Test
    void shouldAllowUnassignedSpecialist_whenNoSpecialtySelected() {
        authorize();
        ProfessionalSpecialist specialist = specialist();
        specialist.setSpecialtyType("0");
        manager.saveProfessionalSpecialist(loggedInInfo, specialist);
        verify(professionalSpecialistDao).persist(specialist);
        verifyNoInteractions(consultationServiceDao, serviceSpecialistsDao);
    }
}
