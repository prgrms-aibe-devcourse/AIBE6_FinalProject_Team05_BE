package com.pokade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.pokade.global.config.FlywayMigrationListener;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PokadeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PokadeApplication.class);
        app.addListeners(new FlywayMigrationListener());
        app.run(args);
    }

}
