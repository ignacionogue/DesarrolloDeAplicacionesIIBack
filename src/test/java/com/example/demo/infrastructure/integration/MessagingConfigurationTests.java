package com.example.demo.infrastructure.integration;

import com.example.demo.application.events.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.amqp.core.AcknowledgeMode;
import javax.sql.DataSource;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessagingConfigurationTests {
    private final MessagingConfiguration config = new MessagingConfiguration();
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("app.messaging.host", "core-uade-2026.duckdns.org")
            .withProperty("app.messaging.port", "5671").withProperty("app.messaging.username", "module_obras")
            .withProperty("app.messaging.password", "test-only-placeholder");
    }
    private DataSource postgres() throws Exception {
        var db=mock(DataSource.class); var connection=mock(Connection.class); var metadata=mock(DatabaseMetaData.class);
        when(db.getConnection()).thenReturn(connection); when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL"); return db;
    }
    @Test void tlsUsesJvmTrustAndHostnameVerificationWithoutHttpCredentials() throws Exception {
        var factory=config.eventConnectionFactory(environment(), postgres());
        try {
            var client=factory.getRabbitConnectionFactory();
            assertEquals("core-uade-2026.duckdns.org",client.getHost()); assertEquals(5671,client.getPort());
            assertEquals("module_obras",client.getUsername()); assertEquals("core",client.getVirtualHost());
            assertTrue(client.isSSL());
            assertEquals(SSLContext.getDefault().getSocketFactory().getClass(), client.getSocketFactory().getClass());
            try(var socket=(SSLSocket)client.getSocketFactory().createSocket()) {
                client.getSocketConfigurator().configure(socket);
                assertEquals("HTTPS",socket.getSSLParameters().getEndpointIdentificationAlgorithm());
            }
            assertTrue(factory.isPublisherConfirms()); assertTrue(factory.isPublisherReturns());
            var template=config.eventRabbitTemplate(factory);
            assertTrue(template.isMandatoryFor(new org.springframework.amqp.core.Message(new byte[0])));
            var container=config.eventListenerContainer(factory,mock(EventDispatcher.class),mock(EventTelemetry.class),environment());
            assertEquals(AcknowledgeMode.MANUAL,container.getAcknowledgeMode());
            assertArrayEquals(new String[]{"q.obras"},container.getQueueNames());
            assertFalse(org.springframework.test.util.ReflectionTestUtils.getField(container,"autoDeclare").equals(true));
            assertEquals(false,org.springframework.test.util.ReflectionTestUtils.getField(container,"defaultRequeueRejected"));
        } finally { factory.destroy(); }
    }
    @Test void refusesTlsDowngradeBeforeOpeningDatabaseOrBroker() {
        var db=mock(DataSource.class);
        assertThrows(IllegalStateException.class,()->config.eventConnectionFactory(environment().withProperty("app.messaging.tls","false"),db));
        verifyNoInteractions(db);
    }
    @Test void refusesBlankAmqpPassword() throws Exception {
        var error=assertThrows(IllegalStateException.class,()->config.eventConnectionFactory(environment().withProperty("app.messaging.password"," "),postgres()));
        assertEquals("RABBITMQ_PASSWORD is required when messaging is enabled",error.getMessage());
    }
}
