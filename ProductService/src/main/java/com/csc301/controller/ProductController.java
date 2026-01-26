package com.csc301.controller;

import com.csc301.model.Product;
import com.csc301.repository.ProductRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductRepository productRepository = new ProductRepository();

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
        Product product = productRepository.findById(id);
        if (product == null) {
            return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
        }

        JsonObject response = new JsonObject();
        response.addProperty("id", product.getId());
        response.addProperty("productname", product.getProductname());
        response.addProperty("price", product.getPrice());
        response.addProperty("quantity", product.getQuantity());
        return ResponseEntity.ok(response.toString());
    }

    private ResponseEntity<?> createProduct(JsonObject json) {
        if (!json.has("id") || !json.has("productname") || !json.has("price") || !json.has("quantity")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        int id = json.get("id").getAsInt();
        String productname = json.get("productname").getAsString();
        float price = json.get("price").getAsFloat();
        int quantity = json.get("quantity").getAsInt();

        if (productname.isEmpty()) {
            return ResponseEntity.status(400).body("{\"error\": \"Product name cannot be empty\"}");
        }

        if (productRepository.existsById(id)) {
            return ResponseEntity.status(409).body("{\"error\": \"Product already exists\"}");
        }

        Product product = new Product(id, productname, price, quantity);
        productRepository.save(product);
        return ResponseEntity.ok("{\"message\": \"Product created successfully\"}");
    }

    private ResponseEntity<?> updateProduct(JsonObject json) {
        if (!json.has("id")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing id field\"}");
        }

        int id = json.get("id").getAsInt();
        Product product = productRepository.findById(id);

        if (product == null) {
            return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
        }

        if (json.has("productname") && !json.get("productname").getAsString().isEmpty()) {
            product.setProductname(json.get("productname").getAsString());
        }
        if (json.has("price")) {
            product.setPrice(json.get("price").getAsFloat());
        }
        if (json.has("quantity")) {
            product.setQuantity(json.get("quantity").getAsInt());
        }

        productRepository.save(product);
        return ResponseEntity.ok("{\"message\": \"Product updated successfully\"}");
    }

    private ResponseEntity<?> deleteProduct(JsonObject json) {
        if (!json.has("id") || !json.has("productname") || !json.has("price") || !json.has("quantity")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        int id = json.get("id").getAsInt();
        String productname = json.get("productname").getAsString();
        float price = json.get("price").getAsFloat();
        int quantity = json.get("quantity").getAsInt();

        Product product = productRepository.findById(id);
        if (product == null) {
            return ResponseEntity.status(404).body("{\"error\": \"Product not found\"}");
        }

        if (!product.getProductname().equals(productname) || 
            product.getPrice() != price || 
            product.getQuantity() != quantity) {
            return ResponseEntity.status(401).body("{\"error\": \"Product details do not match\"}");
        }

        productRepository.deleteById(id);
        return ResponseEntity.ok("{\"message\": \"Product deleted successfully\"}");
    }
}
