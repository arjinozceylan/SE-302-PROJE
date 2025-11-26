package scheduler.ui;

import javafx.animation.FillTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class MainApp extends Application {

    // --- THEME CONSTANTS ---
    // Dark Mode Colors
    private static final String DARK_BG = "#1E1E1E";
    private static final String DARK_PANEL = "#252526";
    private static final String DARK_BTN = "#3A3D41";
    private static final String DARK_BORDER = "#444444";
    private static final String DARK_TEXT = "#FFFFFF";
    private static final String DARK_PROMPT = "#AAAAAA";

    // Light Mode Colors
    private static final String LIGHT_BG = "#F3F3F3";
    private static final String LIGHT_PANEL = "#FFFFFF";
    private static final String LIGHT_BTN = "#E1E1E1";
    private static final String LIGHT_BORDER = "#CCCCCC";
    private static final String LIGHT_TEXT = "#333333";
    private static final String LIGHT_PROMPT = "#666666";

    private static final String ACCENT_COLOR = "#0E639C";

    // State
    private boolean isDarkMode = true;

    // UI Components
    private BorderPane root;
    private HBox topMenu;
    private VBox leftPane;
    private HBox bottomBar;
    private Label lblErrorCount;
    private Label lblSectionTitle;
    private Label lblDate, lblBlock, lblTime, lblUploaded, lblStats;
    private ListView<String> uploadedFilesList;
    private Button btnHelp, btnImport, btnExport;
    private TextField txtSearch, txtBlockStart, txtBlockEnd, txtTimeStart, txtTimeEnd;
    private DatePicker startDate, endDate;
    private ToggleButton tglStudents, tglExams, tglDays;

    // Toggle Switch Reference
    private ToggleSwitch themeSwitch;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();

        // --- 1. HEADER / TOOLBAR ---
        topMenu = new HBox(15);
        topMenu.setPadding(new Insets(10));
        topMenu.setAlignment(Pos.CENTER_LEFT);

        btnHelp = createStyledButton("?");

        lblErrorCount = new Label("Errors: 0");
        lblErrorCount.setTextFill(Color.WHITE);
        lblErrorCount.setStyle(
                "-fx-background-color: #D11212; -fx-padding: 3 8 3 8; -fx-background-radius: 10; -fx-font-weight: bold;");

        btnImport = createStyledButton("Import \u2193");
        btnImport.setOnAction(e -> showImportDialog(primaryStage));

        btnExport = createStyledButton("Export \u2191");
        btnExport.setOnAction(e -> showExportDialog(primaryStage));

        txtSearch = createStyledTextField("Search...");
        txtSearch.setPrefWidth(200);

        // -- FILTER BUTTONS --
        HBox filters = new HBox(5);
        tglStudents = createStyledToggleButton("Students");
        tglExams = createStyledToggleButton("Exams");
        tglDays = createStyledToggleButton("Days");

        ToggleGroup group = new ToggleGroup();
        tglStudents.setToggleGroup(group);
        tglExams.setToggleGroup(group);
        tglDays.setToggleGroup(group);
        tglStudents.setSelected(true);

        // Filter Actions
        tglStudents.setOnAction(e -> {
            if (tglStudents.isSelected())
                showStudentList();
            updateToggleStyles();
        });
        tglExams.setOnAction(e -> {
            if (tglExams.isSelected())
                showExamList();
            updateToggleStyles();
        });
        tglDays.setOnAction(e -> {
            if (tglDays.isSelected())
                showDayList();
            updateToggleStyles();
        });

        filters.getChildren().addAll(tglStudents, tglExams, tglDays);

        // -- THEME TOGGLE --
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeSwitch = new ToggleSwitch(true); // Start in Dark Mode
        themeSwitch.switchOnProperty().addListener((obs, oldVal, newVal) -> {
            isDarkMode = newVal;
            applyTheme(); // Refresh all styles
        });

        topMenu.getChildren().addAll(btnHelp, lblErrorCount, btnImport, btnExport, txtSearch, filters, spacer,
                themeSwitch);

        // --- 2. LEFT SIDEBAR ---
        leftPane = new VBox(15);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(260);

        lblSectionTitle = new Label("Filter Options");
        lblSectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // Date Range
        VBox dateBox = new VBox(5);
        lblDate = new Label("Date Range:");
        dateBox.getChildren().add(lblDate);

        startDate = new DatePicker();
        startDate.setPromptText("Start Date");
        startDate.setMaxWidth(Double.MAX_VALUE);
        endDate = new DatePicker();
        endDate.setPromptText("End Date");
        endDate.setMaxWidth(Double.MAX_VALUE);

        dateBox.getChildren().addAll(startDate, endDate);

        // Block Range
        VBox blockBox = new VBox(5);
        lblBlock = new Label("Block Range:");
        blockBox.getChildren().add(lblBlock);
        HBox blockInputs = new HBox(5);
        txtBlockStart = createStyledTextField("Min");
        txtBlockEnd = createStyledTextField("Max");
        blockInputs.getChildren().addAll(txtBlockStart, txtBlockEnd);
        blockBox.getChildren().add(blockInputs);

        // Time Range
        VBox timeBox = new VBox(5);
        lblTime = new Label("Time Range:");
        timeBox.getChildren().add(lblTime);
        HBox timeInputs = new HBox(5);
        txtTimeStart = createStyledTextField("09:00");
        txtTimeEnd = createStyledTextField("17:00");
        timeInputs.getChildren().addAll(txtTimeStart, txtTimeEnd);
        timeBox.getChildren().add(timeInputs);

        // --- FILE LIST ---
        Separator sepFiles = new Separator();
        lblUploaded = new Label("Uploaded Files:");
        lblUploaded.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        uploadedFilesList = new ListView<>();
        uploadedFilesList.setPrefHeight(200);
        uploadedFilesList.setPlaceholder(new Label("No files loaded"));

        // Custom Cell Factory
        uploadedFilesList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                // Apply current theme style to cell
                String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;
                String textColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;

                setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + textColor + ";");

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);
                    Label label = new Label(item);
                    label.setTextFill(Color.web(textColor));
                    HBox.setHgrow(label, Priority.ALWAYS);

                    Button btnRemove = new Button("X");
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-font-weight: bold; -fx-background-color: transparent; -fx-border-color: #999; -fx-border-radius: 3;");

                    btnRemove.setOnAction(event -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Remove File");
                        alert.setHeaderText("Remove " + item + "?");
                        alert.setContentText("Are you sure you want to remove this file?");
                        styleDialog(alert); // Style the alert

                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            uploadedFilesList.getItems().remove(item);
                        }
                    });

                    box.getChildren().addAll(label, btnRemove);
                    setGraphic(box);
                }
            }
        });

        leftPane.getChildren().addAll(lblSectionTitle, new Separator(), dateBox, new Separator(), blockBox,
                new Separator(), timeBox, sepFiles, lblUploaded, uploadedFilesList);

        // --- 3. BOTTOM BAR ---
        bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        lblStats = new Label("Total Exams: 0 | Total Students: 0 | Total Classes: 0");
        lblStats.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        bottomBar.getChildren().add(lblStats);

        root.setTop(topMenu);
        root.setLeft(leftPane);
        root.setBottom(bottomBar);

        // --- INIT THEME & SCREEN ---
        applyTheme(); // Applies initial colors
        showStudentList();

        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setTitle("MainApp - Exam Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // THEME ENGINE
    private void applyTheme() {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String border = isDarkMode ? DARK_BORDER : LIGHT_BORDER;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btn = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String prompt = isDarkMode ? DARK_PROMPT : LIGHT_PROMPT;

        // Root & Panels
        root.setStyle("-fx-background-color: " + bg + ";");
        topMenu.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1 0;");
        leftPane.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 1 0 0;");
        bottomBar.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 1 0 0 0;");

        // Labels
        Color textColor = Color.web(text);
        lblSectionTitle.setTextFill(textColor);
        lblDate.setTextFill(textColor);
        lblBlock.setTextFill(textColor);
        lblTime.setTextFill(textColor);
        lblUploaded.setTextFill(textColor);
        lblStats.setTextFill(textColor);

        // Buttons
        String btnStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-background-radius: 4;";
        btnHelp.setStyle(btnStyle);
        btnImport.setStyle(btnStyle);
        btnExport.setStyle(btnStyle);

        // Inputs
        String inputStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-prompt-text-fill: "
                + prompt + ";";
        txtSearch.setStyle(inputStyle);
        txtBlockStart.setStyle(inputStyle);
        txtBlockEnd.setStyle(inputStyle);
        txtTimeStart.setStyle(inputStyle);
        txtTimeEnd.setStyle(inputStyle);

        // DatePickers
        styleDatePicker(startDate, btn, text, prompt);
        styleDatePicker(endDate, btn, text, prompt);

        // List
        uploadedFilesList.setStyle("-fx-background-color: " + btn + "; -fx-control-inner-background: " + btn + ";");
        uploadedFilesList.refresh(); // Triggers cell update for text color

        // Toggles
        updateToggleStyles();

        // Refresh Center View if present
        if (tglStudents.isSelected())
            showStudentList();
        else if (tglExams.isSelected())
            showExamList();
        else if (tglDays.isSelected())
            showDayList();
    }

    private void styleDatePicker(DatePicker dp, String bg, String text, String prompt) {
        dp.setStyle("-fx-control-inner-background: " + bg + "; -fx-background-color: " + bg + ";");
        dp.getEditor().setStyle(
                "-fx-background-color: " + bg + "; -fx-text-fill: " + text + "; -fx-prompt-text-fill: " + prompt + ";");
    }

    private void updateToggleStyles() {
        String border = isDarkMode ? DARK_BORDER : LIGHT_BORDER;
        String btn = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;

        String baseStyle = "-fx-text-fill: " + text + "; -fx-background-radius: 4; -fx-border-color: " + border
                + "; -fx-border-radius: 4; ";

        tglStudents.setStyle(baseStyle + "-fx-background-color: " + (tglStudents.isSelected() ? ACCENT_COLOR : btn)
                + "; " + (tglStudents.isSelected() ? "-fx-text-fill: white;" : ""));
        tglExams.setStyle(baseStyle + "-fx-background-color: " + (tglExams.isSelected() ? ACCENT_COLOR : btn) + "; "
                + (tglExams.isSelected() ? "-fx-text-fill: white;" : ""));
        tglDays.setStyle(baseStyle + "-fx-background-color: " + (tglDays.isSelected() ? ACCENT_COLOR : btn) + "; "
                + (tglDays.isSelected() ? "-fx-text-fill: white;" : ""));
    }

    // --- HELPER FACTORIES ---
    private Button createStyledButton(String text) {
        return new Button(text); // Style handled in applyTheme
    }

    private TextField createStyledTextField(String prompt) {
        TextField txt = new TextField();
        txt.setPromptText(prompt);
        return txt;
    }

    private ToggleButton createStyledToggleButton(String text) {
        return new ToggleButton(text); // Style handled in updateToggleStyles
    }

    private void styleTableView(TableView<?> table) {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String border = isDarkMode ? DARK_BORDER : LIGHT_BORDER;

        table.setStyle(
                "-fx-background-color: " + bg + ";" +
                        "-fx-control-inner-background: " + bg + ";" +
                        "-fx-base: " + bg + ";" +
                        "-fx-table-cell-border-color: " + border + ";" +
                        "-fx-table-header-border-color: " + border + ";");
        Label placeholder = (Label) table.getPlaceholder();
        if (placeholder != null)
            placeholder.setTextFill(Color.GRAY);
    }

    private void styleDialog(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;

        dialogPane.setStyle("-fx-background-color: " + panel + ";");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: " + text + ";");
        // Header styling is tricky without CSS file, but this covers the basics
    }

    // CENTER VIEWS
    private void showStudentList() {
        TableView<Student> table = new TableView<>();
        table.setPlaceholder(new Label("No students found. Please import a file."));
        styleTableView(table);

        TableColumn<Student, String> colName = new TableColumn<>("Name Surname");
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));

        table.getColumns().addAll(colName, colId);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(FXCollections.observableArrayList());
        root.setCenter(table);
    }

    private void showExamList() {
        TableView<String> table = new TableView<>();
        table.setPlaceholder(new Label("No exams found. Please import a file."));
        styleTableView(table);
        TableColumn<String, String> col = new TableColumn<>("Exam Name");
        table.getColumns().add(col);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        root.setCenter(table);
    }

    private void showDayList() {
        TableView<String> table = new TableView<>();
        table.setPlaceholder(new Label("No schedule data found."));
        styleTableView(table);
        TableColumn<String, String> col = new TableColumn<>("Day");
        table.getColumns().add(col);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        root.setCenter(table);
    }

    // DIALOGS
    private void showImportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Import Files");

        VBox dropZone = new VBox(20);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(30));

        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;

        dropZone.setStyle(
                "-fx-border-color: #666; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: " + panel
                        + ";");

        Label lblInstruction = new Label("Drag and drop files here");
        lblInstruction.setTextFill(Color.web(text));

        Button btnBrowse = new Button("Browse Files");
        btnBrowse.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");

        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null)
                for (File file : files)
                    uploadedFilesList.getItems().add(file.getName());
        });

        dropZone.getChildren().addAll(lblInstruction, new Label("- or -"), btnBrowse);

        // Drag Drop Logic (Simplified)
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles())
                    uploadedFilesList.getItems().add(file.getName());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        Scene dialogScene = new Scene(dropZone, 400, 300);
        dialog.setScene(dialogScene);
        dialog.show();
    }

    private void showExportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Export");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        layout.setStyle("-fx-background-color: " + bg + ";");

        Label lblType = new Label("File Type / Source");
        lblType.setTextFill(Color.web(text));
        ComboBox<String> cmbType = new ComboBox<>(
                FXCollections.observableArrayList("Student List", "Exam Results", "Schedule"));

        Label lblName = new Label("File Name");
        lblName.setTextFill(Color.web(text));
        TextField txtName = new TextField();

        Button btnExport = new Button("Export");
        btnExport.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");

        layout.getChildren().addAll(lblType, cmbType, lblName, txtName, btnExport);

        Scene s = new Scene(layout, 300, 250);
        dialog.setScene(s);
        dialog.show();
    }

    // CUSTOM TOGGLE SWITCH
    private static class ToggleSwitch extends StackPane {
        private final Rectangle background;
        private final Circle trigger;
        private final BooleanProperty switchedOn = new SimpleBooleanProperty(false);

        private final TranslateTransition translateAnimation = new TranslateTransition(Duration.seconds(0.25));
        private final FillTransition fillAnimation = new FillTransition(Duration.seconds(0.25));
        private final ParallelTransition animation = new ParallelTransition(translateAnimation, fillAnimation);

        public ToggleSwitch(boolean initialValue) {
            switchedOn.set(initialValue);

            // Size settings
            double width = 50;
            double height = 28;
            double radius = 12;

            background = new Rectangle(width, height);
            background.setArcWidth(height);
            background.setArcHeight(height);
            background.setFill(Color.WHITE);
            background.setStroke(Color.LIGHTGRAY);

            trigger = new Circle(radius);
            trigger.setFill(Color.WHITE);
            trigger.setEffect(new DropShadow(2, Color.gray(0.2)));

            getChildren().addAll(background, trigger);

            // Initial State Logic
            if (initialValue) {
                trigger.setTranslateX(width / 2 - radius - 2);
                background.setFill(Color.web("#4CD964"));
                background.setStroke(Color.web("#4CD964"));
            } else {
                trigger.setTranslateX(-(width / 2 - radius - 2));
                background.setFill(Color.web("#E9E9EA"));
                background.setStroke(Color.web("#E9E9EA"));
            }

            // Click Event
            setOnMouseClicked(event -> {
                switchedOn.set(!switchedOn.get());
            });

            // Listener for animation
            switchedOn.addListener((obs, oldState, newState) -> {
                boolean isOn = newState;
                translateAnimation.setNode(trigger);
                translateAnimation.setToX(isOn ? width / 2 - radius - 2 : -(width / 2 - radius - 2));

                fillAnimation.setShape(background);
                fillAnimation.setToValue(isOn ? Color.web("#4CD964") : Color.web("#E9E9EA"));

                animation.play();
            });
        }

        public BooleanProperty switchOnProperty() {
            return switchedOn;
        }
    }

    // --- DUMMY DATA CLASSES ---
    public static class Student {
        private final SimpleStringProperty name;
        private final SimpleStringProperty id;

        public Student(String name, String id) {
            this.name = new SimpleStringProperty(name);
            this.id = new SimpleStringProperty(id);
        }

        public String getName() {
            return name.get();
        }

        public String getId() {
            return id.get();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}