//CHECKSTYLE:OFF

/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;

public class DocumentResponse {

    private List<DocumentTo1> documents;

    public DocumentResponse() {
        documents = new ArrayList<DocumentTo1>();
    }

    public DocumentResponse(ArrayList<DocumentTo1> documentList) {
        documents = documentList;
    }

    public List<DocumentTo1> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentTo1> documents) {
        this.documents = documents;
    }

    public void add(DocumentTo1 document) {
        getDocuments().add(document);
    }

    public void addAll(List<DocumentTo1> document) {
        for (DocumentTo1 documentto : document) {
            getDocuments().add(documentto);
        }
    }

    public void addAll(DocumentResponse documentResponse) {
        addAll(documentResponse.getDocuments());
    }

    public void mergeAll(DocumentResponse documentResponse) {
        addAll(documentResponse.getDocuments());
    }

}
