/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCourseCopyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, bounded synthetic course replication; never a fallback into real-chart import. */
@Service
public class CdsCourseService {
    private final CdsImportConfigurationService configuration;
    private final TransactionTemplate read;
    private final CdsProgramContextService programs;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    public CdsCourseService(CdsImportConfigurationService configuration, PlatformTransactionManager manager, CdsProgramContextService programs) {
        this.configuration = configuration;
        this.programs = programs;
        read = new TransactionTemplate(manager); read.setReadOnly(true); read.setTimeout(30);
    }

    public void forEachCopy(CdsImportActorDto actor, CdsImportCommand command, Consumer<CdsCourseCopyDto> copies) {
        read.execute(status -> { requireEducator(actor, command); return null; });
        String after = "";
        long count = 0;
        while (true) {
            String cursor = after;
            var page = read.execute(status -> entityManager.createQuery("select distinct pp.providerNo from ProgramProvider pp "
                    + "join pp.role role join pp.provider provider where pp.programId=:course "
                    + "and lower(role.roleName)='student' and provider.status='1' and pp.providerNo>:after order by pp.providerNo", String.class)
                    .setParameter("course", command.courseId().longValue()).setParameter("after", cursor)
                    .setMaxResults(100).getResultList());
            if (page.isEmpty()) break;
            var selected = read.execute(status -> {
                var names = page.stream().map(student -> "program" + student).toList();
                var programs = entityManager.createQuery("select p.name,p.id,p.facilityId from Program p "
                        + "where p.name in :names and p.programStatus='active'", Object[].class)
                        .setParameter("names", names).setMaxResults(201).getResultList();
                if (programs.size() > 200) throw new CdsImportException("STUDENT_PERSONAL_PROGRAM_REQUIRES_REVIEW");
                var result = new java.util.ArrayList<CdsCourseCopyDto>(page.size());
                for (String student : page) {
                    var matches = programs.stream().filter(p -> ("program" + student).equals(p[0])).toList();
                    if (matches.size() != 1) throw new CdsImportException("STUDENT_PERSONAL_PROGRAM_REQUIRES_REVIEW");
                    Integer program = (Integer) matches.get(0)[1], facility = (Integer) matches.get(0)[2];
                    if (command.facilityId() != null && !Objects.equals(command.facilityId(), facility)) {
                        throw new CdsImportException("STUDENT_PROGRAM_FACILITY_CONFLICT");
                    }
                    result.add(new CdsCourseCopyDto("course-" + command.courseId() + "-student-" + student + "-program-" + program,
                            student, program, facility));
                }
                return result;
            });
            // Planning callbacks perform separate administrative transactions, outside this read page.
            for (CdsCourseCopyDto copy : selected) { copies.accept(copy); count++; }
            after = page.get(page.size() - 1);
        }
        if (count == 0) throw new CdsImportException("COURSE_HAS_NO_ELIGIBLE_STUDENTS");
    }

    @Transactional(readOnly = true)
    public void requireCopy(CdsImportActorDto actor, CdsImportCommand command) {
        requireEducator(actor, command);
        if (command.programId() == null || command.programId().equals(command.courseId())) return;
        var program = programs.metadata(command.programId());
        if (program == null || program.name() == null || !program.name().startsWith("program")) deny();
        String student = program.name().substring("program".length());
        Long enrolled = entityManager.createQuery("select count(pp.id) from ProgramProvider pp join pp.role r "
                + "join pp.provider p where pp.programId=:course and pp.providerNo=:student and lower(r.roleName)='student' and p.status='1'", Long.class)
                .setParameter("course", command.courseId().longValue()).setParameter("student", student).getSingleResult();
        if (enrolled == 0 || !"active".equalsIgnoreCase(program.status())
                || (command.facilityId() != null && !Objects.equals(command.facilityId(), program.facilityId()))) deny();
    }

    private void requireEducator(CdsImportActorDto actor, CdsImportCommand command) {
        if (!command.synthetic() || command.courseId() == null || command.mergeExisting()
                || !"yes".equalsIgnoreCase(configuration.value("OSCAR_LEARNING", "no"))
                || !Boolean.parseBoolean(configuration.value("cds.import.source." + command.sourceNamespace() + ".synthetic", "false"))) deny();
        var course = programs.metadata(command.courseId());
        if (course == null || !"active".equalsIgnoreCase(course.status())) deny();
        String authorized = configuration.value("cds.import.course." + command.courseId() + ".educators", "");
        if (authorized.length() > 8192 || Arrays.stream(authorized.split(",")).map(String::trim)
                .noneMatch(actor.providerNo()::equals)) deny();
        Long membership = entityManager.createQuery("select count(pp.id) from ProgramProvider pp join pp.role r "
                + "where pp.programId=:course and pp.providerNo=:educator and lower(r.roleName)<>'student'", Long.class)
                .setParameter("course", command.courseId().longValue()).setParameter("educator", actor.providerNo()).getSingleResult();
        if (membership == 0) deny();
    }

    private static void deny() { throw new SecurityException("Synthetic course import delegation denied"); }
}
