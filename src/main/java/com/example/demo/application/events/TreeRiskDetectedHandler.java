package com.example.demo.application.events;
import org.springframework.stereotype.Component;

@Component
public class TreeRiskDetectedHandler implements EventHandler {
    private final EnvironmentCaseStore cases;
    public TreeRiskDetectedHandler(EnvironmentCaseStore cases) { this.cases=cases; }
    public String eventType() { return "treeRiskDetected"; }
    public String sourceModule() { return "ambiente"; }
    public boolean relevant(EventEnvelope event) { return event.data().path("requiresPublicWorks").asBoolean(); }
    public void validate(EventEnvelope event) {
        var d=event.data(); EnvironmentEventFields.bool(d,"requiresPublicWorks");
        EnvironmentEventFields.bool(d,"requiresStreetClosure");
        EnvironmentEventFields.uuid(d,"treeId"); EnvironmentEventFields.uuid(d,"zoneId");
        EnvironmentEventFields.text(d,"surveyCode"); EnvironmentEventFields.time(d,"surveyedAt"); EnvironmentEventFields.location(d);
        if(!java.util.Set.of("HIGH","CRITICAL").contains(d.path("riskLevel").asString())) throw new InvalidEventException();
        if(!java.util.Set.of("FALLING_BRANCH","TRUNK_INSTABILITY","ROOT_UPLIFT","POWER_LINE_CONTACT","SIGN_OBSTRUCTION","PEST_INFESTATION")
                .contains(d.path("riskType").asString())) throw new InvalidEventException();
        if(!java.util.Set.of("HEALTHY","WEAKENED","DISEASED","DEAD").contains(d.path("healthStatus").asString())) throw new InvalidEventException();
        if(d.hasNonNull("suggestedIntervention") && !java.util.Set.of("FORMATION_PRUNING","SAFETY_PRUNING","REMOVAL","PLANTING","TREATMENT")
                .contains(d.path("suggestedIntervention").asString())) throw new InvalidEventException();
    }
    public void handle(EventEnvelope event) {
        cases.receive(event,"treeId","surveyedAt",event.data().path("suggestedIntervention").isString()
                && !event.data().path("suggestedIntervention").asString().isBlank());
    }
}
