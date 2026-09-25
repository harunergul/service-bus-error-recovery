package com.example.patientservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:h2:mem:testdb", "listener.enabled=false" })
class PatientServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
