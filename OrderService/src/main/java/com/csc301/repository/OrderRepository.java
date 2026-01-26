package com.csc301.repository;

import com.csc301.model.Order;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderRepository {
    private static final Map<String, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    public Order findById(String id) {
        return orders.get(id);
    }

    public boolean existsById(String id) {
        return orders.containsKey(id);
    }

    public void deleteById(String id) {
        orders.remove(id);
    }

    public void clear() {
        orders.clear();
    }
}
