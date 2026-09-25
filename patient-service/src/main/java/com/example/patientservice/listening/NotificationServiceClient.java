package com.example.patientservice.listening;

import com.example.patientservice.move.PatientMoved;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${notification-service.url}")
public interface NotificationServiceClient {

	@PostMapping("/patient-moves")
	void patientMoved(@RequestBody PatientMoved event);

}
