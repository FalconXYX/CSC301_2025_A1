package com.csc301.model;

import java.util.UUID;

public class Order {
    private String id;
    private int user_id;
    private int product_id;
    private int quantity;

    public Order() {
        this.id = UUID.randomUUID().toString();
    }

    public Order(int user_id, int product_id, int quantity) {
        this.id = UUID.randomUUID().toString();
        this.user_id = user_id;
        this.product_id = product_id;
        this.quantity = quantity;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getUser_id() { return user_id; }
    public void setUser_id(int user_id) { this.user_id = user_id; }

    public int getProduct_id() { return product_id; }
    public void setProduct_id(int product_id) { this.product_id = product_id; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
