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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportControlDto;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportExecutionControlUnitTest {
    private static final CdsImportControlDto VALID = new CdsImportControlDto(true,true,7,false,true);

    @Test void allFieldAndFileReadsShareOnePoll_andForcedBoundariesAreAlwaysFresh() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        try(var scope=scope(owner,fields,clock,10000,()->{})) {
            for(int index=0;index<100;index++){fields.checkpoint();scope.check(false);}
            verify(owner,times(1)).control("run");
            clock.set(249_999_999);fields.checkpoint();scope.check(false);
            verify(owner,times(1)).control("run");
            clock.incrementAndGet();fields.checkpoint();scope.check(false);
            verify(owner,times(2)).control("run");
            scope.check(true);verify(owner,times(3)).control("run");
            assertThatThrownBy(()->scope.requireBinding("other",7)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->scope.requireBinding("run",8)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void everyBoundedFieldReadChecksOverallBudget_evenBeforeNextSqlPoll() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        var budgets=new AtomicInteger();
        try(var scope=scope(owner,fields,clock,10000,()->{
            budgets.incrementAndGet();if(clock.get()>=100_000_000)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
        })) {
            fields.checkpoint();clock.set(100_000_000);
            assertThatThrownBy(fields::checkpoint).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            verify(owner,times(1)).control("run");assertThat(budgets.get()).isGreaterThanOrEqualTo(4);
        }
    }

    @Test void everyBoundedFieldReadChecksStageBudget_andSqlCannotHideADeadline() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        try(var scope=scope(owner,fields,clock,100,()->{})) {
            clock.set(100_000_000);
            assertThatThrownBy(fields::checkpoint).hasMessage("IMPORT_FILE_VERIFICATION_TIMEOUT");
            verify(owner,times(1)).control("run");
        }
        clock.set(0);
        when(owner.control("run")).thenAnswer(call->{clock.addAndGet(100_000_000);return VALID;});
        assertThatThrownBy(()->scope(owner,fields,clock,100,()->{})).hasMessage("IMPORT_FILE_VERIFICATION_TIMEOUT");
    }

    @Test void failedConstructionPreservesThePreviousScope_andNeverRegistersABrokenOne() throws Exception {
        var fields=new CdsFieldParser();var previous=new AtomicInteger();var owner=owner();
        when(owner.control("run")).thenReturn(null);
        try(var prior=fields.control(previous::incrementAndGet)) {
            assertThatThrownBy(()->scope(owner,fields,new AtomicLong(),10000,()->{})).hasMessage("IMPORT_COORDINATOR_LOST");
            fields.checkpoint();assertThat(previous).hasValue(1);
        }
        fields.checkpoint();assertThat(previous).hasValue(1);
    }

    @Test void missingOrChangedOwnershipAndCancellationCannotBecomeACachedSuccess() throws Exception {
        var values=new CdsImportControlDto[]{new CdsImportControlDto(false,true,7,false,true),
            new CdsImportControlDto(true,false,-1,false,false),new CdsImportControlDto(true,true,8,false,true),
            new CdsImportControlDto(true,true,7,false,false),new CdsImportControlDto(true,true,7,true,true)};
        var codes=new String[]{"IMPORT_COORDINATOR_LOST","OWNER_FENCED","OWNER_FENCED","OWNER_FENCED","IMPORT_CANCELLED"};
        for(int index=0;index<values.length;index++) {
            var owner=owner();var clock=new AtomicLong();var fields=new CdsFieldParser();
            try(var scope=scope(owner,fields,clock,10000,()->{})) {
                when(owner.control("run")).thenReturn(values[index]);
                String expected=codes[index];
                assertThatThrownBy(()->scope.check(true)).hasMessage(expected);
                assertThatThrownBy(()->scope.check(false)).hasMessage(expected);
                verify(owner,times(3)).control("run");
            }
        }
    }

    @Test void interruptAndScopeLifetimeRefuseWorkWithoutAnotherSqlRead() throws Exception {
        var owner=owner();var fields=new CdsFieldParser();
        var scope=scope(owner,fields,new AtomicLong(),10000,()->{});
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(()->scope.check(false)).hasMessage("IMPORT_CANCELLED");
        } finally {Thread.interrupted();scope.close();}
        assertThatThrownBy(()->scope.check(true)).isInstanceOf(IllegalStateException.class);
        verify(owner,times(1)).control("run");
    }

    @Test void monotonicElapsedPollingWorksAcrossNegativeValuesAndNanoTimeWrap() throws Exception {
        for(long initial:new long[]{-1_000_000_000L,Long.MAX_VALUE-100_000_000L}) {
            var clock=new AtomicLong(initial);var owner=owner();var fields=new CdsFieldParser();
            try(var scope=scope(owner,fields,clock,10000,()->{})) {
                clock.addAndGet(249_999_999);fields.checkpoint();verify(owner,times(1)).control("run");
                clock.incrementAndGet();fields.checkpoint();verify(owner,times(2)).control("run");
            }
        }
    }

    private static CdsImportCoordinatorService.Owner owner() {
        var owner=mock(CdsImportCoordinatorService.Owner.class);when(owner.control("run")).thenReturn(VALID);return owner;
    }
    private static CdsImportExecutionControl scope(CdsImportCoordinatorService.Owner owner,CdsFieldParser fields,
            AtomicLong clock,long millis,Runnable budget) {
        return new CdsImportExecutionControl("run",7,owner,"IMPORT_FILE_VERIFICATION_TIMEOUT",millis,clock::get,budget,fields);
    }
}
