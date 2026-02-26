package com.csc301;

import com.csc301.config.ConfigLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApp {

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "config.json";

        try {
            String serverPortEnv = System.getenv("SERVER_PORT");
            String serverAddressEnv = System.getenv("SERVER_ADDRESS");

            SpringApplication app = new SpringApplication(UserServiceApp.class);
            java.util.Map<String, Object> properties = new java.util.HashMap<>();

            if (serverPortEnv != null) {
                properties.put("server.port", Integer.parseInt(serverPortEnv));
            } else {
                ConfigLoader.Config config = ConfigLoader.loadConfig(configPath);
                properties.put("server.port", config.port);
                properties.put("server.address", config.ip);
            }
            if (serverAddressEnv != null) {
                properties.put("server.address", serverAddressEnv);
            }

            app.setDefaultProperties(properties);
            app.run(args);
            System.out.println("User Service started.");
        } catch (Exception e) {
            System.err.println("Failed to start User Service: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

