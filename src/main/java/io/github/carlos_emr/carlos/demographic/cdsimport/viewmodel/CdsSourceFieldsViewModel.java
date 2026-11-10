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
package io.github.carlos_emr.carlos.demographic.cdsimport.viewmodel;

import java.util.List;

/** One immutable, bounded clinical-category page; storage locations never enter presentation. */
public record CdsSourceFieldsViewModel(int demographicNo, String category, List<Field> fields, String nextCursor, List<Review> reviews, String reviewNextCursor, String fieldCursor, String reviewCursor) {
    public CdsSourceFieldsViewModel { fields=List.copyOf(fields); reviews=List.copyOf(reviews); }
    public int getDemographicNo(){return demographicNo; } public String getCategory(){return category; }
    public List<Field> getFields(){return fields; } public String getNextCursor(){return nextCursor; }
    public List<Review> getReviews(){return reviews; } public String getReviewNextCursor(){return reviewNextCursor; }
    public String getFieldCursor(){return fieldCursor; } public String getReviewCursor(){return reviewCursor; }
    @Override public String toString(){return "CdsSourceFieldsViewModel[protected]"; }
    public record Review(String reviewId, String operator, String actedAt, String kind, String state, String reason, long acceptedVersion, String sourceXPath){
        public String getReviewId(){return reviewId; } public String getOperator(){return operator; } public String getActedAt(){return actedAt; }
        public String getKind(){return kind; } public String getState(){return state; } public String getReason(){return reason; } public long getAcceptedVersion(){return acceptedVersion; } public String getSourceXPath(){return sourceXPath; }
        @Override public String toString(){return "CdsSourceReviewViewModel[protected]"; }
    }
    public record Field(String id, String sourcePath, long occurrence, String namespace, String valueType, String precision,
            String originalPreview, String normalizedPreview, boolean normalizedAvailable, boolean truncated, boolean externalPayload, long payloadBytes) {
        public String getId(){return id; } public String getSourcePath(){return sourcePath; } public long getOccurrence(){return occurrence; }
        public String getNamespace(){return namespace; } public String getValueType(){return valueType; } public String getPrecision(){return precision; }
        public String getOriginalPreview(){return originalPreview; } public String getNormalizedPreview(){return normalizedPreview; }
        public boolean isNormalizedAvailable(){return normalizedAvailable; } public boolean isTruncated(){return truncated; } public boolean isExternalPayload(){return externalPayload; } public long getPayloadBytes(){return payloadBytes; }
        @Override public String toString(){return "CdsSourceFieldViewModel[protected]"; }
    }
}
