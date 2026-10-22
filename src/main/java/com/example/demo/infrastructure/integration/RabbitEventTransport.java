package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class RabbitEventTransport implements EventTransport {
    private final RabbitTemplate template;
    private final EventSerialization codec;
    private final String exchange;
    public RabbitEventTransport(RabbitTemplate template, EventSerialization codec, String exchange) {
        this.template=template; this.codec=codec; this.exchange=exchange;
    }
    @Override public void publish(EventEnvelope event) {
        var properties=new MessageProperties();
        properties.setContentType("application/json");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId().toString());
        var confirmation=new CorrelationData(event.eventId().toString());
        try {
            template.send(exchange,"",new Message(codec.encode(event).getBytes(StandardCharsets.UTF_8),properties),confirmation);
            if (!confirmation.getFuture().get(10,TimeUnit.SECONDS).ack() || confirmation.getReturned()!=null)
                throw new IllegalStateException("PUBLICATION_NOT_CONFIRMED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("PUBLICATION_INTERRUPTED");
        } catch (Exception ex) {
            // Exceptions from the client can contain connection details: don't attach them.
            throw new IllegalStateException("PUBLICATION_NOT_CONFIRMED");
        }
    }
}
