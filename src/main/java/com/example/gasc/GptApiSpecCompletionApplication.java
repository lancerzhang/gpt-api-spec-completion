package com.example.gasc;

import com.example.gasc.service.RamlCompletionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GptApiSpecCompletionApplication {

    @Autowired
    private ApplicationContext applicationContext;

    public static void main(String[] args) {
        SpringApplication.run(GptApiSpecCompletionApplication.class, args);
    }

    @Bean
    public CommandLineRunner runRamlProcessing() {
        return args -> {
            // The path to the Mule project, you can modify this as required
            String projectPath = "/Users/lancer/Development/ws/getting-started-hello-mule";

            RamlCompletionService service = applicationContext.getBean(RamlCompletionService.class);
            service.configure(projectPath);
            service.process();
        };
    }
}
