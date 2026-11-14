package io.github.yatab1.vigie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VigieApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VigieApiApplication.class, args);
    }
}
