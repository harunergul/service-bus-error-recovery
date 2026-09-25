package com.example.patientservice.publishing;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.example.patientservice.move.PatientMoved;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PatientMovedPublisher {

	private static final Logger log = LoggerFactory.getLogger(PatientMovedPublisher.class);

	private final ServiceBusSenderClient sender;

	private final JsonMapper jsonMapper;

	public PatientMovedPublisher(@Value("${servicebus.connection-string}") String connectionString,
			@Value("${servicebus.topic}") String topic, JsonMapper jsonMapper) {
		this.sender = new ServiceBusClientBuilder().connectionString(connectionString).sender().topicName(topic).buildClient();
		this.jsonMapper = jsonMapper;
	}

	public void publish(PatientMoved event) {
		ServiceBusMessage message = new ServiceBusMessage(jsonMapper.writeValueAsString(event));
		message.setMessageId(String.valueOf(event.moveId()));
		message.setContentType("application/json");
		message.setSubject("PatientMoved");
		sender.sendMessage(message);
		log.info("Published {}", event);
	}

	@PreDestroy
	void close() {
		sender.close();
	}

}
