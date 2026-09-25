package com.example.patientservice.listening;

import com.example.patientservice.move.PatientMoved;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "notification-service", url = "${notification-service.url}")
public interface NotificationServiceClient {

	@PostMapping("/patient-moves")
	void patientMoved(@RequestBody PatientMoved event);

	/**
	 * Checks that notification-service answers at all, without sending a PatientMoved. Only the
	 * kind of answer matters: a 4xx (e.g. 405, since the endpoint only takes POST) means it is up,
	 * while a 5xx or a connection error means it is still down.
	 */
	@RequestMapping(method = RequestMethod.HEAD, value = "/patient-moves")
	void probe();

}
