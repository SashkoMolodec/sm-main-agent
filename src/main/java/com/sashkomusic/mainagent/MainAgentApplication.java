package com.sashkomusic.mainagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class MainAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MainAgentApplication.class, args);
    }

}
