package com.csc301;

import com.csc301.config.ConfigLoader;
import com.csc301.controller.OrderController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class OrderServiceApp {

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "config.json";
        
        try {
            ConfigLoader.Config config = ConfigLoader.loadConfig(configPath, "OrderService");
            ConfigLoader.ISCSConfig iscsConfig = new ConfigLoader.ISCSConfig(
                ConfigLoader.getISCSIp(configPath),
                ConfigLoader.getISCSPort(configPath)
            );
            
            SpringApplication app = new SpringApplication(OrderServiceApp.class);
            java.util.Map<String, Object> properties = new java.util.HashMap<>();
            properties.put("server.port", config.port);
            properties.put("server.address", config.ip);
            app.setDefaultProperties(properties);
            
            ConfigurableApplicationContext context = app.run(args);
            
            // Inject ISCS config into OrderController
            OrderController orderController = context.getBean(OrderController.class);
            orderController.setISCSConfig(iscsConfig);
            
            System.out.println("Order Service started on " + config.ip + ":" + config.port);
        } catch (Exception e) {
            System.err.println("Failed to load config: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
