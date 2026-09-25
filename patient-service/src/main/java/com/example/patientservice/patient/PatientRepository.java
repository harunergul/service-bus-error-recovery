package com.example.patientservice.patient;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	boolean existsByNationalIdentity(String nationalIdentity);

}
