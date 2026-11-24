package com.yourapp.ticketpass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.yourapp.ticketpass.shared.config")
@EnableScheduling
public class TicketPassApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketPassApplication.class, args);
    }
}
