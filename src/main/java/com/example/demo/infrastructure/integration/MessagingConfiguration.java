package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class MessagingConfiguration {
    @Bean(destroyMethod="destroy")
    CachingConnectionFactory eventConnectionFactory(Environment env, javax.sql.DataSource database) throws Exception {
        if (!env.getProperty("app.messaging.tls",Boolean.class,true))
            throw new IllegalStateException("AMQPS requires TLS; RABBITMQ_TLS_ENABLED cannot be false");
        try (var connection=database.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"))
                throw new IllegalStateException("Messaging requires PostgreSQL");
        }
        var client=new com.rabbitmq.client.ConnectionFactory();
        client.setHost(env.getRequiredProperty("app.messaging.host"));
        client.setPort(env.getRequiredProperty("app.messaging.port",Integer.class));
        client.setUsername(env.getRequiredProperty("app.messaging.username"));
        var password=env.getRequiredProperty("app.messaging.password");
        if(password.isBlank()) throw new IllegalStateException("RABBITMQ_PASSWORD is required when messaging is enabled");
        client.setPassword(password);
        client.setVirtualHost(env.getProperty("app.messaging.vhost","core"));
        // Trust the JVM certificate store and verify the server DNS name.
        client.useSslProtocol(javax.net.ssl.SSLContext.getDefault());
        client.enableHostnameVerification();
        var factory=new CachingConnectionFactory(client);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }
    @Bean RabbitTemplate eventRabbitTemplate(CachingConnectionFactory eventConnectionFactory) {
        var template=new RabbitTemplate(eventConnectionFactory); template.setMandatory(true); return template;
    }
    @Bean EventTransport eventTransport(RabbitTemplate eventRabbitTemplate,EventSerialization codec,Environment env) {
        return new RabbitEventTransport(eventRabbitTemplate,codec,env.getProperty("app.messaging.exchange","muni.inbox"));
    }
    @Bean SimpleMessageListenerContainer eventListenerContainer(CachingConnectionFactory eventConnectionFactory,
            EventDispatcher dispatcher,EventTelemetry metrics,Environment env) {
        var container=new SimpleMessageListenerContainer(eventConnectionFactory);
        container.setQueueNames(env.getProperty("app.messaging.queue","q.obras"));
        container.setAutoDeclare(false);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setDefaultRequeueRejected(false);
        container.setPrefetchCount(10);
        container.setMessageListener(new RabbitEventListener(dispatcher,metrics));
        return container;
    }
    // Intentionally no RabbitAdmin, Exchange, Queue or Binding bean. Core owns topology.
    @Bean MessagingWorker messagingWorker(JdbcEventOutbox outbox,EventTransport transport,EventDispatcher dispatcher) {
        return new MessagingWorker(outbox,transport,dispatcher);
    }
    public static class MessagingWorker {
        private final JdbcEventOutbox outbox; private final EventTransport transport; private final EventDispatcher dispatcher;
        MessagingWorker(JdbcEventOutbox outbox,EventTransport transport,EventDispatcher dispatcher) {
            this.outbox=outbox; this.transport=transport; this.dispatcher=dispatcher;
        }
        @Scheduled(fixedDelayString="${app.messaging.poll-ms:5000}")
        public void work() {
            dispatcher.recover();
            for(int i=0;i<20 && outbox.publishOne(transport);i++) { /* bounded batch */ }
        }
    }
}
