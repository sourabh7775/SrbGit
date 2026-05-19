package com.angelbroking.smartapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.angelbroking.smartapi.algo.rds")
public class TradeApplication {
	public static void main(String[] args) {
		log.info("app");
		try {
			SpringApplication.run(TradeApplication.class, args);
		} catch (Throwable t) {
			log.error("", t);
		}
	}
}
