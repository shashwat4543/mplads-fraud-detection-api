package com.mplads.fraud_detection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FraudDetectionApplication {
	public static void main(String[] args) {
		SpringApplication.run(FraudDetectionApplication.class, args);
	}
}