package com.csc301.controller;

import com.csc301.model.Product;
import com.csc301.repository.ProductRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/product")
public class ProductController {
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @PostMapping
    public ResponseEntity<?> handleProductRequest(@RequestBody String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String command = json.has("command") ? json.get("command").getAsString() : null;

            if (command == null || command.isEmpty()) {
                return ResponseEntity.status(400).body("{\"error\": \"Missing command field\"}");
            }

            switch (command.toLowerCase()) {
                case "create":
                    return createProduct(json);
                case "update":
                    return updateProduct(json);
                case "delete":
                    return deleteProduct(json);
                default:
                    return ResponseEntity.status(400).body("{\"error\": \"Invalid command\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid JSON request\"}");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable int id) {
        Optional<Product> product = productRepository.findById(id);
        if (!product.isPresent()) {
            return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
        }

        Product p = product.get();
        JsonObject response = new JsonObject();
        response.addProperty("id", p.getId());
        response.addProperty("name", p.getProductname());
        response.addProperty("description", p.getDescription());
        response.addProperty("price", p.getPrice());
        response.addProperty("quantity", p.getQuantity());
        return ResponseEntity.ok(response.toString());
    }

    private ResponseEntity<?> createProduct(JsonObject json) {
        if (!json.has("id") || !json.has("name") || !json.has("description") || !json.has("price") || !json.has("quantity")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        try {
            int id = parseIntStrict(json, "id");
            String name = json.get("name").getAsString();
            String description = json.get("description").getAsString();
            float price = (float) json.get("price").getAsDouble();
            int quantity = parseIntStrict(json, "quantity");

            if (name == null || name.isEmpty()) {
                return ResponseEntity.status(400).body("{\"error\": \"Product name cannot be empty\"}");
            }

            if (description == null || description.isEmpty()) {
                return ResponseEntity.status(400).body("{\"error\": \"Product description cannot be empty\"}");
            }

            if (price < 0 || quantity < 0) {
                return ResponseEntity.status(400).body("{\"error\": \"Invalid field values\"}");
            }

            if (productRepository.existsById(id)) {
                return ResponseEntity.status(409).body("{\"error\": \"Product already exists\"}");
            }

            Product product = new Product(id, name, price, quantity);
            product.setDescription(description);
            productRepository.save(product);

            JsonObject response = new JsonObject();
            response.addProperty("id", product.getId());
            response.addProperty("name", product.getProductname());
            response.addProperty("description", product.getDescription());
            response.addProperty("price", product.getPrice());
            response.addProperty("quantity", product.getQuantity());
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid field types\"}");
        }
    }

    private ResponseEntity<?> updateProduct(JsonObject json) {
        if (!json.has("id")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing id field\"}");
        }

        try {
            int id = parseIntStrict(json, "id");
            Optional<Product> productOpt = productRepository.findById(id);

            if (!productOpt.isPresent()) {
                return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
            }

            Product product = productOpt.get();

            if (json.has("name")) {
                String name = json.get("name").getAsString();
                if (name.isEmpty()) {
                    return ResponseEntity.status(400).body("{\"error\": \"Product name cannot be empty\"}");
                }
                product.setProductname(name);
            }

            if (json.has("description")) {
                String description = json.get("description").getAsString();
                if (description.isEmpty()) {
                    return ResponseEntity.status(400).body("{\"error\": \"Product description cannot be empty\"}");
                }
                product.setDescription(description);
            }

            if (json.has("price")) {
                float price = (float) json.get("price").getAsDouble();
                if (price < 0) {
                    return ResponseEntity.status(400).body("{\"error\": \"Invalid field values\"}");
                }
                product.setPrice(price);
            }

            if (json.has("quantity")) {
                int quantity = parseIntStrict(json, "quantity");
                if (quantity < 0) {
                    return ResponseEntity.status(400).body("{\"error\": \"Invalid field values\"}");
                }
                product.setQuantity(quantity);
            }

            productRepository.save(product);

            JsonObject response = new JsonObject();
            response.addProperty("id", product.getId());
            response.addProperty("name", product.getProductname());
            response.addProperty("description", product.getDescription());
            response.addProperty("price", product.getPrice());
            response.addProperty("quantity", product.getQuantity());
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid field types\"}");
        }
    }

    private ResponseEntity<?> deleteProduct(JsonObject json) {
        if (!json.has("id") || !json.has("name") || !json.has("price") || !json.has("quantity")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        try {
            int id = parseIntStrict(json, "id");
            String name = json.get("name").getAsString();
            float price = (float) json.get("price").getAsDouble();
            int quantity = parseIntStrict(json, "quantity");

            Optional<Product> productOpt = productRepository.findById(id);
            if (!productOpt.isPresent()) {
                return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
            }

            Product product = productOpt.get();
            if (!product.getProductname().equals(name) ||
                product.getPrice() != price ||
                product.getQuantity() != quantity) {
                return ResponseEntity.status(404).body("{\"error\": \"Product details do not match\"}");
            }

            productRepository.deleteById(id);
            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid field types\"}");
        }
    }

    private int parseIntStrict(JsonObject json, String fieldName) {
        double value = json.get(fieldName).getAsDouble();
        if (value % 1 != 0) {
            throw new IllegalArgumentException("Non-integer value for field: " + fieldName);
        }
        return (int) value;
    }

    // Delete all products (called by OrderService on non-restart startup)
    @DeleteMapping("/deleteall")
    public ResponseEntity<?> deleteAllProducts() {
        try {
            productRepository.deleteAll();
            return ResponseEntity.ok("{\"message\": \"All products deleted\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\": \"Failed to delete all products\"}");
        }
    }

    // Shutdown endpoint
    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown() {
        System.out.println("Shutdown command received — shutting down ProductService.");
        new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            applicationContext.close();
            System.exit(0);
        }).start();
        return ResponseEntity.ok("{\"message\": \"Product Service shutting down\"}");
    }
}
