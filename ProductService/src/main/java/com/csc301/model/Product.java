package com.csc301.model;

public class Product {
    private int id;
    private String productname;
    private String description;
    private float price;
    private int quantity;

    public Product() {}

    public Product(int id, String productname, float price, int quantity) {
        this.id = id;
        this.productname = productname;
        this.description = "";
        this.price = price;
        this.quantity = quantity;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getProductname() { return productname; }
    public void setProductname(String productname) { this.productname = productname; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public float getPrice() { return price; }
    public void setPrice(float price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
