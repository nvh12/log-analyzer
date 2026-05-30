package com.nvh12.reaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ReactionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactionApplication.class, args);
    }

}
