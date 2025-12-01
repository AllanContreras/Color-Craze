package com.Color_craze;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ColorCrazeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ColorCrazeApplication.class, args);
	}
}
