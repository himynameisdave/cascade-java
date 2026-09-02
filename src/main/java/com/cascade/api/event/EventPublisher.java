package com.cascade.api.event;

import java.util.Properties;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes board events to the two integration surfaces this deployment
 * supports: a JMS topic that existing internal tooling subscribes to, and a
 * Kafka topic feeding the analytics pipeline.
 *
 * <p>Both are optional and best-effort. Neither is allowed to fail or delay the
 * request that produced the event — webhooks are the guaranteed path.
 */
public class EventPublisher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EventPublisher.class);

    private static final String JMS_TOPIC = "cascade.issues";
    private static final String KAFKA_TOPIC = "cascade-issue-events";

    private final Connection jmsConnection;
    private final Session jmsSession;
    private final MessageProducer jmsProducer;
    private final KafkaProducer<String, String> kafka;

    public EventPublisher(String brokerUrl, String kafkaServers) {
        this.jmsConnection = openJms(brokerUrl);
        Session session = null;
        MessageProducer producer = null;
        if (jmsConnection != null) {
            try {
                session = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(JMS_TOPIC);
                producer = session.createProducer(topic);
            } catch (JMSException e) {
                LOG.warn("JMS session unavailable: {}", e.getMessage());
            }
        }
        this.jmsSession = session;
        this.jmsProducer = producer;
        this.kafka = openKafka(kafkaServers);
    }

    private Connection openJms(String brokerUrl) {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            return null;
        }
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            // Only deserialize our own payloads if a consumer ever sends objects back.
            factory.setTrustedPackages(java.util.List.of("com.cascade"));
            Connection connection = factory.createConnection();
            connection.start();
            return connection;
        } catch (JMSException e) {
            LOG.warn("JMS broker unavailable at {}: {}", brokerUrl, e.getMessage());
            return null;
        }
    }

    private KafkaProducer<String, String> openKafka(String servers) {
        if (servers == null || servers.isBlank()) {
            return null;
        }
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2_000);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        try {
            return new KafkaProducer<>(props);
        } catch (RuntimeException e) {
            LOG.warn("Kafka producer unavailable: {}", e.getMessage());
            return null;
        }
    }

    public void publish(String projectId, String event, String payloadJson) {
        if (jmsProducer != null && jmsSession != null) {
            try {
                TextMessage message = jmsSession.createTextMessage(payloadJson);
                message.setStringProperty("event", event);
                message.setStringProperty("projectId", projectId);
                jmsProducer.send(message);
            } catch (JMSException e) {
                LOG.warn("could not publish {} to JMS: {}", event, e.getMessage());
            }
        }
        if (kafka != null) {
            try {
                kafka.send(new ProducerRecord<>(KAFKA_TOPIC, projectId, payloadJson));
            } catch (RuntimeException e) {
                LOG.warn("could not publish {} to Kafka: {}", event, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (kafka != null) {
            kafka.close();
        }
        try {
            if (jmsSession != null) {
                jmsSession.close();
            }
            if (jmsConnection != null) {
                jmsConnection.close();
            }
        } catch (JMSException e) {
            LOG.warn("could not close the JMS connection: {}", e.getMessage());
        }
    }
}
