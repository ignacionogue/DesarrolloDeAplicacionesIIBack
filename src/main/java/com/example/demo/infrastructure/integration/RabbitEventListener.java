package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

public class RabbitEventListener implements ChannelAwareMessageListener {
    private final EventDispatcher dispatcher;
    private final EventTelemetry metrics;
    public RabbitEventListener(EventDispatcher dispatcher, EventTelemetry metrics) { this.dispatcher=dispatcher; this.metrics=metrics; }
    @Override public void onMessage(Message message, Channel channel) throws Exception {
        long tag=message.getMessageProperties().getDeliveryTag();
        try {
            dispatcher.dispatch(message.getBody());
        } catch (RuntimeException failure) {
            metrics.result("nacked",null);
            channel.basicNack(tag,false,false);
            return;
        }
        // Do not nack if the ack itself fails; the broker will redeliver after disconnect.
        channel.basicAck(tag,false);
    }
}
