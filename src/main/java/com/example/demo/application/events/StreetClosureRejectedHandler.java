package com.example.demo.application.events;
import org.springframework.stereotype.Component;

@Component
public class StreetClosureRejectedHandler implements EventHandler {
    private final ClosureResponseProcessor processor;
    public StreetClosureRejectedHandler(ClosureResponseProcessor processor) {this.processor=processor;}
    public String eventType() {return "streetClosureRejected";}
    public String sourceModule() {return "transito";}
    public boolean relevant(EventEnvelope e) {return ClosureEventValidation.relevant(e);}
    public void validate(EventEnvelope e) {ClosureEventValidation.base(e);EnvironmentEventFields.text(e.data(),"rejectionReason");EnvironmentEventFields.text(e.data(),"requestingModule");}
    public void handle(EventEnvelope e) {processor.apply(e);}
}
