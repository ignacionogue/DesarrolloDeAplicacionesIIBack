package com.example.demo;

import com.example.demo.application.events.*;
import com.example.demo.infrastructure.integration.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RabbitEventTransportTests {
    @Test void sendsPersistentJsonToEmptyRoutingKeyAndWaitsForConfirm() {
        var template=mock(RabbitTemplate.class); var codec=new EventCodec(JsonMapper.builder().findAndAddModules().build());
        var event=EventEnvelope.outgoing("testEvent",JsonMapper.builder().build().createObjectNode(),null,null);
        doAnswer(call->{
            Message message=call.getArgument(2); CorrelationData correlation=call.getArgument(3);
            assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getDeliveryMode());
            assertEquals("application/json",message.getMessageProperties().getContentType());
            assertEquals(event.eventId().toString(),message.getMessageProperties().getMessageId());
            assertEquals("module_obras",message.getMessageProperties().getUserId());
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null));return null;
        }).when(template).send(eq("muni.inbox"),eq(""),any(Message.class),any(CorrelationData.class));
        new RabbitEventTransport(template,codec,"muni.inbox","module_obras").publish(event);
        verify(template).send(eq("muni.inbox"),eq(""),any(Message.class),any(CorrelationData.class));
    }
    @Test void brokerNackDoesNotCountAsPublicationAndDoesNotLeakBrokerMessage() {
        var template=mock(RabbitTemplate.class); var codec=mock(EventSerialization.class);
        when(codec.encode(any())).thenReturn("{}");
        doAnswer(call->{((CorrelationData)call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false,"private broker info"));return null;})
                .when(template).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        var event=EventEnvelope.outgoing("testEvent",JsonMapper.builder().build().createObjectNode(),null,null);
        var error=assertThrows(IllegalStateException.class,()->new RabbitEventTransport(template,codec,"muni.inbox","module_obras").publish(event));
        assertEquals("PUBLICATION_NOT_CONFIRMED",error.getMessage());assertNull(error.getCause());
    }
    @Test void mandatoryReturnFailsEvenWhenBrokerConfirms() {
        var template=mock(RabbitTemplate.class); var codec=mock(EventSerialization.class);
        when(codec.encode(any())).thenReturn("{}");
        doAnswer(call->{
            CorrelationData correlation=call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]),312,"private routing info","muni.inbox",""));
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null)); return null;
        }).when(template).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        var event=EventEnvelope.outgoing("testEvent",JsonMapper.builder().build().createObjectNode(),null,null);
        var error=assertThrows(IllegalStateException.class,()->new RabbitEventTransport(template,codec,"muni.inbox","module_obras").publish(event));
        assertEquals("PUBLICATION_NOT_CONFIRMED",error.getMessage()); assertNull(error.getCause());
    }
}
