package com.csc301.controller;

import com.csc301.model.User;
import com.csc301.repository.UserRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserRepository userRepository = new UserRepository();

    @PostMapping
    public ResponseEntity<?> handleUserRequest(@RequestBody String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String command = json.has("command") ? json.get("command").getAsString() : null;

            if (command == null || command.isEmpty()) {
                return ResponseEntity.status(400).body("{\"error\": \"Missing command field\"}");
            }

            switch (command.toLowerCase()) {
                case "create":
                    return createUser(json);
                case "update":
                    return updateUser(json);
                case "delete":
                    return deleteUser(json);
                default:
                    return ResponseEntity.status(400).body("{\"error\": \"Invalid command\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.status(400).body("{\"error\": \"Invalid JSON request\"}");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable int id) {
        User user = userRepository.findById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("{\"error\": \"User not found\"}");
        }

        JsonObject response = new JsonObject();
        response.addProperty("id", user.getId());
        response.addProperty("username", user.getUsername());
        response.addProperty("email", user.getEmail());
        return ResponseEntity.ok(response.toString());
    }

    private ResponseEntity<?> createUser(JsonObject json) {
        if (!json.has("id") || !json.has("username") || !json.has("email") || !json.has("password")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        int id = json.get("id").getAsInt();
        String username = json.get("username").getAsString();
        String email = json.get("email").getAsString();
        String password = json.get("password").getAsString();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(400).body("{\"error\": \"Fields cannot be empty\"}");
        }

        if (userRepository.existsById(id)) {
            return ResponseEntity.status(409).body("{\"error\": \"User already exists\"}");
        }

        User user = new User(id, username, email, password);
        userRepository.save(user);
        return ResponseEntity.ok("{\"message\": \"User created successfully\"}");
    }

    private ResponseEntity<?> updateUser(JsonObject json) {
        if (!json.has("id")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing id field\"}");
        }

        int id = json.get("id").getAsInt();
        User user = userRepository.findById(id);

        if (user == null) {
            return ResponseEntity.status(404).body("{\"error\": \"User not found\"}");
        }

        if (json.has("username") && !json.get("username").getAsString().isEmpty()) {
            user.setUsername(json.get("username").getAsString());
        }
        if (json.has("email") && !json.get("email").getAsString().isEmpty()) {
            user.setEmail(json.get("email").getAsString());
        }
        if (json.has("password") && !json.get("password").getAsString().isEmpty()) {
            user.setPassword(json.get("password").getAsString());
        }

        userRepository.save(user);
        return ResponseEntity.ok("{\"message\": \"User updated successfully\"}");
    }

    private ResponseEntity<?> deleteUser(JsonObject json) {
        if (!json.has("id") || !json.has("username") || !json.has("email") || !json.has("password")) {
            return ResponseEntity.status(400).body("{\"error\": \"Missing required fields\"}");
        }

        int id = json.get("id").getAsInt();
        String username = json.get("username").getAsString();
        String email = json.get("email").getAsString();
        String password = json.get("password").getAsString();

        User user = userRepository.findById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("{\"error\": \"User not found\"}");
        }

        String passwordHash = User.hashPassword(password);
        if (!user.getUsername().equals(username) || 
            !user.getEmail().equals(email) || 
            !user.getPasswordHash().equals(passwordHash)) {
            return ResponseEntity.status(401).body("{\"error\": \"Credentials do not match\"}");
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok("{\"message\": \"User deleted successfully\"}");
    }
}
