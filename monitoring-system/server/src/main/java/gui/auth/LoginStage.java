package gui.auth;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.function.Consumer;

public class LoginStage extends Stage {
    public LoginStage(Consumer<String> onLogin) {
        setTitle("🔐 Вход в систему");

        // Заголовок
        Label title = new Label("Добро пожаловать");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2b6cb0;");

        // Поля
        TextField userField = new TextField();
        userField.setPromptText("Имя пользователя");
        userField.setStyle(fieldStyle());

        PasswordField passField = new PasswordField();
        passField.setPromptText("Пароль");
        passField.setStyle(fieldStyle());

        Button loginBtn = new Button("Войти");
        loginBtn.setStyle(buttonStyle());
        loginBtn.setDefaultButton(true);

        Label feedback = new Label();
        feedback.setStyle("-fx-text-fill: red; -fx-font-size: 12px;");

        loginBtn.setOnAction(e -> {
            String username = userField.getText();
            String password = passField.getText();

            String role = AuthService.authenticate(username, password);
            if (role != null) {
                onLogin.accept(role);
                this.close();
            } else {
                feedback.setText("❌ Неверный логин или пароль");
            }
        });

        // Аватар / иконка входа
        Label avatar = new Label("🧠");
        avatar.setStyle("-fx-font-size: 36px;");

        VBox fields = new VBox(10,
                new Label("Логин:"), userField,
                new Label("Пароль:"), passField,
                feedback,
                loginBtn
        );

        fields.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(15, avatar, title, fields);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.CENTER);
        content.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #f0f4ff, #e6f0ff);
            -fx-border-radius: 12;
            -fx-background-radius: 12;
            -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 4);
        """);

        Scene scene = new Scene(content, 340, 380);
        setScene(scene);

        // Анимация плавного появления
        FadeTransition fade = new FadeTransition(Duration.millis(500), content);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private String fieldStyle() {
        return """
            -fx-background-color: white;
            -fx-border-color: #cbd5e0;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-padding: 6;
            -fx-font-size: 13px;
        """;
    }

    private String buttonStyle() {
        return """
            -fx-background-color: #4299e1;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-font-size: 14px;
            -fx-background-radius: 8;
            -fx-padding: 8 16;
        """;
    }
}
