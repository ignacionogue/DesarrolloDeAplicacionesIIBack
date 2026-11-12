package com.example.demo.dto.response;

import java.util.List;

public record IncidentTriageSuggestion(
        String summary,
        DamageCategory damageCategory,
        SuggestedPriority suggestedPriority,
        RecommendedResourceType recommendedResourceType,
        double confidence,
        boolean humanReviewRequired,
        List<String> reasons
) {
    public enum DamageCategory { PAVEMENT, SIDEWALK, STREETLIGHT, DRAINAGE, STRUCTURE, TREE, OTHER }
    public enum SuggestedPriority { LOW, MEDIUM, HIGH, CRITICAL }
    public enum RecommendedResourceType {
        ROAD_CREW, SIDEWALK_CREW, ELECTRICAL_CREW, DRAINAGE_CREW,
        STRUCTURAL_INSPECTION, ARBORIST, MANUAL_REVIEW
    }

    public IncidentTriageSuggestion {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static IncidentTriageSuggestion manual(String reason) {
        return new IncidentTriageSuggestion(
                "La incidencia requiere clasificacion manual",
                DamageCategory.OTHER,
                SuggestedPriority.LOW,
                RecommendedResourceType.MANUAL_REVIEW,
                0.0,
                true,
                List.of(reason)
        );
    }
}
