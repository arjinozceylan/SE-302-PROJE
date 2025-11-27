package scheduler.ui;

import javafx.animation.FillTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

// --- Imports for Backend Logic & Models ---
import scheduler.model.*;
import scheduler.io.CsvDataLoader;
import scheduler.core.ExamScheduler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MainApp extends Application {

    // --- THEME CONSTANTS ---
    private static final String DARK_BG = "#1E1E1E";
    private static final String DARK_PANEL = "#252526";
    private static final String DARK_BTN = "#3A3D41";
    private static final String DARK_BORDER = "#444444";
    private static final String DARK_TEXT = "#FFFFFF";
    private static final String DARK_PROMPT = "#AAAAAA";

    private static final String LIGHT_BG = "#F3F3F3";
    private static final String LIGHT_PANEL = "#FFFFFF";
    private static final String LIGHT_BTN = "#E1E1E1";
    private static final String LIGHT_BORDER = "#CCCCCC";
    private static final String LIGHT_TEXT = "#333333";
    private static final String LIGHT_PROMPT = "#666666";
    private static final String ACCENT_COLOR = "#0E639C";

    // State
    private boolean isDarkMode = true;

    // --- DATA HOLDERS ---
    private List<Student> allStudents = new ArrayList<>();
    private List<Course> allCourses = new ArrayList<>();
    private List<Classroom> allClassrooms = new ArrayList<>();
    private List<Enrollment> allEnrollments = new ArrayList<>();

    // Map: StudentID -> List of Scheduled Exams (Result from ExamScheduler)
    private Map<String, List<StudentExam>> studentScheduleMap = new HashMap<>();

    // UI Table Data Sources
    private ObservableList<Student> studentObservableList = FXCollections.observableArrayList();
    private ObservableList<Course> examObservableList = FXCollections.observableArrayList();

    // UI Components
    private BorderPane root;
    private HBox topMenu, bottomBar;
    private VBox leftPane;
    private Label lblErrorCount, lblSectionTitle, lblDate, lblBlock, lblTime, lblUploaded, lblStats;
    private ListView<String> uploadedFilesList;
    private Button btnHelp, btnImport, btnExport;
    private TextField txtSearch, txtBlockStart, txtBlockEnd, txtTimeStart, txtTimeEnd;
    private DatePicker startDate, endDate;
    private ToggleButton tglStudents, tglExams, tglDays;
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
        // Filter student list on typing
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> filterStudentList(newVal));

        // View Toggles
        HBox filters = new HBox(5);
        tglStudents = createStyledToggleButton("Students");
        tglExams = createStyledToggleButton("Exams");
        tglDays = createStyledToggleButton("Days");

        ToggleGroup group = new ToggleGroup();
        tglStudents.setToggleGroup(group);
        tglExams.setToggleGroup(group);
        tglDays.setToggleGroup(group);
        tglStudents.setSelected(true);

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

        // Theme Switch (Right Aligned)
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        themeSwitch = new ToggleSwitch(true);
        themeSwitch.switchOnProperty().addListener((obs, oldVal, newVal) -> {
            isDarkMode = newVal;
            applyTheme();
        });

        topMenu.getChildren().addAll(btnHelp, lblErrorCount, btnImport, btnExport, txtSearch, filters, spacer,
                themeSwitch);

        // --- 2. LEFT SIDEBAR (FILTERS) ---
        leftPane = new VBox(15);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(260);

        lblSectionTitle = new Label("Filter Options");
        lblSectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        VBox dateBox = new VBox(5);
        lblDate = new Label("Date Range:");
        startDate = new DatePicker();
        startDate.setPromptText("Start Date");
        startDate.setMaxWidth(Double.MAX_VALUE);
        endDate = new DatePicker();
        endDate.setPromptText("End Date");
        endDate.setMaxWidth(Double.MAX_VALUE);
        dateBox.getChildren().addAll(lblDate, startDate, endDate);

        VBox blockBox = new VBox(5);
        lblBlock = new Label("Block Range:");
        HBox blockInputs = new HBox(5);
        txtBlockStart = createStyledTextField("Min");
        txtBlockEnd = createStyledTextField("Max");
        blockInputs.getChildren().addAll(txtBlockStart, txtBlockEnd);
        blockBox.getChildren().addAll(lblBlock, blockInputs);

        VBox timeBox = new VBox(5);
        lblTime = new Label("Time Range:");
        HBox timeInputs = new HBox(5);
        txtTimeStart = createStyledTextField("09:00");
        txtTimeEnd = createStyledTextField("17:00");
        timeInputs.getChildren().addAll(txtTimeStart, txtTimeEnd);
        timeBox.getChildren().addAll(lblTime, timeInputs);

        // Uploaded File List
        Separator sepFiles = new Separator();
        lblUploaded = new Label("Uploaded Files:");
        lblUploaded.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        uploadedFilesList = new ListView<>();
        uploadedFilesList.setPrefHeight(200);
        uploadedFilesList.setPlaceholder(new Label("No files loaded"));

        // Custom Cell Factory (Handles Text Wrapping & Theme)
        uploadedFilesList.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;
                String textColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
                setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + textColor + ";");

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    // --- Text Wrapping Logic ---
                    Label label = new Label(item);
                    label.setTextFill(Color.web(textColor));
                    label.setWrapText(true); // Allow multiline
                    label.setMaxWidth(160); // Constrain width

                    HBox.setHgrow(label, Priority.ALWAYS);

                    // Status Icon
                    Label icon = new Label("\u2713");
                    icon.setTextFill(Color.LIGHTGREEN);

                    // Remove Button
                    Button btnRemove = new Button("X");
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-font-weight: bold; -fx-background-color: transparent;");
                    btnRemove.setOnAction(event -> uploadedFilesList.getItems().remove(item));

                    box.getChildren().addAll(icon, label, btnRemove);
                    setGraphic(box);
                }
            }
        });

        leftPane.getChildren().addAll(lblSectionTitle, new Separator(), dateBox, new Separator(), blockBox,
                new Separator(), timeBox, sepFiles, lblUploaded, uploadedFilesList);

        // --- 3. BOTTOM BAR (STATS) ---
        bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        lblStats = new Label("Total Exams: 0 | Total Students: 0 | Total Classes: 0");
        lblStats.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        bottomBar.getChildren().add(lblStats);

        root.setTop(topMenu);
        root.setLeft(leftPane);
        root.setBottom(bottomBar);

        applyTheme();
        showStudentList(); // Default View

        Scene scene = new Scene(root, 1100, 750);
        primaryStage.setTitle("MainApp - Exam Management System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // =============================================================
    // FILE PROCESSING (Detects types based on filename)
    // =============================================================

    private void processAndLoadFiles(List<File> files) {
        int newStudents = 0;
        int newCourses = 0;

        for (File file : files) {
            String name = file.getName().toLowerCase();
            try {
                // 1. STUDENTS
                if (name.contains("allstudents") || name.contains("std_id")) {
                    List<Student> loaded = CsvDataLoader.loadStudents(file.toPath());
                    allStudents.addAll(loaded);
                    newStudents += loaded.size();
                    uploadedFilesList.getItems().add(file.getName() + "\n(Students: " + loaded.size() + ")");
                }
                // 2. COURSES
                else if (name.contains("allcourses")) {
                    List<Course> loaded = CsvDataLoader.loadCourses(file.toPath());
                    allCourses.addAll(loaded);
                    newCourses += loaded.size();
                    uploadedFilesList.getItems().add(file.getName() + "\n(Courses: " + loaded.size() + ")");
                }
                // 3. CLASSROOMS
                else if (name.contains("allclassrooms") || name.contains("capacities")) {
                    List<Classroom> loaded = CsvDataLoader.loadClassrooms(file.toPath());
                    allClassrooms.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Rooms: " + loaded.size() + ")");
                }
                // 4. ATTENDANCE / ENROLLMENTS
                else if (name.contains("allattendancelists") || name.contains("attendance")) {
                    List<Enrollment> loaded = CsvDataLoader.loadEnrollments(file.toPath());
                    allEnrollments.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Links: " + loaded.size() + ")");
                } else {
                    uploadedFilesList.getItems().add(file.getName() + " [Unknown]");
                }
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Error loading " + file.getName() + ":\n" + e.getMessage());
                styleDialog(alert);
                alert.show();
                e.printStackTrace();
            }
        }

        // Update UI Tables
        studentObservableList.setAll(allStudents);
        examObservableList.setAll(allCourses);
        updateStats();

        // Check if ready to run Scheduler
        if (!allStudents.isEmpty() && !allCourses.isEmpty() && !allClassrooms.isEmpty() && !allEnrollments.isEmpty()) {
            runSchedulerLogic();
        }
    }

    // =============================================================
    // SCHEDULER LOGIC (Integration Point)
    // =============================================================
    private void runSchedulerLogic() {
        System.out.println("UI: Calling backend scheduler...");

        // 1. Instantiate the Backend Logic Class
        ExamScheduler scheduler = new ExamScheduler();

        // 2. Execute Algorithm
        // Returns: Map<StudentID, List<StudentExam>>
        studentScheduleMap = scheduler.run(allStudents, allCourses, allEnrollments, allClassrooms);

        // 3. Update UI Stats
        Platform.runLater(() -> {
            int totalScheduledExams = studentScheduleMap.values().stream().mapToInt(List::size).sum();
            lblStats.setText(String.format("Scheduled: %d total exam entries | %d students assigned",
                    totalScheduledExams, studentScheduleMap.size()));

            // Refresh table to show data if currently viewing students
            if (tglStudents.isSelected())
                showStudentList();
        });
    }

    private void updateStats() {
        lblStats.setText(String.format("Total Exams: %d | Total Students: %d | Total Classes: %d",
                allCourses.size(), allStudents.size(), allClassrooms.size()));
    }

    private void filterStudentList(String query) {
        if (query == null || query.isEmpty()) {
            studentObservableList.setAll(allStudents);
        } else {
            String lower = query.toLowerCase();
            List<Student> filtered = allStudents.stream()
                    .filter(s -> s.getId().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            studentObservableList.setAll(filtered);
        }
    }

    // =============================================================
    // CENTER VIEWS (Tables)
    // =============================================================

    private void showStudentList() {
        TableView<Student> table = new TableView<>();
        table.setPlaceholder(new Label("No students data loaded."));
        styleTableView(table);

        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        table.getColumns().add(colId);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(studentObservableList);

        // Click Listener -> Show Detail
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                showStudentScheduleDetail(newVal);
        });

        root.setCenter(table);
    }

    private void showStudentScheduleDetail(Student student) {
        VBox detailView = new VBox(10);
        detailView.setPadding(new Insets(20));
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        detailView.setStyle("-fx-background-color: " + bg + ";");

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnBack = createStyledButton("\u2190 Back List");
        btnBack.setOnAction(e -> showStudentList());

        Label lblTitle = new Label("Exam Schedule: " + student.getId());
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));
        header.getChildren().addAll(btnBack, lblTitle);

        TableView<StudentExam> detailTable = new TableView<>();
        styleTableView(detailTable);
        detailTable.setPlaceholder(new Label("No exams scheduled for this student."));

        TableColumn<StudentExam, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseId"));

        TableColumn<StudentExam, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getDate().toString()));

        TableColumn<StudentExam, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getStart().toString()));

        TableColumn<StudentExam, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("classroomId"));

        TableColumn<StudentExam, String> colSeat = new TableColumn<>("Seat");
        colSeat.setCellValueFactory(new PropertyValueFactory<>("seatNo"));

        detailTable.getColumns().addAll(colCourse, colDate, colTime, colRoom, colSeat);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Get data from Results Map
        List<StudentExam> exams = studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());
        detailTable.setItems(FXCollections.observableArrayList(exams));

        detailView.getChildren().addAll(header, new Separator(), detailTable);
        root.setCenter(detailView);
    }

    private void showExamList() {
        TableView<Course> table = new TableView<>();
        table.setPlaceholder(new Label("No courses loaded."));
        styleTableView(table);

        TableColumn<Course, String> colName = new TableColumn<>("Course Code");
        colName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        TableColumn<Course, String> colDur = new TableColumn<>("Duration (min)");
        colDur.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));

        table.getColumns().addAll(colName, colDur);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(examObservableList);
        root.setCenter(table);
    }

    private void showDayList() {
        TableView<String> table = new TableView<>();
        table.setPlaceholder(new Label("Day view not implemented yet."));
        styleTableView(table);
        root.setCenter(table);
    }

    // =============================================================
    // DIALOGS & THEME ENGINE
    // =============================================================

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

        Label lblInstruction = new Label("Drag and drop CSV files here");
        lblInstruction.setTextFill(Color.web(text));
        lblInstruction.setStyle("-fx-text-alignment: center;");

        Button btnBrowse = new Button("Browse Files");
        btnBrowse.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");
        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null)
                processAndLoadFiles(files);
        });

        dropZone.getChildren().addAll(lblInstruction, new Label("- or -"), btnBrowse);

        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                processAndLoadFiles(db.getFiles());
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

    private void applyTheme() {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String border = isDarkMode ? DARK_BORDER : LIGHT_BORDER;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btn = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String prompt = isDarkMode ? DARK_PROMPT : LIGHT_PROMPT;

        root.setStyle("-fx-background-color: " + bg + ";");
        topMenu.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1 0;");
        leftPane.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 1 0 0;");
        bottomBar.setStyle(
                "-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 1 0 0 0;");

        Color textColor = Color.web(text);
        lblSectionTitle.setTextFill(textColor);
        lblDate.setTextFill(textColor);
        lblBlock.setTextFill(textColor);
        lblTime.setTextFill(textColor);
        lblUploaded.setTextFill(textColor);
        lblStats.setTextFill(textColor);

        String btnStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-background-radius: 4;";
        btnHelp.setStyle(btnStyle);
        btnImport.setStyle(btnStyle);
        btnExport.setStyle(btnStyle);

        String inputStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-prompt-text-fill: "
                + prompt + ";";
        txtSearch.setStyle(inputStyle);
        txtBlockStart.setStyle(inputStyle);
        txtBlockEnd.setStyle(inputStyle);
        txtTimeStart.setStyle(inputStyle);
        txtTimeEnd.setStyle(inputStyle);

        styleDatePicker(startDate, btn, text, prompt);
        styleDatePicker(endDate, btn, text, prompt);

        uploadedFilesList.setStyle("-fx-background-color: " + btn + "; -fx-control-inner-background: " + btn + ";");
        uploadedFilesList.refresh();
        updateToggleStyles();
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

    private Button createStyledButton(String text) {
        return new Button(text);
    }

    private TextField createStyledTextField(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        return t;
    }

    private ToggleButton createStyledToggleButton(String text) {
        return new ToggleButton(text);
    }

    private void styleTableView(TableView<?> table) {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String border = isDarkMode ? DARK_BORDER : LIGHT_BORDER;
        table.setStyle("-fx-background-color: " + bg + "; -fx-control-inner-background: " + bg + "; -fx-base: " + bg
                + "; -fx-table-cell-border-color: " + border + "; -fx-table-header-border-color: " + border + ";");
        if (table.getPlaceholder() != null)
            ((Label) table.getPlaceholder()).setTextFill(Color.GRAY);
    }

    private void styleDialog(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        dialogPane.setStyle("-fx-background-color: " + panel + ";");
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: " + text + ";");
    }

    // ToggleSwitch Class
    private static class ToggleSwitch extends StackPane {
        private final Rectangle background;
        private final Circle trigger;
        private final BooleanProperty switchedOn = new SimpleBooleanProperty(false);
        private final TranslateTransition translateAnimation = new TranslateTransition(Duration.seconds(0.25));
        private final FillTransition fillAnimation = new FillTransition(Duration.seconds(0.25));
        private final ParallelTransition animation = new ParallelTransition(translateAnimation, fillAnimation);

        public ToggleSwitch(boolean initialValue) {
            switchedOn.set(initialValue);
            double width = 50, height = 28, radius = 12;
            background = new Rectangle(width, height);
            background.setArcWidth(height);
            background.setArcHeight(height);
            background.setFill(Color.WHITE);
            background.setStroke(Color.LIGHTGRAY);
            trigger = new Circle(radius);
            trigger.setFill(Color.WHITE);
            trigger.setEffect(new DropShadow(2, Color.gray(0.2)));
            getChildren().addAll(background, trigger);

            if (initialValue) {
                trigger.setTranslateX(width / 2 - radius - 2);
                background.setFill(Color.web("#4CD964"));
                background.setStroke(Color.web("#4CD964"));
            } else {
                trigger.setTranslateX(-(width / 2 - radius - 2));
                background.setFill(Color.web("#E9E9EA"));
                background.setStroke(Color.web("#E9E9EA"));
            }

            setOnMouseClicked(event -> switchedOn.set(!switchedOn.get()));
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

    public static void main(String[] args) {
        launch(args);
    }
}