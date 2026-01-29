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
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (json.has("command")) {
                ServiceClient.ServiceResponse response = userServiceClient.request("/user", "POST", body);
                if (response.statusCode == 404 && response.body != null && response.body.contains("Credentials do not match")) {
                    return ResponseEntity.ok("{}");
                }
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
        try {
            userServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse response = userServiceClient.request("/user/" + id, "GET", "");
            return ResponseEntity.status(response.statusCode).body(response.body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach User Service\"}");
        }
    }

    @PostMapping("/product")
    public ResponseEntity<?> handleProductRequest(@RequestBody String body) {
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
        try {
            productServiceClient = new ServiceClient(iscsConfig.ip, iscsConfig.port);
            ServiceClient.ServiceResponse response = productServiceClient.request("/product/" + id, "GET", "");
            return ResponseEntity.status(response.statusCode).body(response.body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to reach Product Service\"}");
        }
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
