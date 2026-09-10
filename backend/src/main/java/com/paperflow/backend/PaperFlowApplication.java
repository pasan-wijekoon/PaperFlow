package com.paperflow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;


@SpringBootApplication
public class PaperFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaperFlowApplication.class, args);
    }
}
