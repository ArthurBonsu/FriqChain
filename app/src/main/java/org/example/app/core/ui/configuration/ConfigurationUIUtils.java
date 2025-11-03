package org.example.app.core.ui.configuration;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.scene.shape.Circle;


/**
 * UI utility methods for configuration interface
 */
public class ConfigurationUIUtils {
    
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###");
    private static final String PRIMARY_COLOR = "#00ff88";
    private static final String SECONDARY_COLOR = "#40e0d0";
    private static final String WARNING_COLOR = "#ffa500";
    private static final String ERROR_COLOR = "#ff6b6b";
    private static final String BACKGROUND_COLOR = "#16213e";
    private static final String CARD_COLOR = "#22334e";
    
    /**
     * Create a styled section header
     */
    public static Label createSectionHeader(String text) {
        Label header = new Label(text);
        header.setFont(Font.font("System", FontWeight.BOLD, 16));
        header.setTextFill(Color.web("#ffffff"));
        header.setPadding(new Insets(10, 0, 10, 0));
        return header;
    }
    
    /**
     * Create a configuration card
     */
    public static VBox createConfigCard(String title, String description, Region content) {
        VBox card = new VBox(10);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 10; -fx-padding: 20;",
            CARD_COLOR
        ));
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web(PRIMARY_COLOR));
        
        Label descLabel = new Label(description);
        descLabel.setTextFill(Color.web("#cccccc"));
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px;");
        
        card.getChildren().addAll(titleLabel, descLabel);
        
        if (content != null) {
            card.getChildren().add(content);
        }
        
        return card;
    }
    
    /**
     * Create a configuration field with label
     */
    public static HBox createConfigField(String label, Control control) {
        HBox field = new HBox(15);
        field.setAlignment(Pos.CENTER_LEFT);
        
        Label fieldLabel = new Label(label + ":");
        fieldLabel.setTextFill(Color.web("#ffffff"));
        fieldLabel.setMinWidth(200);
        fieldLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        control.setMaxWidth(300);
        
        field.getChildren().addAll(fieldLabel, control);
        return field;
    }
    
    /**
     * Create a styled text field
     */
    public static TextField createStyledTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-border-color: %s; -fx-border-radius: 6; -fx-padding: 8;",
            BACKGROUND_COLOR, SECONDARY_COLOR
        ));
        return field;
    }
    
    /**
     * Create a styled number field
     */
    public static TextField createNumberField(String prompt, boolean allowDecimal) {
        TextField field = createStyledTextField(prompt);
        
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getText();
            if (text.matches(allowDecimal ? "[0-9.]*" : "[0-9]*")) {
                return change;
            }
            return null;
        };
        
        field.setTextFormatter(new TextFormatter<>(filter));
        return field;
    }
    
    /**
     * Create a styled combo box
     */
    public static <T> ComboBox<T> createStyledComboBox() {
        ComboBox<T> combo = new ComboBox<>();
        combo.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-border-color: %s; -fx-border-radius: 6;",
            BACKGROUND_COLOR, SECONDARY_COLOR
        ));
        return combo;
    }
    
    /**
     * Create a styled checkbox
     */
    public static CheckBox createStyledCheckBox(String text) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setTextFill(Color.web("#ffffff"));
        checkBox.setStyle("-fx-font-size: 13px;");
        return checkBox;
    }
    
    /**
     * Create a styled button
     */
    public static Button createStyledButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; " +
            "-fx-font-weight: bold; -fx-padding: 10 20; " +
            "-fx-background-radius: 8; -fx-font-size: 13px;",
            color, color.equals(PRIMARY_COLOR) ? "black" : "white"
        ));
        
        // Add hover effect
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "; -fx-opacity: 0.8;");
        });
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("; -fx-opacity: 0.8;", ""));
        });
        
        return button;
    }
    
    /**
     * Create a primary button
     */
    public static Button createPrimaryButton(String text) {
        return createStyledButton(text, PRIMARY_COLOR);
    }
    
    /**
     * Create a secondary button
     */
    public static Button createSecondaryButton(String text) {
        return createStyledButton(text, SECONDARY_COLOR);
    }
    
    /**
     * Create a warning button
     */
    public static Button createWarningButton(String text) {
        return createStyledButton(text, WARNING_COLOR);
    }
    
    /**
     * Create a danger button
     */
    public static Button createDangerButton(String text) {
        return createStyledButton(text, ERROR_COLOR);
    }
    
    /**
     * Create a slider with label showing current value
     */
    public static VBox createSliderWithLabel(String label, double min, double max, 
                                            double initial, String unit) {
        VBox container = new VBox(5);
        
        Label titleLabel = new Label(label);
        titleLabel.setTextFill(Color.web("#ffffff"));
        titleLabel.setStyle("-fx-font-weight: bold;");
        
        Slider slider = new Slider(min, max, initial);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setBlockIncrement((max - min) / 10);
        
        Label valueLabel = new Label(String.format("%.0f %s", initial, unit));
        valueLabel.setTextFill(Color.web(PRIMARY_COLOR));
        
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            valueLabel.setText(String.format("%.0f %s", newVal.doubleValue(), unit));
        });
        
        container.getChildren().addAll(titleLabel, slider, valueLabel);
        return container;
    }
    
    /**
     * Show confirmation dialog
     */
    public static boolean showConfirmDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    /**
     * Show error dialog
     */
    public static void showErrorDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show info dialog
     */
    public static void showInfoDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Show warning dialog
     */
    public static void showWarningDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Create a status indicator
     */
    public static HBox createStatusIndicator(String label, boolean isActive) {
        HBox indicator = new HBox(10);
        indicator.setAlignment(Pos.CENTER_LEFT);
        
        Label statusLabel = new Label(label);
        statusLabel.setTextFill(Color.web("#ffffff"));
        
        Circle statusCircle = new Circle(5);
        statusCircle.setFill(Color.web(isActive ? PRIMARY_COLOR : ERROR_COLOR));
        
        Label statusText = new Label(isActive ? "Active" : "Inactive");
        statusText.setTextFill(Color.web(isActive ? PRIMARY_COLOR : ERROR_COLOR));
        
        indicator.getChildren().addAll(statusLabel, statusCircle, statusText);
        return indicator;
    }
    
    /**
     * Create a progress indicator with label
     */
    public static VBox createProgressWithLabel(String label) {
        VBox container = new VBox(10);
        container.setAlignment(Pos.CENTER);
        
        ProgressIndicator progress = new ProgressIndicator();
        progress.setStyle("-fx-progress-color: " + PRIMARY_COLOR);
        
        Label progressLabel = new Label(label);
        progressLabel.setTextFill(Color.web("#ffffff"));
        
        container.getChildren().addAll(progress, progressLabel);
        return container;
    }
    
    /**
     * Format large numbers for display
     */
    public static String formatNumber(long number) {
        return DECIMAL_FORMAT.format(number);
    }
    
    /**
     * Format file size
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * Format duration
     */
    public static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Create a tooltip with styled appearance
     */
    public static Tooltip createStyledTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setStyle("-fx-background-color: " + CARD_COLOR + "; " +
                        "-fx-text-fill: white; -fx-font-size: 12px;");
        return tooltip;
    }
    
    /**
     * Add validation styling to a control
     */
    public static void setValidationStyle(Control control, boolean isValid) {
        if (isValid) {
            control.setStyle(control.getStyle().replace(ERROR_COLOR, PRIMARY_COLOR));
        } else {
            control.setStyle(control.getStyle().replace(PRIMARY_COLOR, ERROR_COLOR));
        }
    }
}