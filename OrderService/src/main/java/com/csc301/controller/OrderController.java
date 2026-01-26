package com.csc301.controller;

import com.csc301.model.Order;
import com.csc301.repository.OrderRepository;
import com.csc301.client.ServiceClient;
import com.csc301.config.ConfigLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
    private final OrderRepository orderRepository = new OrderRepository();
    private ServiceClient userServiceClient;
    private ServiceClient productServiceClient;
    private ConfigLoader.ISCSConfig iscsConfig;

    @PostMapping("/order")
    public ResponseEntity<?> placeOrder(@RequestBody String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String command = json.has("command") ? json.get("command").getAsString() : null;

            if ("place order".equalsIgnoreCase(command)) {
                return handlePlaceOrder(json);
            }
            return ResponseEntity.status(400).body("{\"error\": \"Invalid command\"}");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid JSON request\"}");
        }
    }

    @PostMapping("/user")
    public ResponseEntity<?> handleUserRequest(@RequestBody String body) {
        try {
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            return ResponseEntity.ok().body(userServiceClient.makeRequest("/user", "POST", body));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach User Service\"}");
        }
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUser(@PathVariable int id) {
        try {
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject result = userServiceClient.makeRequest("/user/" + id, "GET", "");
            return result != null ? ResponseEntity.ok(result.toString()) : 
                   ResponseEntity.status(404).body("{\"error\": \"User not found\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach User Service\"}");
        }
    }

    @PostMapping("/product")
    public ResponseEntity<?> handleProductRequest(@RequestBody String body) {
        try {
            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            return ResponseEntity.ok().body(productServiceClient.makeRequest("/product", "POST", body));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach Product Service\"}");
        }
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<?> getProduct(@PathVariable int id) {
        try {
            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject result = productServiceClient.makeRequest("/product/" + id, "GET", "");
            return result != null ? ResponseEntity.ok(result.toString()) : 
                   ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach Product Service\"}");
        }
    }

    private ResponseEntity<?> handlePlaceOrder(JsonObject json) {
        try {
            if (!json.has("user_id") || !json.has("product_id") || !json.has("quantity")) {
                return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
            }

            int userId = json.get("user_id").getAsInt();
            int productId = json.get("product_id").getAsInt();
            int quantity = json.get("quantity").getAsInt();

            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject userResult = userServiceClient.makeRequest("/user/" + userId, "GET", "");
            if (userResult == null) {
                return ResponseEntity.status(404).body("{\"error\": \"User not found\"}");
            }

            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject productResult = productServiceClient.makeRequest("/product/" + productId, "GET", "");
            if (productResult == null) {
                return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
            }

            int currentQuantity = productResult.get("quantity").getAsInt();
            if (currentQuantity < quantity) {
                return ResponseEntity.status(400).body("{\"error\": \"Insufficient product quantity\"}");
            }

            int newQuantity = currentQuantity - quantity;
            JsonObject updateProduct = new JsonObject();
            updateProduct.addProperty("command", "update");
            updateProduct.addProperty("id", productId);
            updateProduct.addProperty("quantity", newQuantity);

            productServiceClient.makeRequest("/product", "POST", updateProduct.toString());

            Order order = new Order(userId, productId, quantity);
            orderRepository.save(order);

            JsonObject response = new JsonObject();
            response.addProperty("status", "success");
            response.addProperty("message", "Order placed successfully");
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to process order\"}");
        }
    }

    public void setISCSConfig(ConfigLoader.ISCSConfig config) {
        this.iscsConfig = config;
    }
}
