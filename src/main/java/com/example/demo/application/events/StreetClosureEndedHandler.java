package com.example.demo.application.events;
import org.springframework.stereotype.Component;

@Component
public class StreetClosureEndedHandler implements EventHandler {
    private final ClosureResponseProcessor processor;
    public StreetClosureEndedHandler(ClosureResponseProcessor processor) {this.processor=processor;}
    public String eventType() {return "streetClosureEnded";}
    public String sourceModule() {return "transito";}
    public boolean relevant(EventEnvelope e) {return true;}
    public void validate(EventEnvelope e) {
        ClosureEventValidation.base(e);
        if(e.correlationId()==null || e.causationId()!=null) throw new InvalidEventException();
        EnvironmentEventFields.uuid(e.data(),"streetClosureId");EnvironmentEventFields.time(e.data(),"completionDateTime");
    }
    public void handle(EventEnvelope e) {processor.apply(e);}
}
