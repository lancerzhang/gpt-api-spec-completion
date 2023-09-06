package com.example.gasc;

import com.example.gasc.service.RamlCompletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GptApiSpecCompletionApplication {

    @Autowired
    private ApplicationContext applicationContext;
    public static void main(String[] args) {
        // Logic to determine app data location
        String appDataLocation = System.getenv("APP_DATA");
        if (appDataLocation == null || appDataLocation.trim().isEmpty()) {
            appDataLocation = System.getProperty("user.home");
        }

        // Set the determined location in system properties so that it can be used in application.properties
        System.setProperty("app.location", appDataLocation);

        SpringApplication.run(com.example.gasc.GptApiSpecCompletionApplication.class, args);
    }
//    public static void main(String[] args) {
//        SpringApplication.run(GptApiSpecCompletionApplication.class, args);
//    }


//    @Bean
//    public CommandLineRunner runRamlProcessing() {
//        return args -> {
//            // The path to the Mule project, you can modify this as required
//            String projectPath = "/Users/lancer/Development/ws/getting-started-hello-mule";
//
//            RamlCompletionService service = applicationContext.getBean(RamlCompletionService.class);
//            service.configure(projectPath);
//            service.process();
//        };
//    }
}
