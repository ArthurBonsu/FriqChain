package org.example.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Simple JavaFX test to verify JavaFX is working correctly
 */
public class JavaFXTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("JavaFX is working with TINC!");
        label.setStyle("-fx-font-size: 18px; -fx-text-fill: green;");
        
        VBox root = new VBox(label);
        root.setStyle("-fx-padding: 20; -fx-alignment: center;");
        
        Scene scene = new Scene(root, 400, 200);
        
        primaryStage.setTitle("TINC JavaFX Test");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}