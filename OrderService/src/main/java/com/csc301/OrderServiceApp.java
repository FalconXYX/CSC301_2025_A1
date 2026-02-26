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
            // Allow env vars to override config.json values (Docker support)
            String iscsIpEnv = System.getenv("ISCS_IP");
            String iscsPortEnv = System.getenv("ISCS_PORT");
            String serverPortEnv = System.getenv("SERVER_PORT");
            String serverAddressEnv = System.getenv("SERVER_ADDRESS");

            ConfigLoader.ISCSConfig iscsConfig;
            if (iscsIpEnv != null && iscsPortEnv != null) {
                iscsConfig = new ConfigLoader.ISCSConfig(iscsIpEnv, Integer.parseInt(iscsPortEnv));
            } else {
                iscsConfig = new ConfigLoader.ISCSConfig(
                    ConfigLoader.getISCSIp(configPath),
                    ConfigLoader.getISCSPort(configPath)
                );
            }

            SpringApplication app = new SpringApplication(OrderServiceApp.class);
            java.util.Map<String, Object> properties = new java.util.HashMap<>();

            if (serverPortEnv != null) {
                properties.put("server.port", Integer.parseInt(serverPortEnv));
            } else {
                ConfigLoader.Config config = ConfigLoader.loadConfig(configPath, "OrderService");
                properties.put("server.port", config.port);
                properties.put("server.address", config.ip);
            }
            if (serverAddressEnv != null) {
                properties.put("server.address", serverAddressEnv);
            }

            app.setDefaultProperties(properties);
            ConfigurableApplicationContext context = app.run(args);

            // Inject ISCS config into OrderController
            OrderController orderController = context.getBean(OrderController.class);
            orderController.setISCSConfig(iscsConfig);

            System.out.println("Order Service started. ISCS at " + iscsConfig.ip + ":" + iscsConfig.port);
        } catch (Exception e) {
            System.err.println("Failed to start Order Service: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

