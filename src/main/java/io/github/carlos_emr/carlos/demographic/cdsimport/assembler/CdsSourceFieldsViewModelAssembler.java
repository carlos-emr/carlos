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
package io.github.carlos_emr.carlos.demographic.cdsimport.assembler;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalSourceService;
import io.github.carlos_emr.carlos.demographic.cdsimport.viewmodel.CdsSourceFieldsViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

/** Read-only category view assembly with explicit previews and immutable original-value links. */
@Service
public class CdsSourceFieldsViewModelAssembler {
    private static final int PAGE=50,PREVIEW=2000;
    private final CdsClinicalSourceService source;
    public CdsSourceFieldsViewModelAssembler(CdsClinicalSourceService source){this.source=source;}
    public CdsSourceFieldsViewModel assemble(LoggedInInfo actor,int demographicNo,String category,String after){
        var page=source.page(actor,demographicNo,category,after,PAGE);
        var rows=page.stream().map(field->new CdsSourceFieldsViewModel.Field(field.id(),field.sourcePath(),field.occurrence(),
                field.sourceNamespace(),field.valueType(),field.precision(),preview(field.originalValue()),preview(field.normalizedValue()),
                length(field.originalValue())>PREVIEW || length(field.normalizedValue())>PREVIEW,field.payloadLocation()!=null,field.payloadLength())).toList();
        return new CdsSourceFieldsViewModel(demographicNo,category,rows,page.size()==PAGE?page.get(page.size()-1).id():null);
    }
    private static int length(String value){return value==null?0:value.length();}
    private static String preview(String value){
        if(value==null)return ""; int end=Math.min(PREVIEW,value.length());
        if(end<value.length() && end>0 && Character.isHighSurrogate(value.charAt(end-1)))end--;
        return value.substring(0,end);
    }
}
