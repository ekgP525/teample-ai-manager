package com.teample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TeampleBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeampleBackendApplication.class, args);
    }

}
