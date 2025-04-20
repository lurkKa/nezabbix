package gui.auth;

import java.util.Map;

public class AuthService {
    private static final Map<String, String> users = Map.of(
            "admin", "ADMIN",
            "user", "USER",
            "guest", "GUEST"
    );

    public static String authenticate(String username, String password) {
        // Упрощенная проверка (пароль всегда "1234")
        return users.containsKey(username) && password.equals("admin") ? users.get(username) : null;
    }
}
