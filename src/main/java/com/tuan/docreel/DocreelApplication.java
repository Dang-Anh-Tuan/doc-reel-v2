package com.tuan.docreel;

import com.tuan.docreel.system.DoctorCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DocreelApplication {

    public static void main(String[] args) {
        if (DoctorCommand.isRequested(args)) {
            DoctorCommand.runAndExit(DocreelApplication.class, args);
            return;
        }
        SpringApplication.run(DocreelApplication.class, args);
    }
}
