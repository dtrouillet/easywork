package fr.easywork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EasyworkApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyworkApplication.class, args);
	}
}
