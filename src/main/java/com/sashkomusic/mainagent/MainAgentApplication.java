package com.sashkomusic.mainagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MainAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainAgentApplication.class, args);
    }

}
