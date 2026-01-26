package main.java.com.csc301.repository;

import com.csc301.model.User;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepository {
    private static final Map<Integer, User> users = new ConcurrentHashMap<>();

    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }

    public User findById(int id) {
        return users.get(id);
    }

    public boolean existsById(int id) {
        return users.containsKey(id);
    }

    public void deleteById(int id) {
        users.remove(id);
    }

    public void clear() {
        users.clear();
    }
}
