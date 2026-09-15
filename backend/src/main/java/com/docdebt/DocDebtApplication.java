package com.docdebt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocDebtApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocDebtApplication.class, args);
    }
}
