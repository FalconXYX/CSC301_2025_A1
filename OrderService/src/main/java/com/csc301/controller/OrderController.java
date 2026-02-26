package com.csc301.controller;

import com.csc301.model.Order;
import com.csc301.repository.OrderRepository;
import com.csc301.client.ServiceClient;
import com.csc301.config.ConfigLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private ServiceClient userServiceClient;
    private ServiceClient productServiceClient;
    private ConfigLoader.ISCSConfig iscsConfig;

    // Tracks whether the first command after startup has been handled
    private final AtomicBoolean firstCommandHandled = new AtomicBoolean(false);

    @PostMapping("/order")
    public ResponseEntity<?> placeOrder(@RequestBody String body) {
        handleFirstCommand(false);
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
        handleFirstCommand(false);
        try {
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("command")) {
                ServiceClient.ServiceResponse response = userServiceClient.request("/user", "POST", body);
                return ResponseEntity.status(response.statusCode).body(response.body);
            }

            if (json.has("id")) {
                int id = parseIntStrict(json, "id");
                ServiceClient.ServiceResponse response = userServiceClient.request("/user/" + id, "GET", "");
                if (response.statusCode == 404) {
                    return ResponseEntity.ok("{}");
                }
                return ResponseEntity.status(response.statusCode).body(response.body);
            }

            return ResponseEntity.status(400).body("{\"error\": \"Missing command or id\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid field types\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach User Service\"}");
        }
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUser(@PathVariable int id) {
        handleFirstCommand(false);
        try {
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse response = userServiceClient.request("/user/" + id, "GET", "");
            return ResponseEntity.status(response.statusCode).body(response.body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach User Service\"}");
        }
    }

    // NEW: Get all products purchased by a specific user
    @GetMapping("/user/purchased/{userId}")
    public ResponseEntity<?> getUserPurchased(@PathVariable int userId) {
        handleFirstCommand(false);
        try {
            // Verify user exists
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse userResponse = userServiceClient.request("/user/" + userId, "GET", "");
            if (userResponse.statusCode == 404) {
                return ResponseEntity.status(404)
                        .header("Content-Type", "application/json")
                        .body("{\"error\": \"User not found\"}");
            }
            if (userResponse.statusCode != 200) {
                return ResponseEntity.status(userResponse.statusCode)
                        .header("Content-Type", "application/json")
                        .body("{\"error\": \"Failed to verify user\"}");
            }

            // Aggregate order quantities by product_id
            List<Order> orders = orderRepository.findByUser_id(userId);
            Map<Integer, Integer> purchased = new HashMap<>();
            for (Order order : orders) {
                purchased.merge(order.getProduct_id(), order.getQuantity(), Integer::sum);
            }

            JsonObject result = new JsonObject();
            for (Map.Entry<Integer, Integer> entry : purchased.entrySet()) {
                result.addProperty(String.valueOf(entry.getKey()), entry.getValue());
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(result.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .header("Content-Type", "application/json")
                    .body("{\"error\": \"Failed to retrieve purchases\"}");
        }
    }

    @PostMapping("/product")
    public ResponseEntity<?> handleProductRequest(@RequestBody String body) {
        handleFirstCommand(false);
        try {
            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("command")) {
                ServiceClient.ServiceResponse response = productServiceClient.request("/product", "POST", body);
                return ResponseEntity.status(response.statusCode).body(response.body);
            }

            if (json.has("id")) {
                int id = parseIntStrict(json, "id");
                ServiceClient.ServiceResponse response = productServiceClient.request("/product/" + id, "GET", "");
                return ResponseEntity.status(response.statusCode).body(response.body);
            }

            return ResponseEntity.status(400).body("{\"error\": \"Missing command or id\"}");
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid field types\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach Product Service\"}");
        }
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<?> getProduct(@PathVariable int id) {
        handleFirstCommand(false);
        try {
            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse response = productServiceClient.request("/product/" + id, "GET", "");
            return ResponseEntity.status(response.statusCode).body(response.body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach Product Service\"}");
        }
    }

    // Shutdown: gracefully stop all services
    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown() {
        System.out.println("Shutdown command received — shutting down OrderService.");
        new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            applicationContext.close();
            System.exit(0);
        }).start();
        return ResponseEntity.ok("{\"message\": \"Order Service shutting down\"}");
    }

    // Restart: if this is the first command, preserve data; otherwise no-op
    @PostMapping("/restart")
    public ResponseEntity<?> restart() {
        if (!firstCommandHandled.getAndSet(true)) {
            System.out.println("Restart is the first command — preserving existing data.");
        }
        return ResponseEntity.ok("{\"message\": \"restart acknowledged\"}");
    }

    /**
     * Called before processing any user-facing request.
     * On the very first call: if isRestart=false, wipe all DB data.
     */
    private void handleFirstCommand(boolean isRestart) {
        if (firstCommandHandled.getAndSet(true)) {
            return; // already handled for previous commands
        }
        if (!isRestart) {
            System.out.println("First command is NOT restart — wiping all data.");
            wipeAllData();
        }
    }

    /** Clears all orders in the Order DB and signals UserService + ProductService to clear too. */
    private void wipeAllData() {
        try {
            orderRepository.deleteAll();
        } catch (Exception e) {
            System.err.println("Failed to wipe orders: " + e.getMessage());
        }
        try {
            ServiceClient client = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            client.request("/user/deleteall", "DELETE", "");
        } catch (Exception ignored) {}
        try {
            ServiceClient client = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            client.request("/product/deleteall", "DELETE", "");
        } catch (Exception ignored) {}
    }

    private ResponseEntity<?> handlePlaceOrder(JsonObject json) {
        try {
            if (!json.has("user_id") || !json.has("product_id") || !json.has("quantity")) {
                return ResponseEntity.status(400).body("{\"status\": \"Invalid Request\"}");
            }

            int userId = parseIntStrict(json, "user_id");
            int productId = parseIntStrict(json, "product_id");
            int quantity = parseIntStrict(json, "quantity");

            if (quantity <= 0) {
                return ResponseEntity.status(400).body("{\"status\": \"Invalid Request\"}");
            }

            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse userResponse = userServiceClient.request("/user/" + userId, "GET", "");
            if (userResponse.statusCode != 200) {
                return ResponseEntity.status(userResponse.statusCode).body("{\"status\": \"Invalid Request\"}");
            }

            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse productResponse = productServiceClient.request("/product/" + productId, "GET", "");
            if (productResponse.statusCode != 200) {
                return ResponseEntity.status(productResponse.statusCode).body("{\"status\": \"Invalid Request\"}");
            }

            JsonObject productResult = JsonParser.parseString(productResponse.body).getAsJsonObject();
            int currentQuantity = productResult.get("quantity").getAsInt();
            if (currentQuantity < quantity) {
                return ResponseEntity.ok("{\"status\": \"Exceeded quantity limit\"}");
            }

            int newQuantity = currentQuantity - quantity;
            JsonObject updateProduct = new JsonObject();
            updateProduct.addProperty("command", "update");
            updateProduct.addProperty("id", productId);
            updateProduct.addProperty("quantity", newQuantity);

            productServiceClient.request("/product", "POST", updateProduct.toString());

            Order order = new Order(userId, productId, quantity);
            orderRepository.save(order);

            JsonObject response = new JsonObject();
            response.addProperty("product_id", productId);
            response.addProperty("user_id", userId);
            response.addProperty("quantity", quantity);
            response.addProperty("status", "Success");
            return ResponseEntity.ok(response.toString());
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body("{\"status\": \"Invalid Request\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to process order\"}");
        }
    }

    private int parseIntStrict(JsonObject json, String fieldName) {
        double value = json.get(fieldName).getAsDouble();
        if (value % 1 != 0) {
            throw new IllegalArgumentException("Non-integer value for field: " + fieldName);
        }
        return (int) value;
    }

    public void setISCSConfig(ConfigLoader.ISCSConfig config) {
        this.iscsConfig = config;
    }
}
