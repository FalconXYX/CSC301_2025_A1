package com.csc301;

import com.csc301.config.ConfigLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class UserServiceApp {

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "config.json";
        
        try {
            ConfigLoader.Config config = ConfigLoader.loadConfig(configPath);
            
            SpringApplication app = new SpringApplication(UserServiceApp.class);
            java.util.Map<String, Object> properties = new java.util.HashMap<>();
            properties.put("server.port", config.port);
            properties.put("server.address", config.ip);
            app.setDefaultProperties(properties);
            
            ConfigurableApplicationContext context = app.run(args);
            System.out.println("User Service started on " + config.ip + ":" + config.port);
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
