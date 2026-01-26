package com.csc301.repository;

import com.csc301.model.Product;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProductRepository {
    private static final Map<Integer, Product> products = new ConcurrentHashMap<>();

    public Product save(Product product) {
        products.put(product.getId(), product);
        return product;
    }

    public Product findById(int id) {
        return products.get(id);
    }

    public boolean existsById(int id) {
        return products.containsKey(id);
    }

    public void deleteById(int id) {
        products.remove(id);
    }

    public void clear() {
        products.clear();
    }
}
