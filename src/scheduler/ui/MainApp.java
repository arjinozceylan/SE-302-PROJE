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
import javafx.scene.Node;
import javafx.geometry.Orientation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import javafx.concurrent.Task;

// --- Imports for Backend Logic & Models ---
import scheduler.model.*;
import scheduler.io.CsvDataLoader;
import scheduler.core.ExamScheduler;
import scheduler.dao.DBManager;
import scheduler.export.ExportOtherTypes;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MainApp extends Application {

    // Tema Renkleri
    private Set<String> lastBottleneckStudents = new HashSet<>();
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

    // Tema durumu
    private boolean isDarkMode = true;

    // Veriler
    private List<Student> allStudents = new ArrayList<>();
    private List<Course> allCourses = new ArrayList<>();
    private List<Classroom> allClassrooms = new ArrayList<>();
    private List<Enrollment> allEnrollments = new ArrayList<>();

    private final List<File> loadedFileCache = new ArrayList<>();

    // Aktif Detay Ekranını Hatırlamak İçin
    private Object currentDetailItem = null;

    // Hata Kayıt Sistemi
    private final List<String> errorLog = new ArrayList<>();

    // Map: StudentID -> List of Scheduled Exams
    private Map<String, List<StudentExam>> studentScheduleMap = new HashMap<>();
    private Map<String, String> lastUnscheduledReasons = new HashMap<>();

    // UI Table Data Sources
    private ObservableList<Student> studentObservableList = FXCollections.observableArrayList();
    private ObservableList<Course> examObservableList = FXCollections.observableArrayList();

    // Master Lists (Arama yaparken veriyi kaybetmemek için yedeği tutuyoruz)
    private ObservableList<Student> masterStudentList = FXCollections.observableArrayList();
    private ObservableList<Course> masterExamList = FXCollections.observableArrayList();
    private ObservableList<DayRow> masterDayList = FXCollections.observableArrayList(); // BU SATIRI EKLE

    // UI Components

    private BorderPane root;
    private HBox topMenu, bottomBar;
    private VBox leftPane;
    private Label lblErrorCount, lblTime, lblStats,
            lblBlockTime;
    private StackPane mainStack; // Ana kapsayıcı (En dış katman)
    private VBox loadingOverlay; // Yükleniyor katmanı

    private ObservableList<UploadedFileItem> uploadedFilesData = FXCollections.observableArrayList();
    private ListView<UploadedFileItem> uploadedFilesList;

    // Gün Sayısı ve Sabit Süre
    private TextField txtDays, txtBlockTime;

    private Button btnHelp, btnImport, btnExport, btnApply, btnCustomize;
    private TextField txtSearch, txtTimeStart, txtTimeEnd;
    private DatePicker startDate, endDate;
    private ToggleButton tglStudents, tglExams, tglDays, tglClassrooms;
    private ToggleSwitch themeSwitch;
    private Stage primaryStage;

    // Kural Gruplarını Tutmak İçin Liste
    private final List<RuleGroupPane> ruleGroups = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        try {
            // 1. Veritabanını Başlat
            DBManager.initializeDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.primaryStage = primaryStage;
        root = new BorderPane();

        // 2. Arayüzü Kur
        setupUI();

        // 3. Sahneyi Göster
        mainStack = new StackPane(root, loadingOverlay);

        showStudentList(); // Tabloyu ilk başta boş göster

        Scene scene = new Scene(mainStack, 1200, 800);
        primaryStage.setTitle("MainApp - Exam Management System");
        primaryStage.setScene(scene);
        applyTheme();
        primaryStage.show();

        // A) Ayarları (Text kutularını) geri yükle
        loadSettingsFromDB();

        // B) Dosya Listesini (Sol menü) geri yükle
        loadSavedFilesList();

        // C) Filtre Kurallarını geri yükle
        restoreRulesFromDB();

        // D) Son Hesaplanan Takvimi ve Tabloları Yükle
        try {
            Map<String, List<StudentExam>> loadedSchedule = DBManager.loadSchedule();

            // Eğer veritabanında kayıtlı bir takvim varsa:
            if (!loadedSchedule.isEmpty()) {
                studentScheduleMap = loadedSchedule;

                // Öğrenci Listesini DB'den doldur
                List<Student> dbStudents = DBManager.loadStudentsFromDB();
                if (!dbStudents.isEmpty()) {
                    allStudents.clear();
                    allStudents.addAll(dbStudents);
                    masterStudentList.setAll(allStudents);
                    studentObservableList.setAll(allStudents);
                }

                // Ders Listesini DB'den doldur
                List<Course> dbCourses = DBManager.loadCoursesFromDB();
                if (!dbCourses.isEmpty()) {
                    allCourses.clear();
                    allCourses.addAll(dbCourses);
                    masterExamList.setAll(allCourses);
                    examObservableList.setAll(allCourses);
                }

                // Sınıfları doldur
                List<Classroom> dbRooms = DBManager.loadClassroomsFromDB();
                if (!dbRooms.isEmpty()) {
                    allClassrooms.clear();
                    allClassrooms.addAll(dbRooms);
                }

                buildMasterDayList();

                updateStats();
                System.out.println("Loaded previous state from Database.");
            }
        } catch (Exception e) {
            logError("Failed to load data from DB: " + e.getMessage());
        }
        refreshActiveView();
    }

    private void setupUI() {

        // --- 1. HEADER / TOOLBAR ---
        topMenu = new HBox(15);
        topMenu.setPadding(new Insets(10, 10, 5, 10));
        topMenu.setAlignment(Pos.CENTER_LEFT);

        HBox toolbarCard = new HBox(10);
        toolbarCard.setAlignment(Pos.CENTER_LEFT);
        toolbarCard.getStyleClass().add("top-card");
        HBox.setHgrow(toolbarCard, Priority.ALWAYS);
        toolbarCard.setMaxWidth(Double.MAX_VALUE);

        // Butonlar
        btnHelp = createStyledButton("?");
        btnHelp.setTooltip(new Tooltip("Help"));
        btnHelp.setOnAction(e -> showHelpDialog());

        lblErrorCount = new Label("0 Errors");
        lblErrorCount.setTextFill(Color.WHITE);
        lblErrorCount.setStyle(
                "-fx-background-color: #D11212; -fx-padding: 6 10 6 10; -fx-background-radius: 15; -fx-font-weight: bold; -fx-font-size: 12px;");
        lblErrorCount.setOnMouseClicked(e -> showErrorLogDialog());

        btnImport = createStyledButton("Import");
        btnImport.setOnAction(e -> showImportDialog(primaryStage));

        btnExport = createStyledButton("Export");
        btnExport.setOnAction(e -> showExportDialog(primaryStage));

        btnApply = createStyledButton("Apply Schedule");
        btnApply.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");
        btnApply.setOnAction(e -> runSchedulerLogic(true));

        // Arama
        txtSearch = createStyledTextField("Search...");
        txtSearch.setPrefWidth(200);
        txtSearch.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(txtSearch, Priority.ALWAYS);
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> performSearch(newVal));

        // Filtreler
        HBox filters = new HBox(0);
        tglStudents = createStyledToggleButton("Students");
        tglExams = createStyledToggleButton("Exams");
        tglDays = createStyledToggleButton("Days");
        tglClassrooms = createStyledToggleButton("Classrooms");

        tglStudents.setStyle("-fx-background-radius: 5 0 0 5; -fx-border-radius: 5 0 0 5; -fx-border-width: 1 0 1 1;");
        tglExams.setStyle("-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");
        tglDays.setStyle("-fx-background-radius: 0 5 5 0; -fx-border-radius: 0 5 5 0; -fx-border-width: 1 1 1 1;");

        ToggleGroup group = new ToggleGroup();

        tglStudents.setToggleGroup(group);
        tglExams.setToggleGroup(group);
        tglClassrooms.setToggleGroup(group);
        tglDays.setToggleGroup(group);
        tglStudents.setSelected(true);

        tglStudents.setOnAction(e -> {
            if (tglStudents.isSelected())
                performSearch(txtSearch.getText());
            updateToggleStyles();
        });
        tglExams.setOnAction(e -> {
            if (tglExams.isSelected())
                performSearch(txtSearch.getText());
            updateToggleStyles();
        });
        tglClassrooms.setOnAction(e -> {
            if (tglClassrooms.isSelected()) {
                performSearch(txtSearch.getText());
                updateToggleStyles();
            }
        });
        tglDays.setOnAction(e -> {
            if (tglDays.isSelected())
                performSearch(txtSearch.getText());
            updateToggleStyles();
        });

        filters.getChildren().addAll(tglStudents, tglExams, tglClassrooms, tglDays);

        themeSwitch = new ToggleSwitch(true);
        themeSwitch.switchOnProperty().addListener((obs, oldVal, newVal) -> {
            isDarkMode = newVal;
            applyTheme();
        });

        HBox leftGroup = new HBox(10);
        leftGroup.setAlignment(Pos.CENTER_LEFT);
        leftGroup.getChildren().addAll(btnHelp, lblErrorCount, new Separator(Orientation.VERTICAL), btnImport,
                btnExport, new Separator(Orientation.VERTICAL), btnApply);

        HBox rightGroup = new HBox(10);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);
        rightGroup.getChildren().addAll(filters, new Separator(Orientation.VERTICAL), themeSwitch);

        toolbarCard.getChildren().addAll(leftGroup, new Separator(Orientation.VERTICAL), txtSearch,
                new Separator(Orientation.VERTICAL), rightGroup);
        topMenu.getChildren().add(toolbarCard);

        // --- 2. LEFT SIDEBAR (SOL PANEL) ---
        leftPane = new VBox(15);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(300);

        // --- A) Period Settings Card ---
        // İçerik elemanları
        Label lblStart = new Label("Start Date:");
        startDate = new DatePicker(LocalDate.now());
        startDate.setMaxWidth(Double.MAX_VALUE);

        HBox toggleBox = new HBox(15);
        RadioButton rbDuration = new RadioButton("By Duration");
        RadioButton rbDates = new RadioButton("By End Date");
        ToggleGroup groupPeriod = new ToggleGroup();
        rbDuration.setToggleGroup(groupPeriod);
        rbDates.setToggleGroup(groupPeriod);
        rbDuration.setSelected(true);
        toggleBox.getChildren().addAll(rbDuration, rbDates);

        VBox inputContainer = new VBox(10);

        VBox durBox = new VBox(2);
        Label lblDays = new Label("Duration (Days):");
        txtDays = createStyledTextField("9");
        txtDays.setText("9");
        durBox.getChildren().addAll(lblDays, txtDays);

        VBox endBox = new VBox(2);
        Label lblEnd = new Label("End Date:");
        endDate = new DatePicker(LocalDate.now().plusDays(9));
        endDate.setMaxWidth(Double.MAX_VALUE);
        endBox.getChildren().addAll(lblEnd, endDate);

        inputContainer.getChildren().addAll(durBox, endBox);

        // Logic
        groupPeriod.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isDurationMode = rbDuration.isSelected();
            txtDays.setDisable(!isDurationMode);
            endDate.setDisable(isDurationMode);
            durBox.setOpacity(isDurationMode ? 1.0 : 0.5);
            endBox.setOpacity(isDurationMode ? 0.5 : 1.0);
        });
        endDate.setDisable(true);
        endBox.setOpacity(0.5);

        // Listeners
        txtDays.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                try {
                    int days = Integer.parseInt(txtDays.getText());
                    if (startDate.getValue() != null)
                        endDate.setValue(startDate.getValue().plusDays(days));
                } catch (Exception e) {
                }
            }
        });
        startDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !txtDays.getText().isEmpty()) {
                try {
                    int days = Integer.parseInt(txtDays.getText());
                    endDate.setValue(newVal.plusDays(days));
                } catch (Exception e) {
                }
            }
        });
        endDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && startDate.getValue() != null && rbDates.isSelected()) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate.getValue(), newVal);
                txtDays.setText(String.valueOf(Math.max(0, days)));
            }
        });

        // KART OLUŞTURMA
        VBox cardDate = createCard(
                " Period Settings",
                "Configure the overall date range for exams.",
                "Duration (Days): Sets the total length of the exam period.\nDate Range: Automatically updates based on Duration. Defines the start and end dates.",
                lblStart, startDate, toggleBox, inputContainer);

        // --- B) Constraints Card ---
        lblBlockTime = new Label("Exam Duration (min):");
        txtBlockTime = createStyledTextField("90");
        txtBlockTime.setText("90");

        lblTime = new Label("Working Hours:");
        HBox timeInputs = new HBox(5);
        txtTimeStart = createStyledTextField("09:00");
        txtTimeEnd = createStyledTextField("17:00");
        HBox.setHgrow(txtTimeStart, Priority.ALWAYS);
        HBox.setHgrow(txtTimeEnd, Priority.ALWAYS);
        timeInputs.getChildren().addAll(txtTimeStart, txtTimeEnd);

        VBox cardConstraints = createCard(
                "\u23F1\uFE0F Constraints",
                "Set default duration and daily working hours.",
                "Default Duration: Used for courses that do NOT have a duration specified in the CSV file (e.g., 90 min).\nTime Range: The daily working hours (e.g., 09:00 - 17:00).",
                lblBlockTime, txtBlockTime, lblTime, timeInputs);

        // --- C) Customization Card ---
        btnCustomize = new Button("Advanced Rules \u2699");
        btnCustomize.setMaxWidth(Double.MAX_VALUE);
        btnCustomize.setOnAction(e -> showCustomizationDialog(primaryStage));

        VBox cardCustom = createCard(
                "\u2699\uFE0F Customization",
                "Define exceptions for capacity & duration.",
                "Click this button to manually override settings for specific courses. For example, you can force 'CS101' to have a duration of 120 mins or require a room with a minimum capacity of 50.",
                btnCustomize);

        // Kartları Ekle
        leftPane.getChildren().addAll(cardDate, cardConstraints, cardCustom);
        cardDate.setPrefHeight(220);
        cardConstraints.setPrefHeight(220);
        cardCustom.setPrefHeight(220);

        // --- 3. BOTTOM BAR ---
        bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        lblStats = new Label("Total Exams: 0 | Total Students: 0 | Total Classes: 0");
        lblStats.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        bottomBar.getChildren().add(lblStats);

        // --- ANA YERLEŞİM ---
        root.setTop(topMenu);

        ScrollPane leftScroll = new ScrollPane(leftPane);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        root.setLeft(leftScroll);
        root.setBottom(bottomBar);

        // --- 4. OVERLAY ---
        loadingOverlay = new VBox(20);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        loadingOverlay.setVisible(false);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(60, 60);
        pi.setStyle("-fx-progress-color: " + ACCENT_COLOR + ";");
        Label lblLoad = new Label("Processing Data...");
        lblLoad.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblLoad.setTextFill(Color.WHITE);
        lblLoad.setEffect(new DropShadow(5, Color.BLACK));
        loadingOverlay.getChildren().addAll(pi, lblLoad);
    }

    // --- MERKEZİ ARAMA YÖNETİCİSİ ---
    private void performSearch(String query) {
        String q = (query == null) ? "" : query.toLowerCase().trim();

        if (tglStudents.isSelected()) {
            showStudentList(q);
        } else if (tglExams.isSelected()) {
            showExamList(q);
        } else if (tglClassrooms != null && tglClassrooms.isSelected()) { // EKLE:
            showClassroomList(q);
        } else if (tglDays.isSelected()) {
            showDayList(q);
        }
    }

    // O anki aktif sekmeyi yeniden yükler
    private void refreshActiveView() {
        if (tglStudents.isSelected()) {
            showStudentList();
        } else if (tglExams.isSelected()) {
            showExamList();
        } else if (tglClassrooms != null && tglClassrooms.isSelected()) { // EKLENDİ
            showClassroomList(txtSearch.getText());
        } else if (tglDays.isSelected()) {
            showDayList();
        }
    }

    private void loadSettingsFromDB() {
        String sDays = DBManager.loadSetting("days");
        if (sDays != null && !sDays.isEmpty())
            txtDays.setText(sDays);

        String sBlock = DBManager.loadSetting("blockTime");
        if (sBlock != null && !sBlock.isEmpty())
            txtBlockTime.setText(sBlock);

        String sTimeStart = DBManager.loadSetting("timeStart");
        if (sTimeStart != null && !sTimeStart.isEmpty())
            txtTimeStart.setText(sTimeStart);

        String sTimeEnd = DBManager.loadSetting("timeEnd");
        if (sTimeEnd != null && !sTimeEnd.isEmpty())
            txtTimeEnd.setText(sTimeEnd);

        String sDateStart = DBManager.loadSetting("startDate");
        if (sDateStart != null && !sDateStart.isEmpty())
            startDate.setValue(LocalDate.parse(sDateStart));

        String sDateEnd = DBManager.loadSetting("endDate");
        if (sDateEnd != null && !sDateEnd.isEmpty())
            endDate.setValue(LocalDate.parse(sDateEnd));
    }

    private void restoreRulesFromDB() {
        List<DBManager.RuleRecord> records = DBManager.loadRules();
        if (records.isEmpty())
            return;

        System.out.println("Restoring " + records.size() + " rule records from DB...");

        Map<Integer, List<DBManager.RuleRecord>> grouped = new HashMap<>();
        for (DBManager.RuleRecord r : records) {
            grouped.computeIfAbsent(r.groupId(), k -> new ArrayList<>()).add(r);
        }

        // Listeyi temizle (Duplicate olmasın)
        ruleGroups.clear();

        for (List<DBManager.RuleRecord> groupRules : grouped.values()) {
            if (groupRules.isEmpty())
                continue;

            DBManager.RuleRecord first = groupRules.get(0);

            List<Course> coursesToSelect = new ArrayList<>();
            for (DBManager.RuleRecord r : groupRules) {
                boolean found = false;
                // 1. Önce mevcut listede ara
                for (Course c : allCourses) {
                    if (c.getId().equals(r.courseId())) {
                        coursesToSelect.add(c);
                        found = true;
                        break;
                    }
                }
                // 2. Bulamazsan yeni oluştur
                if (!found) {
                    coursesToSelect.add(new Course(r.courseId(), 0));
                }
            }

            // Paneli oluştur
            RuleGroupPane pane = new RuleGroupPane(new VBox());
            pane.restoreSettings(first.duration(), first.minCap(), first.maxCap(), coursesToSelect);

            ruleGroups.add(pane);
        }
    }

    // ERROR HANDLING SYSTEM

    private void logError(String message) {
        // Hata zamanı ile birlikte kaydet
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8); // HH:mm:ss
        String logEntry = "[" + timestamp + "] " + message;

        errorLog.add(logEntry);

        // UI Güncellemesini FX Thread'de yap
        Platform.runLater(() -> {
            lblErrorCount.setText("Errors: " + errorLog.size());
        });

        System.err.println(logEntry); // Konsola da bas
    }

    private void showErrorLogDialog() {
        if (errorLog.isEmpty()) {
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Error Log");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));

        // Koyu/Açık tema kontrolü
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;

        layout.setStyle("-fx-background-color: " + bg + ";");

        // Başlık
        Label lblHeader = new Label("Session Errors (" + errorLog.size() + ")");
        lblHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        lblHeader.setTextFill(Color.web(text));

        // Hata Metin Alanı (TextArea)
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setFont(Font.font("Consolas", 12));

        // TextArea için özel stil (Koyu tema uyumlu)
        if (isDarkMode) {
            textArea.setStyle("-fx-control-inner-background: " + DARK_BTN + "; " +
                    "-fx-text-fill: #FF6B6B; " + // Hatalar kırmızımsı görünsün
                    "-fx-highlight-fill: " + ACCENT_COLOR + "; " +
                    "-fx-highlight-text-fill: white;");
        } else {
            textArea.setStyle("-fx-text-fill: #D32F2F;");
        }

        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Hataları listele
        StringBuilder sb = new StringBuilder();
        for (String err : errorLog) {
            sb.append("• ").append(err).append("\n\n");
        }
        textArea.setText(sb.toString());

        // Butonlar
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnClear = new Button("Clear Log");
        btnClear.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold;");
        btnClear.setOnAction(e -> {
            errorLog.clear();
            lblErrorCount.setText("Errors: 0");
            dialog.close();
        });

        Button btnClose = new Button("Close");
        btnClose.setStyle(
                "-fx-background-color: " + (isDarkMode ? DARK_BTN : LIGHT_BTN) + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        btnBox.getChildren().addAll(btnClear, btnClose);

        layout.getChildren().addAll(lblHeader, textArea, btnBox);

        Scene scene = new Scene(layout, 600, 400);
        dialog.setScene(scene);
        dialog.show();
    }

    // FILE PROCESSING

    private enum FileType { STUDENTS, COURSES, ROOMS, LINKS, UNKNOWN }

    private FileType detectFileTypeByName(File file) {
        String name = file.getName().toLowerCase();
        if (name.contains("allstudents") || name.contains("students") || name.contains("std_id")) return FileType.STUDENTS;
        if (name.contains("allcourses") || name.contains("courses") || name.contains("course")) return FileType.COURSES;
        if (name.contains("allclassrooms") || name.contains("classroom") || name.contains("room") || name.contains("capacities") || name.contains("capacity")) return FileType.ROOMS;
        if (name.contains("allattendancelists") || name.contains("attendance") || name.contains("enrollment") || name.contains("enrollments") || name.contains("links")) return FileType.LINKS;
        return FileType.UNKNOWN;
    }

    private FileType detectFileTypeByContent(File file) {
        // Peek first non-empty line and infer by header/columns.
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Normalize
                String lower = line.toLowerCase();
                // Split by comma/semicolon/tab
                String[] cols = lower.split("[;,\t]");
                if (cols.length == 0) return FileType.UNKNOWN;

                // Header-based detection
                String c0 = cols.length > 0 ? cols[0].trim() : "";
                String c1 = cols.length > 1 ? cols[1].trim() : "";

                // Students: studentId;firstName;lastName OR studentid
                if ((c0.contains("student") && c0.contains("id")) && (cols.length == 1 || !c1.contains("course"))) {
                    return FileType.STUDENTS;
                }
                // Courses: courseId;durationMinutes / duration
                if ((c0.contains("course") && c0.contains("id")) || (c0.contains("course") && c1.contains("duration")) || (c0.equals("courseid"))) {
                    return FileType.COURSES;
                }
                // Rooms: roomId;capacity OR classroomId;capacity
                if (((c0.contains("room") && c0.contains("id")) || (c0.contains("class") && c0.contains("id"))) && c1.contains("cap")) {
                    return FileType.ROOMS;
                }
                // Links: studentId;courseId OR courseId;studentId
                if ((c0.contains("student") && c0.contains("id") && c1.contains("course")) || (c0.contains("course") && c0.contains("id") && c1.contains("student"))) {
                    return FileType.LINKS;
                }

                // Data-based fallback (no header)
                // Accept multiple student formats:
                //  - 1 col:  Std_ID_001
                //  - 2 cols: Std_ID_001;John Doe  (students)
                //  - 2 cols: Std_ID_001;CourseCode_01 (links)
                //  - 3+ cols: Std_ID_001;John;Doe (students)

                String firstTok = cols[0].trim();
                String secondTok = (cols.length > 1) ? cols[1].trim() : "";

                boolean firstLooksStudent = firstTok.startsWith("std_id")
                        || firstTok.matches("std_?id_?\\d+")
                        || firstTok.matches("std_id_\\d+");

                boolean secondLooksCourse = secondTok.contains("course")
                        || secondTok.startsWith("coursecode_")
                        || secondTok.matches("[a-z]{2,}\\d{2,}.*");

                // 1 column student ids
                if (cols.length == 1 && firstLooksStudent) {
                    return FileType.STUDENTS;
                }

                // 3+ columns starting with student id => students
                if (cols.length >= 3 && firstLooksStudent) {
                    return FileType.STUDENTS;
                }

                // 2 columns: id;course => links, id;name => students
                if (cols.length == 2 && firstLooksStudent) {
                    return secondLooksCourse ? FileType.LINKS : FileType.STUDENTS;
                }

                // If 2 columns and second is numeric -> rooms OR courses
                if (cols.length >= 2) {
                    String second = cols[1].trim();
                    boolean secondNumeric = second.matches("\\d+");
                    if (secondNumeric) {
                        // Heuristic: if first contains "room" or "class" -> rooms else courses
                        String first = cols[0].trim();
                        if (first.contains("room") || first.contains("class")) return FileType.ROOMS;
                        return FileType.COURSES;
                    }
                }

                return FileType.UNKNOWN;
            }
        } catch (Exception ignored) {
        }
        return FileType.UNKNOWN;
    }

    private FileType detectFileType(File file) {
        FileType byContent = detectFileTypeByContent(file);
        if (byContent != FileType.UNKNOWN) return byContent;
        return detectFileTypeByName(file);
    }

    private void processAndLoadFiles(List<File> files) {
        showLoading();
        Set<String> existingPaths = new HashSet<>();
        for (UploadedFileItem item : uploadedFilesData)
            existingPaths.add(item.file.getAbsolutePath());

        Task<List<File>> task = new Task<>() {
            @Override
            protected List<File> call() {
                List<File> filesToAdd = new ArrayList<>();
                for (File f : files)
                    if (!existingPaths.contains(f.getAbsolutePath()))
                        filesToAdd.add(f);
                return filesToAdd;
            }
        };

        task.setOnSucceeded(e -> {
            List<File> newFiles = task.getValue();
            if (!newFiles.isEmpty()) {
                for (File file : newFiles) {
                    FileType ft = detectFileType(file);
                    String type = switch (ft) {
                        case STUDENTS -> "Students";
                        case COURSES -> "Courses";
                        case ROOMS -> "Rooms";
                        case LINKS -> "Links";
                        default -> "Unknown";
                    };

                    DBManager.saveUploadedFile(file.getAbsolutePath());

                    uploadedFilesData.add(new UploadedFileItem(file, file.getName() + "\n(" + type + ")"));
                    loadedFileCache.add(file);

                    try {
                        if (ft == FileType.STUDENTS) {
                            allStudents.addAll(CsvDataLoader.loadStudents(file.toPath()));
                        } else if (ft == FileType.COURSES) {
                            allCourses.addAll(CsvDataLoader.loadCourses(file.toPath()));
                        } else if (ft == FileType.ROOMS) {
                            allClassrooms.addAll(CsvDataLoader.loadClassrooms(file.toPath()));
                        } else if (ft == FileType.LINKS) {
                            allEnrollments.addAll(CsvDataLoader.loadEnrollments(file.toPath()));
                        }
                    } catch (java.io.IOException ex) {
                        System.err.println("Dosya okuma hatası (" + type + "): " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }
            hideLoading();
            updateStats();
            if (tglClassrooms.isSelected()) {
                txtSearch.setText(" ");
                txtSearch.setText("");
            } else {
                refreshActiveView();
            }
        });

        task.setOnFailed(e -> {
            hideLoading();
            e.getSource().getException().printStackTrace(); // Hata varsa konsola yaz
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // UI'daki tarih/saat alanlarından sınav günü/saat pencereleri üret
    private List<DayWindow> buildDayWindowsFromFilters() {
        LocalDate from = getFilterStartDate();
        LocalDate to = getFilterEndDate();

        // Hiç tarih seçilmediyse scheduler çalışmasın
        if (from == null || to == null) {
            return Collections.emptyList();
        }

        // Ters girildiyse düzelt
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        LocalTime fromTime = getFilterStartTime();
        LocalTime toTime = getFilterEndTime();

        // Saat alanları boşsa default ver
        if (fromTime == null) {
            fromTime = LocalTime.of(9, 0);
        }
        if (toTime == null) {
            toTime = LocalTime.of(17, 0);
        }

        List<DayWindow> windows = new ArrayList<>();
        LocalDate d = from;
        while (!d.isAfter(to)) {
            windows.add(new DayWindow(d, List.of(new TimeRange(fromTime, toTime))));
            d = d.plusDays(1);
        }
        return windows;
    }

    private void showLoading() {
        if (loadingOverlay != null) {
            root.setDisable(true); // Alttaki uygulamaya tıklanmasın
            loadingOverlay.setVisible(true);
            loadingOverlay.toFront(); // En öne getir
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false); // Gizle
            root.setDisable(false); // Uygulamayı tekrar aktif et
        }
    }

    private long rescheduleSeed = 42L;

    // SCHEDULER LOGIC

    private void runSchedulerLogic(boolean forceReshuffle) {
        // 1. Ayarları ve Kuralları Kaydet
        saveCurrentState();

        if (forceReshuffle) {
            rescheduleSeed = System.nanoTime();
            System.out.println("UI: Force re-schedule requested. Seed=" + rescheduleSeed);
        }

        System.out.println("UI: Reloading data from CHECKED files...");

        // 2. Temizlik
        allStudents.clear();
        allCourses.clear();
        allClassrooms.clear();
        allEnrollments.clear();
        studentScheduleMap.clear();
        lastUnscheduledReasons.clear();
        lastBottleneckStudents.clear();

        // Eski hataları sil
        errorLog.clear();
        Platform.runLater(() -> lblErrorCount.setText("Errors: 0"));

        // 3. Seçili Dosyaları Oku
        boolean anyFileChecked = false;
        for (UploadedFileItem item : uploadedFilesData) {
            if (item.isSelected.get()) {
                anyFileChecked = true;
                File file = item.file;
                try {
                    FileType ft = detectFileType(file);
                    if (ft == FileType.STUDENTS) {
                        allStudents.addAll(CsvDataLoader.loadStudents(file.toPath()));
                    } else if (ft == FileType.COURSES) {
                        allCourses.addAll(CsvDataLoader.loadCourses(file.toPath()));
                    } else if (ft == FileType.ROOMS) {
                        allClassrooms.addAll(CsvDataLoader.loadClassrooms(file.toPath()));
                    } else if (ft == FileType.LINKS) {
                        allEnrollments.addAll(CsvDataLoader.loadEnrollments(file.toPath()));
                    }
                } catch (Exception e) {
                    logError("Error loading " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        // 4. Kuralları Geri Yükle
        if (ruleGroups.isEmpty()) {
            restoreRulesFromDB();
        }

        // Kuralları Taze Verilere Uygula
        if (!ruleGroups.isEmpty()) {
            System.out.println("Applying " + ruleGroups.size() + " rule groups...");
            for (RuleGroupPane pane : ruleGroups) {
                pane.applyRulesToSelectedCourses();
            }
        }

        // 5. Varsayılan Süre Uygula
        int defaultDuration = 90;
        try {
            if (!txtBlockTime.getText().trim().isEmpty()) {
                defaultDuration = Integer.parseInt(txtBlockTime.getText().trim());
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid Block Time format, using default 90.");
        }

        for (Course c : allCourses) {
            if (c.getDurationMinutes() <= 0) {
                c.setDurationMinutes(defaultDuration);
            }
        }

        // 6. UI Güncelle
        masterStudentList.setAll(allStudents);
        masterExamList.setAll(allCourses);

        studentObservableList.setAll(allStudents);
        examObservableList.setAll(allCourses);
        updateStats();

        // Validasyonlar
        if (!anyFileChecked) {
            System.out.println("No files checked during auto-run.");
            return;
        }
// Fallback: Students dosyası algılanmadıysa bile enrollments'tan student üret
        if (allStudents.isEmpty() && !allEnrollments.isEmpty()) {
            Set<String> ids = new LinkedHashSet<>();
            for (Enrollment e : allEnrollments) {
                if (e.getStudentId() != null && !e.getStudentId().isBlank()) {
                    ids.add(e.getStudentId().trim());
                }
            }
            for (String id : ids) {
                allStudents.add(new Student(id, "")); // isim yoksa boş bırak
            }
            System.out.println("UI: Students file missing/undetected. Reconstructed " + allStudents.size() + " students from enrollments.");
        }
        if (allStudents.isEmpty() || allCourses.isEmpty() || allClassrooms.isEmpty() || allEnrollments.isEmpty()) {
            if (allStudents.isEmpty()) logError("No students loaded. Check student CSV format/header/delimiter or file type detection.");
            if (allCourses.isEmpty()) logError("No courses loaded. Check courses CSV format/header/delimiter or file type detection.");
            if (allClassrooms.isEmpty()) logError("No classrooms loaded. Check rooms CSV format/header/delimiter or file type detection.");
            if (allEnrollments.isEmpty()) logError("No enrollments loaded. Check links/enrollments CSV format/header/delimiter or file type detection.");
            return;
        }

        List<DayWindow> dayWindows = buildDayWindowsFromFilters();
        if (dayWindows.isEmpty())
            return;

        showLoading();

        // 7. Arka Plan Görevi
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DBManager.clearScheduleTable();
                DBManager.clearConflictLog();

                // Shuffle kopyaları
                List<Student> studentsIn = new ArrayList<>(allStudents);
                List<Course> coursesIn = new ArrayList<>(allCourses);
                coursesIn.removeIf(Course::isIgnored);
                List<Enrollment> enrollmentsIn = new ArrayList<>(allEnrollments);
                List<Classroom> classroomsIn = new ArrayList<>(allClassrooms);
                List<DayWindow> dayWindowsIn = new ArrayList<>(dayWindows);

                if (forceReshuffle) {
                    Random rnd = new Random(rescheduleSeed);
                    Collections.shuffle(studentsIn, rnd);
                    Collections.shuffle(coursesIn, rnd);
                    Collections.shuffle(enrollmentsIn, rnd);
                    Collections.shuffle(classroomsIn, rnd);
                    Collections.shuffle(dayWindowsIn, rnd);
                }

                final int BEST_OF_N = 10;

                ScheduleRunResult bestResult = java.util.stream.IntStream.range(0, BEST_OF_N)
                        .parallel()
                        .mapToObj(i -> {
                            // Her çekirdek için verinin kopyasını ve özel seed'i kullan
                            long seed = rescheduleSeed + (i * 31);
                            return runSchedulerOnce(
                                    seed,
                                    new ArrayList<>(studentsIn),
                                    new ArrayList<>(coursesIn),
                                    new ArrayList<>(enrollmentsIn),
                                    new ArrayList<>(classroomsIn),
                                    new ArrayList<>(dayWindowsIn));
                        })
                        // Sonuçları karşılaştır ve en iyisini seç
                        .min((r1, r2) -> {
                            ScheduleScore s1 = computeScore(r1.schedule, r1.reasons);
                            ScheduleScore s2 = computeScore(r2.schedule, r2.reasons);
                            // s1 daha iyiyse -1 (önce gelir), s2 daha iyiyse 1
                            if (s1.betterThan(s2))
                                return -1;
                            if (s2.betterThan(s1))
                                return 1;
                            return 0;
                        })
                        .orElse(null);

                // Herhangi bir koşu sonucu üretilemediyse çık
                if (bestResult == null) {
                    return null;
                }

                // Seçilen en iyi sonucu DB'ye yaz
                for (List<StudentExam> list : bestResult.schedule.values()) {
                    for (StudentExam se : list) {
                        DBManager.insertSchedule(se);
                    }
                }

                for (Map.Entry<String, String> entry : bestResult.reasons.entrySet()) {
                    DBManager.logConflict(entry.getKey(), entry.getValue());
                }

                // UI thread içinde kullanmak için final kopyalar
                final ScheduleRunResult chosen = bestResult;
                final Map<String, String> reasons = chosen.reasons;

                Platform.runLater(() -> {
                    studentScheduleMap = chosen.schedule;
                    lastUnscheduledReasons = reasons;
                    lastBottleneckStudents = extractBottleneckStudents(reasons);

                    // 1. Master Listeleri Güncelle
                    masterStudentList.setAll(allStudents);
                    masterExamList.setAll(allCourses);

                    // 2. Günlük Master Listesini Oluştur
                    buildMasterDayList();

                    if (!reasons.isEmpty()) {
                        // Hataları Sınav Koduna Göre Sırala
                        List<Map.Entry<String, String>> sortedErrors = new ArrayList<>(reasons.entrySet());
                        sortedErrors.sort((e1, e2) -> naturalCompare(e1.getKey(), e2.getKey()));

                        for (Map.Entry<String, String> entry : sortedErrors) {
                            logError("Scheduling Failed: " + entry.getKey() + " -> " + entry.getValue());
                        }
                    }

                    int totalScheduledExams = studentScheduleMap.values().stream().mapToInt(List::size).sum();
                    lblStats.setText(String.format("Scheduled: %d total exam entries | %d students assigned",
                            totalScheduledExams, studentScheduleMap.size()));

                    refreshActiveView();
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> hideLoading());
        task.setOnFailed(e -> {
            hideLoading();
            updateStats();
            refreshActiveView();
            logError("Critical Scheduler Error: " + task.getException().getMessage());
            task.getException().printStackTrace();
        });

        Thread t = new Thread(task, "exam-scheduler-task");
        t.setDaemon(true);
        t.start();
    }

    // DATE / TIME FILTER HELPERS (LEFT SIDEBAR)

    private LocalDate getFilterStartDate() {
        return (startDate != null) ? startDate.getValue() : null;
    }

    private LocalDate getFilterEndDate() {
        return (endDate != null) ? endDate.getValue() : null;
    }

    private LocalTime parseTimeField(TextField field) {
        if (field == null)
            return null;
        String text = field.getText();
        if (text == null)
            return null;
        text = text.trim();
        if (text.isEmpty())
            return null;

        try {
            // "09:00" formatı
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            // Geçersizse filtre yok say
            return null;
        }
    }

    private LocalTime getFilterStartTime() {
        return parseTimeField(txtTimeStart);
    }

    private LocalTime getFilterEndTime() {
        return parseTimeField(txtTimeEnd);
    }

    /**
     * Bir Timeslot mevcut tarih+saat filtreleri ile uyuşuyor mu?
     * - Tarih aralığı: [startDate, endDate]
     * - Saat aralığı: sınav aralığı seçilen saat aralığı ile örtüşüyor mu?
     */
    private boolean timeslotMatchesFilters(Timeslot ts) {
        if (ts == null)
            return false;

        LocalDate fromDate = getFilterStartDate();
        LocalDate toDate = getFilterEndDate();
        LocalTime fromTime = getFilterStartTime();
        LocalTime toTime = getFilterEndTime();

        // 1) Tarih filtresi
        if (fromDate != null && ts.getDate().isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && ts.getDate().isAfter(toDate)) {
            return false;
        }

        // 2) Saat filtresi (aynı gün için zaman aralığı örtüşme kontrolü)
        if (fromTime == null && toTime == null) {
            return true; // sadece tarih filtresi varsa ve geçtiyse OK
        }

        LocalTime slotStart = ts.getStart();
        LocalTime slotEnd = ts.getEnd();

        // Filtrede sadece başlangıç varsa: sınav bu saatten önce tamamen bitmişse
        // eleriz

        if (fromTime != null && slotEnd.isBefore(fromTime)) {
            return false;
        }
        // Filtrede sadece bitiş varsa: sınav bu saatten sonra tamamen başlıyorsa eleriz
        if (toTime != null && slotStart.isAfter(toTime)) {
            return false;
        }
        // Buraya geldiysek, sınav aralığı filtre aralığıyla kısmen bile olsa örtüşüyor
        return true;
    }

    // Bir öğrencinin sınav listesini mevcut filtrelere göre süzer.
    private List<StudentExam> filterExamsByCurrentFilters(List<StudentExam> exams) {
        if (exams == null || exams.isEmpty())
            return Collections.emptyList();
        List<StudentExam> out = new ArrayList<>();
        for (StudentExam se : exams) {
            if (se.getTimeslot() != null && timeslotMatchesFilters(se.getTimeslot())) {
                out.add(se);
            }
        }
        return out;
    }

    // Belirli bir dersin ilk atanmış sınavından tarihi al
    private String getCourseDate(String courseId) {
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId))
                    continue;
                if (!timeslotMatchesFilters(se.getTimeslot()))
                    continue;
                return se.getTimeslot().getDate().toString();
            }
        }
        return "UNSCHEDULED";
    }

    // Belirli bir dersin ilk atanmış sınavından saat aralığını al
    private String getCourseTimeRange(String courseId) {
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId))
                    continue;
                if (!timeslotMatchesFilters(se.getTimeslot()))
                    continue;
                return se.getTimeslot().getStart().toString() + " - "
                        + se.getTimeslot().getEnd().toString();
            }
        }
        return "-";
    }

    // Belirli bir ders için kullanılan tüm sınıfları topla
    private String getCourseRooms(String courseId) {
        java.util.Set<String> rooms = new java.util.LinkedHashSet<>();
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId))
                    continue;
                if (!timeslotMatchesFilters(se.getTimeslot()))
                    continue;
                rooms.add(se.getClassroomId());
            }
        }
        if (rooms.isEmpty()) {
            return "-";
        }
        return String.join(", ", rooms);
    }

    // Belirli bir ders için toplam kaç öğrenci atanmış?
    private int getCourseStudentCount(String courseId) {
        int count = 0;
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId))
                    continue;
                if (!timeslotMatchesFilters(se.getTimeslot()))
                    continue;
                count++;
            }
        }
        return count;
    }

    // Bu ders aslında global schedule'da var mı?
    private boolean isCourseScheduledGlobally(String courseId) {
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (se.getCourseId().equals(courseId) && se.getTimeslot() != null) {
                    return true; // en az bir slot buldu, globalde scheduled
                }
            }
        }
        return false; // hiç bulunamadı → gerçekten unscheduled
    }

    // Dersin mevcut filtrelere göre ve genel durumda durumu + sebebi
    private String getCourseStatusText(String courseId) {
        int visibleCount = getCourseStudentCount(courseId);

        // 1) Mevcut tarih/saat filtre aralığında öğrenci varsa → scheduled
        if (visibleCount > 0) {
            return "Scheduled (in current filters)";
        }

        // 2) Filtreye göre 0 ama globalde aslında bir slot'a atanmış olabilir
        if (isCourseScheduledGlobally(courseId)) {
            return "Scheduled (outside selected range)";
        }

        // 3) Hiç atanamamış → unscheduled + sebep
        String reason = lastUnscheduledReasons.get(courseId);
        if (reason != null && !reason.isBlank()) {
            return "UNSCHEDULED: " + reason;
        }

        // 4) Hiç sebep yoksa
        return "UNSCHEDULED";
    }

    // EXPORT HELPERS
    // Rows are: [CourseCode, Date, Time, Rooms, Students, Status]
    private List<String[]> buildScheduleExportRowsByCourse() {
        List<String[]> rows = new ArrayList<>();

        // Prefer the master exam list if present; otherwise fall back to allCourses
        List<Course> source;
        if (masterExamList != null && !masterExamList.isEmpty()) {
            source = new ArrayList<>(masterExamList);
        } else {
            source = new ArrayList<>(allCourses);
        }

        // Stable ordering
        source.sort((a, b) -> naturalCompare(a.getId(), b.getId()));

        for (Course c : source) {
            if (c == null) continue;
            String courseId = c.getId();
            if (courseId == null || courseId.isBlank()) continue;

            String date = getCourseDate(courseId);
            String time = getCourseTimeRange(courseId);
            String rooms = getCourseRooms(courseId);
            String students = String.valueOf(getCourseStudentCount(courseId));
            String status = getCourseStatusText(courseId);

            rows.add(new String[]{courseId, date, time, rooms, students, status});
        }
        return rows;
    }

    private File chooseSaveFile(Stage owner, String title, String suggestedName, FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(filter);
        if (suggestedName != null && !suggestedName.isBlank()) {
            fc.setInitialFileName(suggestedName);
        }
        return fc.showSaveDialog(owner);
    }

    private void showInfoDialog(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.initOwner(primaryStage);
        a.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.initOwner(primaryStage);
        a.showAndWait();
    }

    private void updateStats() {
        lblStats.setText(String.format("Total Exams: %d | Total Students: %d | Total Classes: %d",
                allCourses.size(), allStudents.size(), allClassrooms.size()));
    }

    // COURSE DETAIL VIEW (Students in a specific Exam)

    private void showCourseStudentList(Course course) {
        // 1. Hafızaya al
        currentDetailItem = course;

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        // Renkleri al
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;

        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + bg + ";");

        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: " + bg + ";");

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("\u2190 Back to Exams");
        btnBack.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + text
                + "; -fx-background-radius: 4; -fx-border-color: #666; -fx-border-radius: 4;");
        btnBack.setOnAction(e -> showExamList());

        Label lblTitle = new Label("Exam Rolls: " + course.getId());
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        lblTitle.setTextFill(Color.web(text));

        header.getChildren().addAll(btnBack, lblTitle);
        contentBox.getChildren().addAll(header, new Separator());
        String status = getCourseStatusText(course.getId());
        if (status.startsWith("UNSCHEDULED")) {
            String reason = lastUnscheduledReasons.get(course.getId());
            if (reason == null || reason.isBlank())
                reason = status;

            Label lblReason = new Label("Reason: " + reason);
            lblReason.setWrapText(true);
            lblReason.setMaxWidth(900);
            lblReason.setTextFill(Color.web(isDarkMode ? "#FF6B6B" : "#D32F2F"));
            contentBox.getChildren().add(lblReason);
            contentBox.getChildren().add(new Separator());
        }
        List<Student> enrolledStudents = new ArrayList<>();
        Set<String> enrolledIds = new HashSet<>();
        for (Enrollment e : allEnrollments) {
            if (e.getCourseId().equals(course.getId()))
                enrolledIds.add(e.getStudentId());
        }
        for (Student s : allStudents) {
            if (enrolledIds.contains(s.getId()))
                enrolledStudents.add(s);
        }

        Map<String, List<Student>> studentsByRoom = new HashMap<>();
        for (Student s : enrolledStudents) {
            String room = findStudentRoom(s.getId(), course.getId());
            studentsByRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(s);
        }

        List<String> sortedRooms = new ArrayList<>(studentsByRoom.keySet());
        sortedRooms.sort((r1, r2) -> {
            if (r1.equals("-"))
                return 1;
            if (r2.equals("-"))
                return -1;
            return naturalCompare(r1, r2);
        });

        if (sortedRooms.isEmpty()) {
            Label emptyLbl = new Label("No students enrolled or scheduled.");
            emptyLbl.setTextFill(Color.GRAY);
            contentBox.getChildren().add(emptyLbl);
        }

        for (String room : sortedRooms) {
            List<Student> roomStudents = studentsByRoom.get(room);
            roomStudents.sort((s1, s2) -> naturalCompare(s1.getId(), s2.getId()));

            String headerText = room.equals("-") ? "Unassigned / Waiting List" : room;
            Label lblRoomHeader = new Label(headerText + " (" + roomStudents.size() + " Students)");
            lblRoomHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            lblRoomHeader.setTextFill(Color.web(ACCENT_COLOR));
            lblRoomHeader.setStyle(
                    "-fx-border-color: transparent transparent #666 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 5 0;");

            TableView<Student> roomTable = new TableView<>();
            styleTableView(roomTable);
            roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

            TableColumn<Student, String> colId = new TableColumn<>("Student ID");
            colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

            roomTable.getColumns().add(colId);
            roomTable.setItems(FXCollections.observableArrayList(roomStudents));

            int rowHeight = 35;
            int tableHeight = (roomStudents.size() * rowHeight) + 40;
            roomTable.setFixedCellSize(rowHeight);
            roomTable.setPrefHeight(tableHeight);
            roomTable.setMinHeight(tableHeight);
            roomTable.setMaxHeight(tableHeight);

            VBox roomGroup = new VBox(10);
            roomGroup.getChildren().addAll(lblRoomHeader, roomTable);
            contentBox.getChildren().add(roomGroup);
        }

        scrollPane.setContent(contentBox);
        root.setCenter(scrollPane);
    }

    // Öğrencinin o dersteki sınıfını bulur
    private String findStudentRoom(String studentId, String courseId) {
        List<StudentExam> exams = studentScheduleMap.get(studentId);
        if (exams != null) {
            for (StudentExam se : exams) {
                if (se.getCourseId().equals(courseId)) {
                    return se.getClassroomId();
                }
            }
        }
        return "-";
    }

    // CENTER VIEWS (Tables)

    private void showStudentList() {
        showStudentList(txtSearch.getText());
    }

    @SuppressWarnings("unchecked")
    private void showStudentList(String filterQuery) {
        currentDetailItem = null;

        // Filtreleme Mantığı
        if (filterQuery == null || filterQuery.isEmpty()) {
            studentObservableList.setAll(masterStudentList);
        } else {
            String lower = filterQuery.toLowerCase();
            List<Student> filtered = masterStudentList.stream()
                    .filter(s -> {
                        String id = (s.getId() == null) ? "" : s.getId().toLowerCase();
                        String name = (s.getName() == null) ? "" : s.getName().toLowerCase();
                        return id.contains(lower) || name.contains(lower);
                    })
                    .collect(Collectors.toList());
            studentObservableList.setAll(filtered);
        }

        TableView<Student> table = new TableView<>();
        table.setPlaceholder(getTablePlaceholder());
        styleTableView(table);

        // 1. ID Sütunu
        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colId.setPrefWidth(120);

        // 2. İsim Sütunu (YENİ) - sadece isim varsa göster
        boolean hasAnyStudentName = masterStudentList.stream()
                .anyMatch(s -> s != null && s.getName() != null && !s.getName().trim().isEmpty());
        TableColumn<Student, String> colName = null;
        if (hasAnyStudentName) {
            colName = new TableColumn<>("Student Name");
            colName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
            colName.setPrefWidth(150);
        }

        // 3. Exam Count
        TableColumn<Student, String> colExamCount = new TableColumn<>("Exams");
        colExamCount.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams = studentScheduleMap.getOrDefault(sid, Collections.emptyList());
            exams = filterExamsByCurrentFilters(exams);
            return new SimpleStringProperty(String.valueOf(exams.size()));
        });
        colExamCount.setPrefWidth(70);

        // 4. Start Date (YENİ) - Öğrencinin ilk sınav tarihi
        TableColumn<Student, String> colStart = new TableColumn<>("First Exam");
        colStart.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams = studentScheduleMap.getOrDefault(sid, Collections.emptyList());
            exams = filterExamsByCurrentFilters(exams);

            if (exams.isEmpty()) return new SimpleStringProperty("-");

            return new SimpleStringProperty(exams.stream()
                    .map(e -> e.getTimeslot().getDate())
                    .min(LocalDate::compareTo)
                    .map(LocalDate::toString)
                    .orElse("-"));
        });
        colStart.setPrefWidth(100);

        // 5. End Date (YENİ) - Öğrencinin son sınav tarihi
        TableColumn<Student, String> colEnd = new TableColumn<>("Last Exam");
        colEnd.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams = studentScheduleMap.getOrDefault(sid, Collections.emptyList());
            exams = filterExamsByCurrentFilters(exams);

            if (exams.isEmpty()) return new SimpleStringProperty("-");

            return new SimpleStringProperty(exams.stream()
                    .map(e -> e.getTimeslot().getDate())
                    .max(LocalDate::compareTo)
                    .map(LocalDate::toString)
                    .orElse("-"));
        });
        colEnd.setPrefWidth(100);

        if (hasAnyStudentName) {
            table.getColumns().addAll(colId, colName, colExamCount, colStart, colEnd);
        } else {
            table.getColumns().addAll(colId, colExamCount, colStart, colEnd);
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setItems(studentObservableList);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showStudentScheduleDetail(newVal);
            }
        });
        root.setCenter(wrapTableInCard(table));
    }

    @SuppressWarnings("unchecked")
    private void showStudentScheduleDetail(Student student) {
        // 1. Hafızaya al
        currentDetailItem = student;

        VBox detailView = new VBox(10);
        detailView.setPadding(new Insets(20));

        // Renkleri al
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;

        detailView.setStyle("-fx-background-color: " + bg + ";");

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        // Geri Butonu
        Button btnBack = new Button("\u2190 Back List");
        btnBack.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + text
                + "; -fx-background-radius: 4; -fx-border-color: #666; -fx-border-radius: 4;");
        btnBack.setOnAction(e -> showStudentList());

        Button btnExport = new Button("Export CSV");
        btnExport.setStyle(
                "-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4;");
        btnExport.setOnAction(e -> exportSingleStudentSchedule(student));

        Label lblTitle = new Label("Exam Schedule: " + student.getId());
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web(text));

        // Header'a butonları ekle
        header.getChildren().addAll(btnBack, btnExport, lblTitle);

        TableView<StudentExam> detailTable = new TableView<>();
        styleTableView(detailTable);
        detailTable.setPlaceholder(new Label("No exams scheduled for this student."));

        TableColumn<StudentExam, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseId"));

        TableColumn<StudentExam, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getDate().toString()));

        TableColumn<StudentExam, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getStart().toString()
                + " - " + cell.getValue().getTimeslot().getEnd().toString()));

        TableColumn<StudentExam, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("classroomId"));

        TableColumn<StudentExam, String> colSeat = new TableColumn<>("Seat");
        colSeat.setCellValueFactory(new PropertyValueFactory<>("seatNo"));

        detailTable.getColumns().addAll(colCourse, colDate, colTime, colRoom, colSeat);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        List<StudentExam> exams = studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());
        exams = filterExamsByCurrentFilters(exams);
        detailTable.setItems(FXCollections.observableArrayList(exams));

        detailView.getChildren().addAll(header, new Separator(), detailTable);
        root.setCenter(detailView);
    }

    // SHOW EXAM LIST (Sınavlar Sekmesi)

    private void showExamList() {
        showExamList(txtSearch.getText());
    }

    @SuppressWarnings("unchecked")
    private void showExamList(String filterQuery) {
        currentDetailItem = null;
        TableView<Course> table = new TableView<>();
        // Dinamik placeholder kullan
        table.setPlaceholder(getTablePlaceholder());
        styleTableView(table);

        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Hücre Özelleştirme
        javafx.util.Callback<TableColumn<Course, String>, TableCell<Course, String>> customCellFactory = column -> new TableCell<Course, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }

                Course course = getTableRow().getItem();
                String statusText = getCourseStatusText(course.getId());
                boolean isUnscheduled = statusText.contains("UNSCHEDULED");

                // Arka planı boyamıyoruz, sadece metin ve küçük bir nokta ekliyoruz
                setText(item);

                String color = isUnscheduled ? (isDarkMode ? "#FF6B6B" : "#D32F2F")
                        : (isDarkMode ? "#81C784" : "#2E7D32");

                // Sadece Status kolonunda ikon gösterelim
                if (getTableColumn().getText().equals("Status")) {
                    Label dot = new Label(isUnscheduled ? "● " : "✔ ");
                    dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 14px;");
                    setGraphic(dot);
                } else {
                    setGraphic(null);
                }

                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-background-color: transparent;");
            }
        };

        // Kolonlar

        TableColumn<Course, String> colCode = new TableColumn<>("Course Code");
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colCode.setCellFactory(customCellFactory);
        colCode.setPrefWidth(120); // Genişlik verildi

        TableColumn<Course, String> colDur = new TableColumn<>("Duration");
        colDur.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));
        colDur.setCellFactory(customCellFactory);
        colDur.setPrefWidth(70); // Genişlik verildi

        TableColumn<Course, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(getCourseDate(cell.getValue().getId())));
        colDate.setCellFactory(customCellFactory);
        colDate.setPrefWidth(100); // Genişlik verildi

        TableColumn<Course, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(getCourseTimeRange(cell.getValue().getId())));
        colTime.setCellFactory(customCellFactory);
        colTime.setPrefWidth(110); // Genişlik verildi

        TableColumn<Course, String> colRooms = new TableColumn<>("Rooms");
        colRooms.setCellValueFactory(cell -> new SimpleStringProperty(getCourseRooms(cell.getValue().getId())));
        colRooms.setCellFactory(customCellFactory);
        colRooms.setPrefWidth(150); // Genişlik verildi

        TableColumn<Course, String> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(getCourseStudentCount(cell.getValue().getId()))));
        colCount.setCellFactory(customCellFactory);
        colCount.setPrefWidth(70); // Genişlik verildi

        TableColumn<Course, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(getCourseStatusText(cell.getValue().getId())));
        colStatus.setCellFactory(customCellFactory);
        colStatus.setPrefWidth(250); // Status mesajları uzun olabileceği için geniş verildi

        table.getColumns().setAll(colCode, colDur, colDate, colTime, colRooms, colCount, colStatus);

        // Filtreleme Mantığı
        ObservableList<Course> displayList = FXCollections.observableArrayList();

        // Eğer veri henüz yüklenmediyse boş dönmemesi için
        if (masterExamList.isEmpty() && !allCourses.isEmpty()) {
            masterExamList.setAll(allCourses);
        }

        if (filterQuery == null || filterQuery.isEmpty()) {
            displayList.addAll(masterExamList);
        } else {
            String q = filterQuery.toLowerCase();
            for (Course c : masterExamList) {
                if (c.getId().toLowerCase().contains(q)) {
                    displayList.add(c);
                }
            }
        }

        // Sıralama ve Ekleme
        javafx.collections.transformation.SortedList<Course> sortedExams = new javafx.collections.transformation.SortedList<>(
                displayList);
        sortedExams.setComparator((c1, c2) -> naturalCompare(c1.getId(), c2.getId()));
        table.setItems(sortedExams);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                showCourseStudentList(newVal);
        });

        root.setCenter(wrapTableInCard(table));
    }

    // CLASSROOM LIST
    private void showClassroomList(String filterQuery) {
        currentDetailItem = null;

        TableView<Classroom> table = new TableView<>();
        table.setPlaceholder(getTablePlaceholder());
        styleTableView(table);

        // 1. ID
        TableColumn<Classroom, String> colId = new TableColumn<>("Classroom ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colId.setMinWidth(150);
        colId.setPrefWidth(200);

        // 2. Capacity
        TableColumn<Classroom, String> colCap = new TableColumn<>("Capacity");
        colCap.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getCapacity())));
        colCap.setMinWidth(80);
        colCap.setPrefWidth(100);

        // 3. Start Date (YENİ) - O sınıftaki ilk sınav
        TableColumn<Classroom, String> colStart = new TableColumn<>("First Exam");
        colStart.setCellValueFactory(cell -> {
            String rid = cell.getValue().getId();
            LocalDate minDate = null;
            
            // Tüm programı tara (Biraz maliyetli olabilir ama veri boyutu küçükse sorun olmaz)
            for (List<StudentExam> list : studentScheduleMap.values()) {
                for (StudentExam se : list) {
                    if (se.getClassroomId().equals(rid) && se.getTimeslot() != null) {
                        LocalDate d = se.getTimeslot().getDate();
                        if (minDate == null || d.isBefore(minDate)) {
                            minDate = d;
                        }
                    }
                }
            }
            return new SimpleStringProperty(minDate == null ? "-" : minDate.toString());
        });
        colStart.setPrefWidth(110);

        // 4. End Date (YENİ) - O sınıftaki son sınav
        TableColumn<Classroom, String> colEnd = new TableColumn<>("Last Exam");
        colEnd.setCellValueFactory(cell -> {
            String rid = cell.getValue().getId();
            LocalDate maxDate = null;
            
            for (List<StudentExam> list : studentScheduleMap.values()) {
                for (StudentExam se : list) {
                    if (se.getClassroomId().equals(rid) && se.getTimeslot() != null) {
                        LocalDate d = se.getTimeslot().getDate();
                        if (maxDate == null || d.isAfter(maxDate)) {
                            maxDate = d;
                        }
                    }
                }
            }
            return new SimpleStringProperty(maxDate == null ? "-" : maxDate.toString());
        });
        colEnd.setPrefWidth(110);

        table.getColumns().addAll(colId, colCap, colStart, colEnd);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Filtreleme
        List<Classroom> sourceList = (allClassrooms != null) ? allClassrooms : new ArrayList<>();
        List<Classroom> filteredData = sourceList.stream()
                .filter(c -> filterQuery == null || filterQuery.isEmpty() ||
                        c.getId().toLowerCase().contains(filterQuery.toLowerCase()))
                .collect(Collectors.toList());

        table.setItems(FXCollections.observableArrayList(filteredData));

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showClassroomScheduleDetail(newVal);
            }
        });

        root.setCenter(wrapTableInCard(table));
    }

    private void showClassroomScheduleDetail(Classroom classroom) {
        currentDetailItem = classroom; // Aktif öğeyi classroom olarak ayarlar
        VBox detailView = new VBox(10);
        detailView.setPadding(new Insets(20));
        // Tema renklerini uygular
        detailView.setStyle("-fx-background-color: " + (isDarkMode ? DARK_BG : LIGHT_BG) + ";");

        // Header: Back Butonu ve Sınıf İsmi
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        Button btnBack = new Button("← Back to List");
        // Buton stili
        String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String btnText = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        btnBack.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + btnText +
                "; -fx-background-radius: 4; -fx-border-color: #666; -fx-border-radius: 4;");

        btnBack.setOnAction(e -> showClassroomList(txtSearch.getText())); // Aramayı koruyarak geri dön

        Label lblTitle = new Label(
                "Classroom Schedule: " + classroom.getId() + " (Cap: " + classroom.getCapacity() + ")");
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));
        header.getChildren().addAll(btnBack, lblTitle);

        // Tablo: Bu sınıftaki sınavlar
        TableView<DayRow> scheduleTable = new TableView<>();
        styleTableView(scheduleTable); // Ortak tablo stilini uygular
        scheduleTable.setPlaceholder(new Label("No exams scheduled in this room."));

        TableColumn<DayRow, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseId"));

        TableColumn<DayRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<DayRow, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));

        TableColumn<DayRow, String> colStudents = new TableColumn<>("# Students Here");
        colStudents.setCellValueFactory(new PropertyValueFactory<>("studentCount"));

        scheduleTable.getColumns().addAll(colCourse, colDate, colTime, colStudents);
        scheduleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // --- VERİ HAZIRLAMA (Aggregation Logic) ---
        // Sınıf ID'sine göre filtrele ve her slot için öğrenci sayısını topla
        Map<String, DayRow> aggregationMap = new HashMap<>();
        String targetId = classroom.getId().trim();

        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (se.getClassroomId() != null && se.getClassroomId().trim().equalsIgnoreCase(targetId)) {

                    // Benzersiz anahtar: Tarih + Zaman + Ders
                    String key = se.getTimeslot().getDate().toString() + "|" +
                            se.getTimeslot().getStart() + "|" +
                            se.getCourseId();

                    DayRow row = aggregationMap.get(key);
                    if (row == null) {
                        // Yeni satır oluştur, başlangıç sayısı 1
                        row = new DayRow(
                                se.getTimeslot().getDate().toString(),
                                se.getTimeslot().getStart() + " - " + se.getTimeslot().getEnd(),
                                se.getClassroomId(),
                                se.getCourseId(),
                                1);
                        aggregationMap.put(key, row);
                    } else {
                        // Var olan satırın öğrenci sayısını artır
                        row.increment();
                    }
                }
            }
        }

        List<DayRow> classroomExams = new ArrayList<>(aggregationMap.values());
        classroomExams.sort(Comparator.comparing(DayRow::getDate).thenComparing(DayRow::getTime));

        scheduleTable.setItems(FXCollections.observableArrayList(classroomExams));

        detailView.getChildren().addAll(header, new Separator(), scheduleTable);
        root.setCenter(detailView);
    }

    // 1. Parametresiz Versiyon
    private void showDayList() {
        showDayList(txtSearch.getText());
    }

    // 2. Parametreli Versiyon (SADECE TARİH ARAMASI)
    @SuppressWarnings("unchecked")
    private void showDayList(String filterQuery) {
        currentDetailItem = null;
        TableView<DayRow> table = new TableView<>();
        table.setPlaceholder(getTablePlaceholder());
        styleTableView(table);

        // Veriyi Hazırla
        Map<String, DayRow> map = new LinkedHashMap<>();
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                Timeslot ts = se.getTimeslot();
                if (ts == null || !timeslotMatchesFilters(ts))
                    continue;

                String dateStr = ts.getDate().toString();
                String timeStr = ts.getStart().toString() + " - " + ts.getEnd().toString();
                String key = dateStr + "|" + timeStr + "|" + se.getClassroomId() + "|" + se.getCourseId();

                DayRow row = map.get(key);
                if (row == null) {
                    map.put(key, new DayRow(dateStr, timeStr, se.getClassroomId(), se.getCourseId(), 1));
                } else {
                    row.increment();
                }
            }
        }

        List<DayRow> allRows = new ArrayList<>(map.values());
        ObservableList<DayRow> displayRows = FXCollections.observableArrayList();

        // --- FİLTRELEME MANTIĞI ---
        if (filterQuery == null || filterQuery.isEmpty()) {
            displayRows.addAll(allRows);
        } else {
            String q = filterQuery.toLowerCase();
            for (DayRow r : allRows) {
                // SADECE TARİH İÇİNDE ARAMA YAP
                if (r.getDate().toLowerCase().contains(q)) {
                    displayRows.add(r);
                }
            }
        }

        // Sıralama
        FXCollections.sort(displayRows, Comparator
                .comparing(DayRow::getDate)
                .thenComparing(DayRow::getTime)
                .thenComparing(DayRow::getRoom));

        TableColumn<DayRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDate()));

        TableColumn<DayRow, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTime()));

        TableColumn<DayRow, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoom()));

        TableColumn<DayRow, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCourseId()));

        TableColumn<DayRow, String> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getStudentCount())));

        table.getColumns().setAll(colDate, colTime, colRoom, colCourse, colCount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setItems(displayRows);
        root.setCenter(wrapTableInCard(table));
    }

    // DIALOGS & THEME ENGINE

    private void showImportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Manage Data Files");

        // Ana yapı BorderPane'e çevrildi
        BorderPane root = new BorderPane();
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String borderCol = isDarkMode ? "#666" : "#CCC";

        root.setStyle("-fx-background-color: " + bg + ";");

        // --- MERKEZ (İçerik) ---
        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));

        // 1. DROP ZONE
        VBox dropZone = new VBox(10);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(30));
        dropZone.setStyle("-fx-border-color: " + borderCol + "; -fx-border-style: dashed; -fx-border-width: 2; " +
                "-fx-background-color: " + panel + "; -fx-background-radius: 5; -fx-border-radius: 5;");

        Label lblInstruction = new Label("Drag and drop CSV files here");
        lblInstruction.setTextFill(Color.web(text));
        lblInstruction.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label lblSub = new Label("Supported Formats: Students, Courses, Classrooms, Attendance");
        lblSub.setStyle(
                "-fx-background-color: " + (isDarkMode ? "rgba(88, 166, 255, 0.1)" : "rgba(0, 90, 158, 0.1)") + ";" +
                        "-fx-text-fill: " + (isDarkMode ? "#58A6FF" : "#005A9E") + ";" +
                        "-fx-padding: 8 20 8 20;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-radius: 20;" +
                        "-fx-border-color: " + (isDarkMode ? "rgba(88, 166, 255, 0.3)" : "rgba(0, 90, 158, 0.3)") + ";"
                        +
                        "-fx-font-size: 11px; -fx-font-weight: bold;");
        lblSub.setAlignment(Pos.CENTER);
        lblSub.setMaxWidth(Double.MAX_VALUE);

        Button btnBrowse = new Button("Browse Files");
        btnBrowse.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-cursor: hand;");

        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null)
                processAndLoadFiles(files);
        });

        dropZone.getChildren().addAll(lblInstruction, new Label("- or -"), btnBrowse);

        // Drag & Drop Events
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                processAndLoadFiles(new ArrayList<>(db.getFiles()));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // 2. FILE LIST
        Label lblListHeader = new Label("Loaded Files (Select to Include):");
        lblListHeader.setTextFill(Color.web(text));
        lblListHeader.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        if (uploadedFilesList == null)
            uploadedFilesList = new ListView<>(uploadedFilesData);
        uploadedFilesList.setStyle("-fx-background-color: " + panel + "; -fx-control-inner-background: " + panel + ";");
        VBox.setVgrow(uploadedFilesList, Priority.ALWAYS);

        // List Cell Factory (Aynı kaldı)
        uploadedFilesList.setCellFactory(param -> new ListCell<UploadedFileItem>() {
            @Override
            protected void updateItem(UploadedFileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);
                    CheckBox cbSelect = new CheckBox();
                    cbSelect.selectedProperty().bindBidirectional(item.isSelected);

                    VBox infoBox = new VBox(2);
                    Label nameLabel = new Label(item.displayText.split("\n")[0]);
                    nameLabel.setTextFill(Color.web(text));
                    Label typeLabel = new Label(item.displayText.contains("(") ? item.displayText.split("\n")[1] : "");
                    typeLabel.setFont(Font.font("Arial", 10));
                    typeLabel.setTextFill(Color.gray(0.6));
                    infoBox.getChildren().addAll(nameLabel, typeLabel);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button btnRemove = new Button("✕");
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-background-color: transparent; -fx-font-weight: bold; -fx-cursor: hand;");
                    btnRemove.setOnAction(event -> {
                        if (showConfirmDialog("Remove File?", "Remove " + item.file.getName() + "?")) {
                            uploadedFilesData.remove(item);
                            loadedFileCache.remove(item.file);
                            DBManager.removeUploadedFile(item.file.getAbsolutePath());
                            allStudents.clear();
                            allCourses.clear();
                            allClassrooms.clear();
                            allEnrollments.clear();
                            studentScheduleMap.clear();
                            updateStats();
                            refreshActiveView();
                        }
                    });
                    box.getChildren().addAll(cbSelect, infoBox, spacer, btnRemove);
                    setGraphic(box);
                    setStyle("-fx-background-color: transparent; -fx-border-color: " + borderCol
                            + "; -fx-border-width: 0 0 1 0;");
                }
            }
        });

        contentBox.getChildren().addAll(lblSub, dropZone, new Separator(), lblListHeader, uploadedFilesList);
        root.setCenter(contentBox);

        // --- ALT KISIM (Bottom Bar) ---
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_LEFT); // Sola hizalı
        // Üst çizgi stili
        bottomBar.setStyle("-fx-background-color: " + panel + "; -fx-border-color: #666; -fx-border-width: 1 0 0 0;");

        Button btnClose = new Button("Close");
        // btnClose.setMaxWidth(Double.MAX_VALUE);
        btnClose.setStyle("-fx-background-color: " + btnBg + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        bottomBar.getChildren().add(btnClose);
        root.setBottom(bottomBar);

        Scene dialogScene = new Scene(root, 500, 650);
        dialogScene.getStylesheets().add(getThemeCSS());
        dialog.setScene(dialogScene);
        dialog.show();
    }

    private void showExportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Export Data");

        BorderPane root = new BorderPane();
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnBg = isDarkMode ? DARK_BTN : LIGHT_BTN;

        root.setStyle("-fx-background-color: " + bg + ";");

        // --- ÜST BİLGİ ---
        Label lblInfoTag = new Label("💡 Info: Choose 'Student List' for counts or 'Exam Schedule' for details.");
        lblInfoTag.setWrapText(true);
        lblInfoTag.setMaxWidth(Double.MAX_VALUE);
        lblInfoTag.setStyle(
                "-fx-background-color: " + (isDarkMode ? "rgba(88, 166, 255, 0.15)" : "rgba(0, 90, 158, 0.1)") + ";" +
                        "-fx-text-fill: " + (isDarkMode ? "#58A6FF" : "#005A9E") + ";" +
                        "-fx-padding: 15;" +
                        "-fx-font-size: 13px;");
        root.setTop(lblInfoTag);

        // --- MERKEZ (Form - GridPane Kullanımı) ---
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(15);
        grid.setHgap(10);

        // Etiketler
        Label lblType = new Label("Export Type:");
        lblType.setTextFill(Color.web(text));
        lblType.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        Label lblName = new Label("File Name:");
        lblName.setTextFill(Color.web(text));
        lblName.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        // Inputlar
        ComboBox<String> cmbType = new ComboBox<>(FXCollections.observableArrayList(
                "Student List",
                "Exam Schedule (Detailed per Student)",
                "Course Schedule (Exams Tab)",
                "Day Schedule"));
        cmbType.getSelectionModel().selectFirst();
        cmbType.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(cmbType, Priority.ALWAYS);

        TextField txtName = createStyledTextField("export_data");
        txtName.setText("export_data");
        Label lblExt = new Label(".csv");
        lblExt.setTextFill(Color.GRAY);
        HBox nameBox = new HBox(5, txtName, lblExt);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txtName, Priority.ALWAYS);

        // Gride ekleme
        grid.add(lblType, 0, 0);
        grid.add(cmbType, 1, 0);
        grid.add(lblName, 0, 1);
        grid.add(nameBox, 1, 1);

        // Kolon kısıtlamaları (Etiket kolonu sabit, input kolonu esnek)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(100);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        root.setCenter(grid);

        // --- ALT KISIM ---
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_LEFT); // Sola hizalı
        bottomBar.setStyle("-fx-background-color: " + panel + "; -fx-border-color: #666; -fx-border-width: 1 0 0 0;");

        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color: " + btnBg + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        // Araya boşluk koy
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnDoExport = new Button("Export CSV");
        btnDoExport
                .setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");

        // Export Aksiyonu (Aynı kaldı)
        btnDoExport.setOnAction(e -> {
            String type = cmbType.getValue();
            String defaultName = txtName.getText().trim();
            if (defaultName.isEmpty())
                defaultName = "export_data";
            if (!defaultName.toLowerCase().endsWith(".csv"))
                defaultName += ".csv";

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Export File");
            fileChooser.setInitialFileName(defaultName);
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

            File selectedFile = fileChooser.showSaveDialog(dialog);
            if (selectedFile != null) {
                if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
                    selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".csv");
                }
                boolean ok = exportData(type, selectedFile, true);
                Alert alert;
                if (ok) {
                    alert = new Alert(Alert.AlertType.INFORMATION,
                            "Export Successful!\n" + selectedFile.getAbsolutePath());
                } else {
                    alert = new Alert(Alert.AlertType.ERROR, "Export FAILED. Check logs.");
                }
                styleDialog(alert);
                alert.showAndWait();
                dialog.close();
            }
        });

        Button btnDoExportXlsx = new Button("Export Excel (.xlsx)");
        btnDoExportXlsx.setStyle("-fx-background-color: #1E8E3E; -fx-text-fill: white; -fx-font-weight: bold;");

        btnDoExportXlsx.setOnAction(e -> {
            String type = cmbType.getValue();
            String baseName = txtName.getText().trim();
            if (baseName.isEmpty()) baseName = "export_data";

            String defaultName = baseName;
            if (!defaultName.toLowerCase().endsWith(".xlsx")) defaultName += ".xlsx";

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Excel File");
            fileChooser.setInitialFileName(defaultName);
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));

            File selectedFile = fileChooser.showSaveDialog(dialog);
            if (selectedFile == null) return;
            if (!selectedFile.getName().toLowerCase().endsWith(".xlsx")) {
                selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".xlsx");
            }

            // Build rows in the same column order you use in CSV
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            List<String[]> rows = new ArrayList<>();

            try {
                if ("Student List".equals(type)) {
                    rows.add(new String[]{"Student ID", "Total Exams"});
                    for (Student s : allStudents) {
                        List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                        rows.add(new String[]{s.getId(), String.valueOf(exams.size())});
                    }
                } else if ("Exam Schedule (Detailed per Student)".equals(type)) {
                    rows.add(new String[]{"Student ID", "Course ID", "Date", "Time", "Room", "Seat"});

                    List<StudentExam> allStudentExams = new ArrayList<>();
                    for (List<StudentExam> list : studentScheduleMap.values()) {
                        allStudentExams.addAll(list);
                    }
                    allStudentExams.sort(Comparator.comparing(StudentExam::getStudentId));

                    for (StudentExam exam : allStudentExams) {
                        if (exam.getTimeslot() != null && timeslotMatchesFilters(exam.getTimeslot())) {
                            String dateStr = exam.getTimeslot().getDate().format(dtf);
                            String timeStr = exam.getTimeslot().getStart() + " - " + exam.getTimeslot().getEnd();
                            rows.add(new String[]{
                                    exam.getStudentId(),
                                    exam.getCourseId(),
                                    dateStr,
                                    timeStr,
                                    exam.getClassroomId(),
                                    String.valueOf(exam.getSeatNo())
                            });
                        }
                    }
                } else if ("Course Schedule (Exams Tab)".equals(type)) {
                    // Use the helper we already added (CourseCode, Date, Time, Rooms, Students, Status)
                    rows.add(new String[]{"Course Code", "Date", "Time", "Rooms", "Student Count", "Status"});
                    for (String[] r : buildScheduleExportRowsByCourse()) {
                        rows.add(r);
                    }
                } else if ("Day Schedule".equals(type)) {
                    rows.add(new String[]{"Date", "Time", "Room", "Course", "Student Count"});
                    for (DayRow r : masterDayList) {
                        rows.add(new String[]{
                                r.getDate(),
                                r.getTime(),
                                r.getRoom(),
                                r.getCourseId(),
                                String.valueOf(r.getStudentCount())
                        });
                    }
                }

                ExportOtherTypes.exportExcel(rows, selectedFile.toPath());

                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Export Successful!\n" + selectedFile.getAbsolutePath());
                styleDialog(alert);
                alert.showAndWait();
                dialog.close();

            } catch (Exception ex) {
                logError("Excel export failed: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Export FAILED. Check logs.\n" + ex.getMessage());
                styleDialog(alert);
                alert.showAndWait();
            }
        });

        Button btnDoExportPdf = new Button("Export PDF (.pdf)");
        btnDoExportPdf.setStyle("-fx-background-color: #0E639C; -fx-text-fill: white; -fx-font-weight: bold;");

        btnDoExportPdf.setOnAction(e -> {
            String type = cmbType.getValue();
            String baseName = txtName.getText().trim();
            if (baseName.isEmpty()) baseName = "export_data";

            String defaultName = baseName;
            if (!defaultName.toLowerCase().endsWith(".pdf")) defaultName += ".pdf";

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PDF File");
            fileChooser.setInitialFileName(defaultName);
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));

            File selectedFile = fileChooser.showSaveDialog(dialog);
            if (selectedFile == null) return;
            if (!selectedFile.getName().toLowerCase().endsWith(".pdf")) {
                selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".pdf");
            }

            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            List<String[]> rows = new ArrayList<>();

            try {
                if ("Student List".equals(type)) {
                    rows.add(new String[]{"Student ID", "Total Exams"});
                    for (Student s : allStudents) {
                        List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                        rows.add(new String[]{s.getId(), String.valueOf(exams.size())});
                    }
                } else if ("Exam Schedule (Detailed per Student)".equals(type)) {
                    rows.add(new String[]{"Student ID", "Course ID", "Date", "Time", "Room", "Seat"});

                    List<StudentExam> allStudentExams = new ArrayList<>();
                    for (List<StudentExam> list : studentScheduleMap.values()) {
                        allStudentExams.addAll(list);
                    }
                    allStudentExams.sort(Comparator.comparing(StudentExam::getStudentId));

                    for (StudentExam exam : allStudentExams) {
                        if (exam.getTimeslot() != null && timeslotMatchesFilters(exam.getTimeslot())) {
                            String dateStr = exam.getTimeslot().getDate().format(dtf);
                            String timeStr = exam.getTimeslot().getStart() + " - " + exam.getTimeslot().getEnd();
                            rows.add(new String[]{
                                    exam.getStudentId(),
                                    exam.getCourseId(),
                                    dateStr,
                                    timeStr,
                                    exam.getClassroomId(),
                                    String.valueOf(exam.getSeatNo())
                            });
                        }
                    }
                } else if ("Course Schedule (Exams Tab)".equals(type)) {
                    rows.add(new String[]{"Course Code", "Date", "Time", "Rooms", "Student Count", "Status"});
                    for (String[] r : buildScheduleExportRowsByCourse()) {
                        rows.add(r);
                    }
                } else if ("Day Schedule".equals(type)) {
                    rows.add(new String[]{"Date", "Time", "Room", "Course", "Student Count"});
                    for (DayRow r : masterDayList) {
                        rows.add(new String[]{
                                r.getDate(),
                                r.getTime(),
                                r.getRoom(),
                                r.getCourseId(),
                                String.valueOf(r.getStudentCount())
                        });
                    }
                }

                ExportOtherTypes.exportPdf(rows, selectedFile.toPath());

                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Export Successful!\n" + selectedFile.getAbsolutePath());
                styleDialog(alert);
                alert.showAndWait();
                dialog.close();

            } catch (Exception ex) {
                logError("PDF export failed: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Export FAILED. Check logs.\n" + ex.getMessage());
                styleDialog(alert);
                alert.showAndWait();
            }
        });

        bottomBar.getChildren().addAll(btnClose, spacer, btnDoExportPdf, btnDoExportXlsx, btnDoExport);
        root.setBottom(bottomBar);

        Scene s = new Scene(root, 450, 300);
        s.getStylesheets().add(getThemeCSS());
        dialog.setScene(s);
        dialog.show();
    }

    private void applyTheme() {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;

        // 1. Ana Arka Plan
        root.setStyle("-fx-background-color: " + bg + ";");
        root.setSnapToPixel(true);
        // 2. CSS Yükle (Zebra, Hover, Header, Scrollbar hepsi burada)
        if (primaryStage.getScene() != null) {
            primaryStage.getScene().getStylesheets().clear();
            primaryStage.getScene().getStylesheets().add(getThemeCSS());
        }

        // 3. Sol Paneli Temizle
        String inputBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String inputText = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String inputPrompt = isDarkMode ? DARK_PROMPT : LIGHT_PROMPT;
        String inputStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText
                + "; -fx-prompt-text-fill: " + inputPrompt + ";";

        if (txtDays != null)
            txtDays.setStyle(inputStyle);
        if (txtBlockTime != null)
            txtBlockTime.setStyle(inputStyle);
        if (txtTimeStart != null)
            txtTimeStart.setStyle(inputStyle);
        if (txtTimeEnd != null)
            txtTimeEnd.setStyle(inputStyle);
        if (txtSearch != null)
            txtSearch.setStyle(inputStyle);

        styleDatePicker(startDate, inputBg, inputText, inputPrompt);
        styleDatePicker(endDate, inputBg, inputText, inputPrompt);

        // Butonlar
        String btnStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText
                + "; -fx-background-radius: 4;";
        if (btnCustomize != null)
            btnCustomize.setStyle(btnStyle);
        if (btnHelp != null)
            btnHelp.setStyle(btnStyle);
        if (btnImport != null)
            btnImport.setStyle(btnStyle);
        if (btnExport != null)
            btnExport.setStyle(btnStyle);
        if (btnApply != null)
            btnApply.setStyle(btnStyle);

        // Toggle Butonları
        updateToggleStyles();

        // Listeyi Yenile
        if (uploadedFilesList != null)
            uploadedFilesList.refresh();

        // Aktif görünümü yenile
        if (currentDetailItem != null) {
            if (currentDetailItem instanceof Student)
                showStudentScheduleDetail((Student) currentDetailItem);
            else if (currentDetailItem instanceof Course)
                showCourseStudentList((Course) currentDetailItem);
            else if (currentDetailItem instanceof Classroom)
                showClassroomScheduleDetail((Classroom) currentDetailItem);
        } else {
            if (tglStudents.isSelected())
                showStudentList();
            else if (tglExams.isSelected())
                showExamList();
            else if (tglClassrooms != null && tglClassrooms.isSelected()) // EKLEME: Classroom sekmesini koru
                showClassroomList("");
            else if (tglDays.isSelected())
                showDayList();
        }
        // Bottom Bar İstatistik Renklendirmesi
        if (lblStats != null) {
            String color = isDarkMode ? "#EBCB8B" : "#0E639C";
            lblStats.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 13px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 2, 0, 0, 1);");
        }
    }

    // CSV/Excel için hücre kaçış karakteri yönetimi
    private String csvEscape(String s, String sep) {
        if (s == null)
            return "";
        // Eğer metin içinde ayırıcı, tırnak veya yeni satır varsa tırnak içine almalı
        boolean needQuotes = s.contains(sep) || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }

    // MainApp.java -> Yeni exportData metodu
    private boolean exportData(String type, File file, boolean forExcel) {
        if (file == null)
            return false;

        // Ayırıcı: Excel ise noktalı virgül (;), değilse virgül (,)
        String SEP = forExcel ? ";" : ",";

        // Tarih formatı (Örn: 15.12.2025)
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {

            // BOM (Türkçe karakterler için sihirli imza - Excel'in UTF-8 anlaması için)
            writer.write('\ufeff');

            // HEADER (Bilgi satırı)
            writer.write("Izmir University of Economics" + SEP + "Fall 2025" + SEP + "Version 1.0");
            writer.newLine();
            writer.newLine();

            // A) STUDENT LIST
            if ("Student List".equals(type)) {
                writer.write("Student ID" + SEP + "Total Exams");
                writer.newLine();

                for (Student s : allStudents) {
                    List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                    // exams = filterExamsByCurrentFilters(exams); // İstersen filtreyi açabilirsin
                    writer.write(csvEscape(s.getId(), SEP) + SEP + exams.size());
                    writer.newLine();
                }
            }
            // B) EXAM SCHEDULE (Detailed per Student)
            else if ("Exam Schedule (Detailed per Student)".equals(type)) {
                writer.write(
                        "Student ID" + SEP + "Course ID" + SEP + "Date" + SEP + "Time" + SEP + "Room" + SEP + "Seat");
                writer.newLine();

                // Sıralı çıktı için listeyi toparlayalım
                List<StudentExam> allStudentExams = new ArrayList<>();
                for (List<StudentExam> list : studentScheduleMap.values()) {
                    allStudentExams.addAll(list);
                }
                // Öğrenci ID'ye göre sırala
                allStudentExams.sort(Comparator.comparing(StudentExam::getStudentId));

                for (StudentExam exam : allStudentExams) {
                    if (exam.getTimeslot() != null && timeslotMatchesFilters(exam.getTimeslot())) {
                        String dateStr = exam.getTimeslot().getDate().format(dtf);
                        String timeStr = exam.getTimeslot().getStart() + " - " + exam.getTimeslot().getEnd();

                        StringBuilder sb = new StringBuilder();
                        sb.append(csvEscape(exam.getStudentId(), SEP)).append(SEP);
                        sb.append(csvEscape(exam.getCourseId(), SEP)).append(SEP);
                        sb.append(dateStr).append(SEP);
                        sb.append(timeStr).append(SEP);
                        sb.append(csvEscape(exam.getClassroomId(), SEP)).append(SEP);
                        sb.append(exam.getSeatNo());

                        writer.write(sb.toString());
                        writer.newLine();
                    }
                }
            }
            // C) COURSE SCHEDULE
            else if ("Course Schedule (Exams Tab)".equals(type)) {
                writer.write("Course Code" + SEP + "Duration (min)" + SEP + "Date" + SEP + "Time" + SEP + "Rooms" + SEP
                        + "Student Count" + SEP + "Status");
                writer.newLine();

                for (Course c : allCourses) {
                    String date = getCourseDate(c.getId());
                    // Tarih formatını düzeltmeye çalış (UNSCHEDULED değilse)
                    if (date != null && !date.equals("-") && !date.contains("UNSCHEDULED")) {
                        try {
                            date = java.time.LocalDate.parse(date).format(dtf);
                        } catch (Exception e) {
                            /* Format hatası olursa olduğu gibi kalsın */ }
                    }

                    String time = getCourseTimeRange(c.getId());
                    String rooms = getCourseRooms(c.getId());

                    String status = getCourseStatusText(c.getId());

                    int count = getCourseStudentCount(c.getId());

                    // Verileri String.format ile güvenli bir şekilde birleştiriyoruz
                    // csvEscape metoduna SEP (;) gönderiyoruz ki çakışma olmasın
                    String line = String.format("%s;%d;%s;%s;%s;%d;%s",
                            csvEscape(c.getId(), SEP),
                            c.getDurationMinutes(),
                            csvEscape(date, SEP),
                            csvEscape(time, SEP),
                            csvEscape(rooms, SEP),
                            count,
                            csvEscape(status, SEP));

                    writer.write(line);
                    writer.newLine();
                }
            }
            // D) DAY SCHEDULE
            else if ("Day Schedule".equals(type)) {
                writer.write("Date" + SEP + "Time" + SEP + "Room" + SEP + "Course" + SEP + "Student Count");
                writer.newLine();

                for (DayRow row : masterDayList) {
                    writer.write(csvEscape(row.getDate(), SEP) + SEP +
                            csvEscape(row.getTime(), SEP) + SEP +
                            csvEscape(row.getRoom(), SEP) + SEP +
                            csvEscape(row.getCourseId(), SEP) + SEP +
                            row.getStudentCount());
                    writer.newLine();
                }
            }

            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void exportSingleStudentSchedule(Student student) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Student Schedule");
        fileChooser.setInitialFileName(student.getId() + "_Schedule.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (Excel)", "*.csv"));

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            // UTF-8 karakter kodlaması ile yazıcı oluştur
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {

                writer.write('\ufeff'); // Excel için BOM (Türkçe karakterlerin düzgün görünmesi için)

                String SEP = ";"; // Excel için ayırıcı olarak noktalı virgül kullanıyoruz

                // Başlık Satırı
                writer.write(
                        "Student ID" + SEP + "Course ID" + SEP + "Date" + SEP + "Time" + SEP + "Room" + SEP + "Seat");
                writer.newLine();

                List<StudentExam> exams = studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());
                exams = filterExamsByCurrentFilters(exams);

                for (StudentExam exam : exams) {

                    StringBuilder sb = new StringBuilder();

                    sb.append(csvEscape(student.getId(), SEP)).append(SEP);
                    sb.append(csvEscape(exam.getCourseId(), SEP)).append(SEP);
                    sb.append(csvEscape(exam.getTimeslot().getDate().toString(), SEP)).append(SEP);
                    sb.append(csvEscape(exam.getTimeslot().getStart() + " - " + exam.getTimeslot().getEnd(), SEP))
                            .append(SEP);
                    sb.append(csvEscape(exam.getClassroomId(), SEP)).append(SEP);
                    sb.append(exam.getSeatNo());

                    writer.write(sb.toString());
                    writer.newLine();
                }

                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Export Successful!\nPath: " + file.getAbsolutePath());
                styleDialog(alert);
                alert.show();

            } catch (IOException ex) {
                logError("Export failed: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
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

        // Ortak stil
        String common = "-fx-cursor: hand; -fx-padding: 8px 15px; -fx-font-size: 13px; -fx-border-color: " + border
                + ";";

        // Seçili ve Seçisiz renkler
        String selectedStyle = "-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;";
        String normalStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + ";";

        // 1. STUDENTS (En Sol - Sol tarafı oval)
        tglStudents.setStyle(common + (tglStudents.isSelected() ? selectedStyle : normalStyle) +
                "-fx-background-radius: 5 0 0 5; -fx-border-radius: 5 0 0 5; -fx-border-width: 1 0 1 1;");

        // 2. EXAMS (Orta - Kare)
        tglExams.setStyle(common + (tglExams.isSelected() ? selectedStyle : normalStyle) +
                "-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");

        // 3. CLASSROOMS (Orta - Kare - EKLENDİ)
        if (tglClassrooms != null) {
            tglClassrooms.setStyle(common + (tglClassrooms.isSelected() ? selectedStyle : normalStyle) +
                    "-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");
        }

        // 4. DAYS (En Sağ - Sağ tarafı oval)
        tglDays.setStyle(common + (tglDays.isSelected() ? selectedStyle : normalStyle) +
                "-fx-background-radius: 0 5 5 0; -fx-border-radius: 0 5 5 0; -fx-border-width: 1 1 1 1;");
    }

    private Label createDescriptionLabel(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true); // Metin uzunsa alt satıra geçsin
        lbl.getStyleClass().add("description-label"); // CSS sınıfını ata
        lbl.setMaxWidth(250); // Panelin dışına taşmasın
        return lbl;
    }

    // Tablo boşken gösterilecek mesajı duruma göre belirleyen metot
    private Node getTablePlaceholder() {
        String text;
        boolean filesLoaded = !uploadedFilesData.isEmpty(); // Dosya listesi dolu mu?
        boolean scheduleExists = !studentScheduleMap.isEmpty(); // Takvim oluşturulmuş mu?

        if (!filesLoaded) {
            // 1. Durum: Hiç dosya yok
            text = "No data loaded.\nClick 'Import' to load CSV files.";
        } else if (!scheduleExists) {
            // 2. Durum: Dosyalar var ama Apply yapılmamış (Senin istediğin durum)
            text = "Files loaded successfully.\nClick 'Apply Schedule' to generate the plan.";
        } else {
            // 3. Durum: Takvim var ama filtreleme sonucu boş (Arama yapınca çıkmazsa)
            text = "No results found matching your search.";
        }

        Label placeholder = new Label(text);
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        placeholder.setTextFill(Color.GRAY);
        placeholder.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        return placeholder;
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

        table.setStyle("");

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Placeholder (Boş tablo yazısı) rengi
        if (table.getPlaceholder() != null && table.getPlaceholder() instanceof Label) {
            ((Label) table.getPlaceholder()).setTextFill(Color.GRAY);
        }
    }

    private VBox createCard(String title, String description, String helpContent, Node... nodes) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.getStyleClass().add("card-pane");
        // Başlığa göre farklı renk ataması
        String accentColor;
        if (title.contains("Period"))
            accentColor = "#0078D7";
        else if (title.contains("Constraints"))
            accentColor = "#D97706";
        else
            accentColor = "#7C3AED";

        card.setStyle(
                card.getStyle() + "-fx-border-width: 0 0 0 4; -fx-border-color: transparent transparent transparent "
                        + accentColor + ";");

        // 1. BAŞLIK VE YARDIM BUTONU (Hizalama eklendi)
        if (title != null) {
            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            // Sembol Belirleme
            String iconSymbol = "";
            // Koyu modda daha parlak, açık modda daha koyu mavi
            String iconColor = isDarkMode ? "#58A6FF" : "#005A9E";

            if (title.contains("Constraints"))
                iconSymbol = "\u23F1";
            else if (title.contains("Customization"))
                iconSymbol = "\u2699";

            Label lblIcon = new Label(iconSymbol);
            // Bulanıklığı önlemek için font pürüzsüzleştirme eklendi
            lblIcon.setStyle("-fx-font-size: 18px; -fx-text-fill: " + iconColor + "; -fx-font-smoothing-type: lcd;");

            // Başlıktaki eski emoji karakterlerini temizle
            Label lblTitle = new Label(title.replaceAll("[^\u0000-\u007F]", "").trim());
            lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 15));
            lblTitle.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));
            lblTitle.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-background-insets: 0;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Yardım butonu (?) stilini koru
            Button btnInfo = new Button("?");
            btnInfo.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; " +
                    "-fx-background-radius: 12; -fx-font-size: 10px; -fx-font-weight: bold; " +
                    "-fx-padding: 2 7 2 7; -fx-cursor: hand;");

            btnInfo.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title.trim() + " Help");
                alert.setHeaderText(title.trim() + " Guide");
                alert.setContentText(helpContent);
                styleDialog(alert);
                alert.showAndWait();
            });

            titleRow.getChildren().addAll(lblIcon, lblTitle, spacer, btnInfo);
            card.getChildren().add(titleRow); // BURASI ÇOK ÖNEMLİ: titleRow'u karta ekliyoruz
        }
        Separator sep = new Separator();
        sep.setStyle("-fx-opacity: 0.3;"); // Çok baskın olmaması için biraz şeffaflaştırdık
        card.getChildren().add(sep);

        // 2. AÇIKLAMA (Alt başlık)
        if (description != null && !description.isEmpty()) {
            Label descLbl = createDescriptionLabel(description);
            descLbl.setStyle("-fx-padding: 2 0 5 0; -fx-font-size: 12px; -fx-text-fill: "
                    + (isDarkMode ? "#AAAAAA" : "#666666") + ";");
            card.getChildren().add(descLbl);
        }

        // 3. İÇERİK (Inputlar vb.)
        VBox contentBox = new VBox(10);
        contentBox.getChildren().addAll(nodes);
        card.getChildren().add(contentBox);

        return card;
    }

    private void styleDialog(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();
        // 1. Temayı yükle (Base64 CSS)
        dialogPane.getStylesheets().add(getThemeCSS());

        // 2. Ekstra stil sınıfı (Gerekirse)
        dialogPane.getStyleClass().add("my-dialog");
    }

    // ToggleSwitch Class
    private static class ToggleSwitch extends StackPane {
        private final Rectangle background;
        private final Circle trigger;
        private final BooleanProperty switchedOn = new SimpleBooleanProperty(false);

        // Animasyonlar
        private final TranslateTransition translateAnimation;
        private final FillTransition backgroundFillAnimation;
        private final FillTransition triggerFillAnimation;
        private final ParallelTransition animation;

        public ToggleSwitch(boolean initialValue) {
            switchedOn.set(initialValue);
            double width = 50, height = 28, radius = 12;

            // 1. Arka Plan (Pist)
            background = new Rectangle(width, height);
            background.setArcWidth(height);
            background.setArcHeight(height);
            background.setStroke(Color.LIGHTGRAY);

            // 2. Hareketli Yuvarlak (Trigger)
            trigger = new Circle(radius);
            trigger.setEffect(new DropShadow(2, Color.gray(0.2)));

            // 3. İkon (Etiket)
            String iconSymbol = initialValue ? "☾" : "☀";
            String iconColor = initialValue ? "white" : "#FFA500";

            Label iconLabel = new Label(iconSymbol);
            iconLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + iconColor + ";");
            iconLabel.mouseTransparentProperty().set(true);
            StackPane.setAlignment(iconLabel, Pos.CENTER);

            getChildren().addAll(background, trigger, iconLabel);

            // 4. Animasyon Tanımları
            translateAnimation = new TranslateTransition(Duration.seconds(0.25));
            backgroundFillAnimation = new FillTransition(Duration.seconds(0.25), background);
            triggerFillAnimation = new FillTransition(Duration.seconds(0.25), trigger);

            animation = new ParallelTransition(translateAnimation, backgroundFillAnimation, triggerFillAnimation);

            // 5. Başlangıç Durumunu Ayarla
            if (initialValue) { // Koyu Mod
                trigger.setTranslateX(width / 2 - radius - 2);
                iconLabel.setTranslateX(11);

                background.setFill(Color.web("#0E639C")); // Mavi Arka Plan
                background.setStroke(Color.web("#0E639C"));

                trigger.setFill(Color.web("#3A3D41"));
            } else { // Açık Mod
                trigger.setTranslateX(-(width / 2 - radius - 2));
                iconLabel.setTranslateX(-11);

                background.setFill(Color.web("#E9E9EA")); // Gri Arka Plan
                background.setStroke(Color.web("#E9E9EA"));

                trigger.setFill(Color.WHITE); // Beyaz Yuvarlak
            }

            // 6. Tıklama Olayı
            setOnMouseClicked(event -> switchedOn.set(!switchedOn.get()));

            // 7. Durum Değişikliği Dinleyicisi
            switchedOn.addListener((obs, oldState, newState) -> {
                boolean isOn = newState;
                double targetX = isOn ? 11 : -11;

                iconLabel.setText(isOn ? "☾" : "☀");
                String newIconColor = isOn ? "white" : "#FFA500";
                iconLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + newIconColor + ";");

                TranslateTransition iconTransit = new TranslateTransition(Duration.seconds(0.25), iconLabel);
                iconTransit.setToX(targetX);
                iconTransit.play();

                translateAnimation.setNode(trigger);
                translateAnimation.setToX(isOn ? width / 2 - radius - 2 : -(width / 2 - radius - 2));

                triggerFillAnimation.setToValue(isOn ? Color.web("#3A3D41") : Color.WHITE);

                backgroundFillAnimation.setToValue(isOn ? Color.web("#0E639C") : Color.web("#E9E9EA"));

                animation.play();
            });
        }

        public BooleanProperty switchOnProperty() {
            return switchedOn;
        }
    }

    // İçe aktarılan dosyanın seçili olup olmadığını tutan sınıf
    private static class UploadedFileItem {
        File file;
        String displayText;
        BooleanProperty isSelected = new SimpleBooleanProperty(true); // Varsayılan seçili

        public UploadedFileItem(File file, String text) {
            this.file = file;
            this.displayText = text;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    // Arama yapabilmek için günlük veriyi önceden hazırlar
    private void buildMasterDayList() {
        Map<String, DayRow> map = new LinkedHashMap<>();
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                Timeslot ts = se.getTimeslot();
                if (ts == null || !timeslotMatchesFilters(ts))
                    continue;

                String dateStr = ts.getDate().toString();
                String timeStr = ts.getStart().toString() + " - " + ts.getEnd().toString();
                String key = dateStr + "|" + timeStr + "|" + se.getClassroomId() + "|" + se.getCourseId();

                DayRow row = map.get(key);
                if (row == null) {
                    map.put(key, new DayRow(dateStr, timeStr, se.getClassroomId(), se.getCourseId(), 1));
                } else {
                    row.increment();
                }
            }
        }

        List<DayRow> rows = new ArrayList<>(map.values());
        // Sıralama
        rows.sort(Comparator.comparing(DayRow::getDate)
                .thenComparing(DayRow::getTime)
                .thenComparing(DayRow::getRoom));

        masterDayList.setAll(rows);
    }

    // ==== DAY VIEW İÇİN SATIR MODELİ ====
    public static class DayRow {
        private final String date;
        private final String time;
        private final String room;
        private final String courseId;
        private int studentCount;

        public DayRow(String date, String time, String room, String courseId, int studentCount) {
            this.date = date;
            this.time = time;
            this.room = room;
            this.courseId = courseId;
            this.studentCount = studentCount;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public String getRoom() {
            return room;
        }

        public String getCourseId() {
            return courseId;
        }

        public int getStudentCount() {
            return studentCount;
        }

        public void increment() {
            this.studentCount++;
        }
    }

    private Set<String> extractBottleneckStudents(Map<String, String> reasons) {
        Set<String> out = new HashSet<>();
        if (reasons == null || reasons.isEmpty())
            return out;

        for (String reason : reasons.values()) {
            if (reason == null)
                continue;
            int idx = reason.indexOf("Bottleneck students:");
            if (idx < 0)
                continue;

            String tail = reason.substring(idx + "Bottleneck students:".length()).trim();
            if (tail.isEmpty())
                continue;

            String[] parts = tail.split(",");
            for (String p : parts) {
                String token = p.trim();
                if (token.isEmpty())
                    continue;
                int paren = token.indexOf('(');
                String sid = (paren > 0) ? token.substring(0, paren).trim() : token;
                if (!sid.isEmpty())
                    out.add(sid);
            }
        }
        return out;
    }

    // YARDIMCI: DOĞAL SIRALAMA (Natural Sort Comparator)

    private int naturalCompare(String s1, String s2) {
        if (s1 == null || s2 == null)
            return 0;

        String clean1 = s1.replaceAll("\\d+", "");
        String clean2 = s2.replaceAll("\\d+", "");

        // Eğer metin kısımları farklıysa normal sırala
        int txtComp = clean1.compareTo(clean2);
        if (txtComp != 0)
            return txtComp;

        // Metinler aynıysa sayıları çekip karşılaştır
        try {
            // String içindeki tüm sayıları çek
            String num1Str = s1.replaceAll("\\D+", "");
            String num2Str = s2.replaceAll("\\D+", "");

            if (num1Str.isEmpty() || num2Str.isEmpty())
                return s1.compareTo(s2);

            // Long'a çevirip karşılaştır (05 ile 5 eşit olsun diye)
            Long n1 = Long.parseLong(num1Str);
            Long n2 = Long.parseLong(num2Str);
            return n1.compareTo(n2);
        } catch (NumberFormatException e) {
            return s1.compareTo(s2);
        }
    }

    // ADVANCED EXAM CUSTOMIZATION

    private void showCustomizationDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Customize Exam Rules");

        BorderPane mainLayout = new BorderPane();
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        String btnBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        mainLayout.setStyle("-fx-background-color: " + bg + ";");

        Label headerDesc = new Label("Add custom constraints for specific courses (e.g., Duration, Capacity).");
        headerDesc.setWrapText(true);
        headerDesc.setTextFill(Color.web(isDarkMode ? "#AAAAAA" : "#666666"));
        headerDesc.setPadding(new Insets(15));
        mainLayout.setTop(headerDesc);

        VBox groupsContainer = new VBox(10);
        groupsContainer.setPadding(new Insets(10));
        groupsContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(groupsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + bg + ";");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // --- ALT BUTONLAR ---
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_LEFT); // Sola hizalı
        bottomBar.setStyle("-fx-background-color: " + (isDarkMode ? DARK_PANEL : LIGHT_PANEL)
                + "; -fx-border-color: #666; -fx-border-width: 1 0 0 0;");

        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color: " + btnBg + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        // Araya boşluk koy
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAddGroup = createStyledButton("+ Add Rule");
        btnAddGroup.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");

        Button btnApplyAll = createStyledButton("Save & Regenerate");
        btnApplyAll
                .setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");

        // Actions
        btnAddGroup.setOnAction(e -> {
            RuleGroupPane groupPane = new RuleGroupPane(groupsContainer);
            groupsContainer.getChildren().add(groupPane);
            ruleGroups.add(groupPane);
        });

        btnApplyAll.setOnAction(e -> {
            for (RuleGroupPane pane : ruleGroups)
                pane.saveToDB(0);
            dialog.close();
            runSchedulerLogic(true);
        });

        // Content Logic
        groupsContainer.getChildren().clear();
        if (ruleGroups.isEmpty()) {
            RuleGroupPane initialPane = new RuleGroupPane(groupsContainer);
            groupsContainer.getChildren().add(initialPane);
            ruleGroups.add(initialPane);
        } else {
            for (RuleGroupPane pane : ruleGroups) {
                if (pane.getParent() != null)
                    ((Pane) pane.getParent()).getChildren().remove(pane);
                groupsContainer.getChildren().add(pane);
                pane.setParentContainer(groupsContainer);
            }
        }

        bottomBar.getChildren().addAll(btnClose, spacer, btnAddGroup, btnApplyAll);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(bottomBar);

        Scene scene = new Scene(mainLayout, 650, 600);
        scene.getStylesheets().add(getThemeCSS());
        dialog.setScene(scene);
        dialog.show();
    }

    // HELPER CLASS: Kural Grubu Paneli (İç Sınıf)

    private class RuleGroupPane extends VBox {
        private final List<Course> selectedCourses = new ArrayList<>();

        private final Label lblSelectionInfo;
        private final TextField txtDuration;
        private final TextField txtMinCap;
        private final TextField txtMaxCap;

        private final CheckBox cbIgnore;

        private VBox parentContainer;

        public RuleGroupPane(VBox parent) {
            this.parentContainer = parent;

            setSpacing(10);
            setPadding(new Insets(15));
            String panelColor = isDarkMode ? "#2A2A2A" : "#FFFFFF";
            String borderColor = isDarkMode ? "#555" : "#CCC";
            setStyle("-fx-background-color: " + panelColor + "; -fx-border-color: " + borderColor
                    + "; -fx-border-radius: 5; -fx-background-radius: 5;");

            // 1. Başlık
            HBox topRow = new HBox();
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label("Rule Group");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            title.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button btnRemove = new Button("Remove");
            btnRemove.setStyle(
                    "-fx-background-color: transparent; -fx-text-fill: #FF6B6B; -fx-border-color: #FF6B6B; -fx-border-radius: 3;");
            btnRemove.setOnAction(e -> removeSelf());
            topRow.getChildren().addAll(title, spacer, btnRemove);

            // 2. Seçim Butonu
            HBox selectionRow = new HBox(10);
            selectionRow.setAlignment(Pos.CENTER_LEFT);
            Button btnSelectCourses = new Button("Select Courses...");
            btnSelectCourses.setStyle("-fx-background-color: #444; -fx-text-fill: white;");
            btnSelectCourses.setOnAction(e -> openMultiSelectDialog());
            lblSelectionInfo = new Label("No courses selected");
            lblSelectionInfo.setTextFill(Color.GRAY);
            selectionRow.getChildren().addAll(btnSelectCourses, lblSelectionInfo);

            // 3. Ayarlar Grid'i
            GridPane settingsGrid = new GridPane();
            settingsGrid.setHgap(10);
            settingsGrid.setVgap(10);
            String promptColor = isDarkMode ? DARK_PROMPT : LIGHT_PROMPT;
            String inputBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
            String inputText = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
            String commonStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText
                    + "; -fx-prompt-text-fill: " + promptColor + ";";

            // Duration
            Label lblDur = new Label("Duration (min):");
            lblDur.setTextFill(Color.web(inputText));
            txtDuration = new TextField();
            txtDuration.setPromptText("Keep Orig.");
            txtDuration.setStyle(commonStyle);
            txtDuration.setPrefWidth(90);

            // Min Cap
            Label lblMin = new Label("Min Room Cap:");
            lblMin.setTextFill(Color.web(inputText));
            txtMinCap = new TextField();
            txtMinCap.setPromptText("0 (Any)");
            txtMinCap.setStyle(commonStyle);
            txtMinCap.setPrefWidth(90);

            // Max Cap
            Label lblMax = new Label("Max Room Cap:");
            lblMax.setTextFill(Color.web(inputText));
            txtMaxCap = new TextField();
            txtMaxCap.setPromptText("0 (No Limit)");
            txtMaxCap.setStyle(commonStyle);
            txtMaxCap.setPrefWidth(90);

            // --- Exclude Checkbox ---
            cbIgnore = new CheckBox("Exclude from Schedule (Ignore)");
            cbIgnore.setTextFill(Color.web(isDarkMode ? "#FF6B6B" : "#D32F2F")); // Kırmızımsı renk
            cbIgnore.setFont(Font.font("Arial", FontWeight.BOLD, 12));

            // İşaretlenirse diğer kutucukları kapat
            cbIgnore.selectedProperty().addListener((obs, oldVal, newVal) -> {
                txtDuration.setDisable(newVal);
                txtMinCap.setDisable(newVal);
                txtMaxCap.setDisable(newVal);
            });

            // Grid Yerleşimi
            settingsGrid.add(lblDur, 0, 0);
            settingsGrid.add(txtDuration, 0, 1);

            settingsGrid.add(lblMin, 1, 0);
            settingsGrid.add(txtMinCap, 1, 1);

            settingsGrid.add(lblMax, 2, 0);
            settingsGrid.add(txtMaxCap, 2, 1);

            // Checkbox'ı en alta, tüm genişliğe yayarak ekle
            settingsGrid.add(cbIgnore, 0, 2, 3, 1);

            getChildren().addAll(topRow, new Separator(), selectionRow, settingsGrid);
        }

        public void setParentContainer(VBox newParent) {
            this.parentContainer = newParent;
        }

        private void removeSelf() {
            parentContainer.getChildren().remove(this);
            ruleGroups.remove(this);
        }

        private void updateLabel() {
            lblSelectionInfo.setText(selectedCourses.size() + " courses selected");
        }

        private void openMultiSelectDialog() {
            Stage subStage = new Stage();
            subStage.initModality(Modality.APPLICATION_MODAL);
            subStage.setTitle("Select Courses");

            VBox root = new VBox(10);
            root.setPadding(new Insets(10));
            String bg = isDarkMode ? DARK_BG : LIGHT_BG;
            String listBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
            String listText = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
            root.setStyle("-fx-background-color: " + bg + ";");

            // Çakışma Kontrolü
            Set<String> unavailableCourseIds = new HashSet<>();
            for (RuleGroupPane otherPane : ruleGroups) {
                if (otherPane == this)
                    continue;
                for (Course c : otherPane.selectedCourses) {
                    unavailableCourseIds.add(c.getId());
                }
            }

            TextField search = createStyledTextField("Search...");
            ListView<Course> listView = new ListView<>();
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.setStyle("-fx-background-color: " + listBg + "; -fx-control-inner-background: " + listBg + ";");

            ObservableList<Course> items = FXCollections.observableArrayList(allCourses);
            items.sort((c1, c2) -> naturalCompare(c1.getId(), c2.getId()));
            listView.setItems(items);

            listView.setCellFactory(lv -> new ListCell<Course>() {
                @Override
                protected void updateItem(Course item, boolean empty) {
                    super.updateItem(item, empty);
                    String cellStyle = "-fx-background-color: " + (empty ? "transparent" : listBg) + "; "
                            + "-fx-text-fill: " + listText + ";";
                    setStyle(cellStyle);

                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        CheckBox cb = new CheckBox();
                        boolean isTaken = unavailableCourseIds.contains(item.getId());
                        boolean isSelectedHere = selectedCourses.stream().anyMatch(c -> c.getId().equals(item.getId()));

                        if (isTaken) {
                            cb.setDisable(true);
                            cb.setText(item.getId() + " (Used)");
                            cb.setTextFill(Color.GRAY);
                            cb.setSelected(false);
                        } else {
                            cb.setDisable(false);
                            cb.setText(item.getId());
                            cb.setTextFill(Color.web(listText));
                            cb.setSelected(isSelectedHere);
                        }

                        cb.setOnAction(e -> {
                            if (!isTaken) {
                                if (cb.isSelected()) {
                                    if (selectedCourses.stream().noneMatch(c -> c.getId().equals(item.getId()))) {
                                        selectedCourses.add(item);
                                    }
                                } else {
                                    selectedCourses.removeIf(c -> c.getId().equals(item.getId()));
                                }
                                updateLabel();
                            }
                        });
                        setGraphic(cb);
                        setText(null);
                    }
                }
            });

            // Arama
            search.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    listView.setItems(items);
                } else {
                    ObservableList<Course> filtered = items.stream()
                            .filter(c -> c.getId().toLowerCase().contains(newVal.toLowerCase()))
                            .collect(Collectors.toCollection(FXCollections::observableArrayList));
                    listView.setItems(filtered);
                }
            });

            Button btnDone = new Button("Done");
            btnDone.setMaxWidth(Double.MAX_VALUE);
            btnDone.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");
            btnDone.setOnAction(e -> subStage.close());

            root.getChildren().addAll(search, listView, btnDone);
            Scene scene = new Scene(root, 400, 500);
            subStage.setScene(scene);
            subStage.showAndWait();
        }

        // --- GÜNCELLENEN LOGIC METODU ---
        public int applyRulesToSelectedCourses() {
            if (selectedCourses.isEmpty())
                return 0;

            boolean ignore = cbIgnore.isSelected(); // Checkbox durumu
            int durationVal = -1;
            int minCapVal = -1;
            int maxCapVal = -1;

            if (!ignore) {
                try {
                    if (!txtDuration.getText().trim().isEmpty())
                        durationVal = Integer.parseInt(txtDuration.getText().trim());
                    if (!txtMinCap.getText().trim().isEmpty())
                        minCapVal = Integer.parseInt(txtMinCap.getText().trim());
                    if (!txtMaxCap.getText().trim().isEmpty())
                        maxCapVal = Integer.parseInt(txtMaxCap.getText().trim());
                } catch (NumberFormatException e) {
                }
            }

            for (Course selectedC : selectedCourses) {
                for (Course realC : allCourses) {
                    if (realC.getId().equals(selectedC.getId())) {
                        // 1. Ignore durumunu işle
                        realC.setIgnored(ignore);

                        // 2. Ignore değilse diğer kuralları uygula
                        if (!ignore) {
                            if (durationVal > 0)
                                realC.setDurationMinutes(durationVal);
                            if (minCapVal >= 0)
                                realC.setMinRoomCapacity(minCapVal);
                            if (maxCapVal >= 0)
                                realC.setMaxRoomCapacity(maxCapVal);
                        }
                        break;
                    }
                }
            }
            return selectedCourses.size();
        }

        public void saveToDB(int groupId) {
            int d = -1, min = -1, max = -1;
            try {
                if (!txtDuration.getText().isEmpty())
                    d = Integer.parseInt(txtDuration.getText());
                if (!txtMinCap.getText().isEmpty())
                    min = Integer.parseInt(txtMinCap.getText());
                if (!txtMaxCap.getText().isEmpty())
                    max = Integer.parseInt(txtMaxCap.getText());
            } catch (Exception e) {
            }

            for (Course c : selectedCourses) {
                DBManager.saveRule(groupId, c.getId(), d, min, max);
            }
        }

        public void restoreSettings(int d, int min, int max, List<Course> coursesToSelect) {
            if (d > 0)
                txtDuration.setText(String.valueOf(d));
            if (min >= 0)
                txtMinCap.setText(String.valueOf(min));
            if (max > 0)
                txtMaxCap.setText(String.valueOf(max));

            this.selectedCourses.clear();
            this.selectedCourses.addAll(coursesToSelect);

            updateLabel();
        }
    }

    // CUSTOM CONFIRMATION DIALOG

    private boolean showConfirmDialog(String title, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage); // Ana pencerenin ortasında açılsın
        dialog.setTitle(title);
        dialog.setResizable(false);

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPadding(new Insets(20));

        // Temayı Uygula
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnBg = isDarkMode ? DARK_BTN : LIGHT_BTN;

        layout.setStyle("-fx-background-color: " + bg + "; -fx-border-color: #666; -fx-border-width: 1;");

        // Başlık
        Label lblTitle = new Label(title);
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        lblTitle.setTextFill(Color.web(text));

        // Mesaj
        Label lblMsg = new Label(message);
        lblMsg.setTextFill(Color.web(text));
        lblMsg.setWrapText(true);
        lblMsg.setMaxWidth(300);

        // Butonlar
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-color: " + btnBg + "; -fx-text-fill: " + text + ";");

        Button btnOk = new Button("Remove");
        btnOk.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold;"); // Kırmızı vurgu

        final boolean[] result = { false };

        btnCancel.setOnAction(e -> dialog.close());
        btnOk.setOnAction(e -> {
            result[0] = true;
            dialog.close();
        });

        btnBox.getChildren().addAll(btnCancel, btnOk);
        layout.getChildren().addAll(lblTitle, new Separator(), lblMsg, btnBox);

        Scene scene = new Scene(layout, 350, 180);
        dialog.setScene(scene);

        // Bekle ve sonucu dön
        dialog.showAndWait();
        return result[0];
    }

    private void loadSavedFilesList() {
        List<String> filePaths = DBManager.loadUploadedFiles();

        for (String path : filePaths) {
            File f = new File(path);
            if (f.exists()) {
                String name = f.getName();
                String type = "Unknown"; // Basit tip tahmini
                if (name.toLowerCase().contains("student"))
                    type = "Students";
                else if (name.toLowerCase().contains("course"))
                    type = "Courses";
                else if (name.toLowerCase().contains("capacities") || name.toLowerCase().contains("classroom"))
                    type = "Rooms";
                else if (name.toLowerCase().contains("attendance"))
                    type = "Links";

                // Varsayılan olarak tikli gelir
                UploadedFileItem item = new UploadedFileItem(f, name + "\n(" + type + ")");
                uploadedFilesData.add(item);
                loadedFileCache.add(f);
            }
        }
    }

    // Ayarları ve kuralları veritabanına kaydeder (Dosyaları değil)
    private void saveCurrentState() {
        System.out.println("Saving app state...");

        // 1. Ayarları Kaydet (Settings Tablosu)
        DBManager.saveSetting("days", txtDays.getText());
        DBManager.saveSetting("blockTime", txtBlockTime.getText());
        DBManager.saveSetting("timeStart", txtTimeStart.getText());
        DBManager.saveSetting("timeEnd", txtTimeEnd.getText());
        if (startDate.getValue() != null)
            DBManager.saveSetting("startDate", startDate.getValue().toString());
        if (endDate.getValue() != null)
            DBManager.saveSetting("endDate", endDate.getValue().toString());

        // 2. Kural Gruplarını Kaydet (Rules Tablosu)
        DBManager.clearRules();
        int gIndex = 0;
        for (RuleGroupPane pane : ruleGroups) {
            pane.saveToDB(gIndex++);
        }
    }

    // HELP / USER GUIDE DIALOG (DETAILED VERSION)

    private void showHelpDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);
        dialog.setTitle("Application Guide & Help");

        BorderPane root = new BorderPane();
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String btnBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
        root.setStyle("-fx-background-color: " + bg + ";");

        // --- İÇERİK ---
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        Label mainHeader = new Label("Exam Management System Guide");
        mainHeader.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        mainHeader.setTextFill(Color.web(text));
        content.getChildren().add(mainHeader);

        String headerColor = ACCENT_COLOR;
        content.getChildren().add(createHelpSection("1. Toolbar (Top Menu)",
                "• Errors: Shows error count. Click for logs.\n" +
                        "• Import: Load CSV files.\n" +
                        "• Export: Save schedule as CSV.\n" +
                        "• Apply: Run scheduling algorithm.\n" +
                        "• Search: Filter tables.",
                headerColor, text));

        content.getChildren().add(createHelpSection("2. Filter Options",
                "• Duration: Exam period length.\n" +
                        "• Working Hours: Daily limits (e.g. 09:00-17:00).",
                headerColor, text));

        content.getChildren().add(createHelpSection("3. Customize Rules",
                "Manually override settings for specific courses.", headerColor, text));

        content.getChildren().add(createHelpSection("⚠️ Troubleshooting",
                "Red text means UNSCHEDULED. Check error logs.",
                isDarkMode ? "#FF6B6B" : "#D32F2F", text));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: " + bg + "; -fx-background-color: transparent;");
        root.setCenter(scrollPane);

        // --- ALT KISIM ---
        HBox bottomBar = new HBox();
        bottomBar.setAlignment(Pos.CENTER_LEFT); // Sola hizalı
        bottomBar.setPadding(new Insets(15));
        // Üst çizgi ve arka plan rengi
        bottomBar.setStyle("-fx-background-color: " + bg + "; -fx-border-color: #666; -fx-border-width: 1 0 0 0;");

        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color: " + btnBg + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        bottomBar.getChildren().add(btnClose);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 600, 700);
        scene.getStylesheets().add(getThemeCSS());
        dialog.setScene(scene);
        dialog.show();
    }

    // MODERN TEMA MOTORU (CSS)

    private String getThemeCSS() {
        String baseColor, accentColor, oddRowColor, textColor, headerColor, cardBg, cardBorder, buttonHover,
                separatorColor;

        if (isDarkMode) {
            // --- KOYU TEMA ---
            baseColor = "#1E1E1E";
            cardBg = "#252526";
            cardBorder = "#3E3E42";
            headerColor = "#2D2D30";
            oddRowColor = "#181818";
            accentColor = "#0E639C";
            textColor = "#E0E0E0";
            buttonHover = "#444444";
            separatorColor = "#555555";
        } else {
            // --- AÇIK TEMA ---
            baseColor = "#F3F3F3";
            cardBg = "#FFFFFF";
            cardBorder = "#DDDDDD";
            headerColor = "#E1E1E1";
            oddRowColor = "#F9F9F9";
            accentColor = "#0078D7";
            textColor = "#333333";
            buttonHover = "#D1D1D1";
            separatorColor = "#CCCCCC";
        }

        String css =
                // 1. GENEL
                ".root { -fx-base: " + baseColor + "; -fx-background: " + baseColor
                        + "; -fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif; }" +

                        // 2. KART TASARIMI
                        ".card-pane {" +
                        "    -fx-background-color: " + cardBg + ";" +
                        "    -fx-background-radius: 8;" +
                        "    -fx-border-radius: 8;" +
                        "    -fx-border-color: " + cardBorder + ";" +
                        "    -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);" +
                        "}" +
                        ".card-title { -fx-text-fill: " + accentColor + "; -fx-font-size: 14px; }" +

                        // 3. ÜST MENÜ KARTI
                        ".top-card {" +
                        "    -fx-background-color: " + cardBg + ";" +
                        "    -fx-background-radius: 8;" +
                        "    -fx-border-radius: 8;" +
                        "    -fx-border-color: " + cardBorder + ";" +
                        "    -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);" +
                        "    -fx-padding: 5 10 5 10;" +
                        "}" +

                        // 4. TABLO & HEADER
                        ".table-view { -fx-background-color: transparent; -fx-padding: 0; -fx-fixed-cell-size: 45px; }"
                        +
                        ".table-view .column-header, .table-view .column-header-background, .table-view .filler {" +
                        "    -fx-background-color: " + headerColor + ";" +
                        "    -fx-border-color: transparent " + separatorColor + " " + cardBorder + " transparent;" +
                        "    -fx-border-width: 0 1 1 0;" +
                        "    -fx-size: 50px;" +
                        "}" +
                        ".table-view .filler { -fx-border-width: 0 0 1 0; }" +
                        ".table-view .column-header .label { -fx-text-fill: " + textColor
                        + "; -fx-font-weight: bold; -fx-font-size: 14px; }" +
                        ".table-cell { -fx-font-size: 14px; -fx-alignment: CENTER-LEFT; -fx-padding: 0 0 0 10; -fx-text-fill: "
                        + textColor + "; }" +

                        // Zebra ve Seçim
                        ".table-row-cell:odd { -fx-background-color: " + oddRowColor + "; }" +
                        ".table-row-cell:even { -fx-background-color: " + cardBg + "; }" +
                        ".table-row-cell:filled:hover { -fx-background-color: " + (isDarkMode ? "#333333" : "#E8E8E8")
                        + "; }" +
                        ".table-row-cell:filled:selected { -fx-background-color: " + accentColor + "; }" +
                        ".table-row-cell:filled:selected .table-cell { -fx-text-fill: white; }" +

                        // 5. LIST VIEW
                        ".list-view { -fx-background-color: transparent; -fx-padding: 0; -fx-background-insets: 0; }" +
                        ".list-cell { -fx-background-color: transparent; -fx-text-fill: " + textColor
                        + "; -fx-padding: 5px; -fx-background-insets: 0; }" +
                        ".list-cell:filled:selected, .list-cell:filled:hover { -fx-background-color: "
                        + (isDarkMode ? "#333333" : "#E8E8E8") + "; }" +

                        // 6. UYARI PENCERELERİ (DIALOGS)
                        ".dialog-pane { -fx-background-color: " + cardBg + "; -fx-border-color: " + cardBorder
                        + "; -fx-border-width: 1; }" +
                        ".dialog-pane .header-panel { -fx-background-color: " + headerColor
                        + "; -fx-border-color: transparent transparent " + separatorColor
                        + " transparent; -fx-border-width: 0 0 1 0; }" +
                        ".dialog-pane .label, .dialog-pane .content { -fx-text-fill: " + textColor
                        + "; -fx-font-size: 14px; }" +
                        ".dialog-pane .button { -fx-background-color: " + accentColor
                        + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-font-size: 14px; }"
                        +
                        ".dialog-pane .button:hover { -fx-background-color: " + buttonHover + "; }" +

                        // 7. BUTONLAR VE INPUTLAR
                        ".label, .radio-button { -fx-text-fill: " + textColor + "; }" +
                        ".button, .toggle-button { -fx-cursor: hand; -fx-padding: 8px 15px; -fx-font-size: 13px; -fx-background-radius: 5px; }"
                        +
                        ".button:hover, .toggle-button:hover { -fx-background-color: " + buttonHover + "; }" +

                        ".text-field, .date-picker .text-field {" +
                        "    -fx-padding: 7px;" +
                        "    -fx-background-radius: 4px;" +
                        "    -fx-font-size: 13px;" +
                        "    -fx-border-color: transparent;" + // Normalde kenarlık yok
                        "}" +
                        ".text-field:focused {" +
                        "    -fx-border-color: " + accentColor + ";" + // Odaklanınca senin rengin (Mavi)
                        "    -fx-border-width: 2px;" +
                        "    -fx-border-radius: 4px;" +
                        "}" +

                        // 8. DATE PICKER IKON DUZELTMESI
                        ".date-picker .arrow-button { -fx-background-color: transparent; -fx-cursor: hand; }" +
                        ".date-picker .arrow-button .arrow { -fx-background-color: " + textColor + "; }" +

                        // 9. TOOLBAR SEPARATOR
                        ".separator:vertical .line { -fx-border-color: " + separatorColor
                        + "; -fx-border-width: 0 1 0 0; -fx-background-color: transparent; }" +

                        // Scrollbar
                        ".scroll-pane > .viewport { -fx-background-color: transparent; }" +
                        ".scroll-bar:horizontal { -fx-pref-height: 0; -fx-opacity: 0; }" +
                        ".scroll-bar:vertical { -fx-background-color: transparent; }" +
                        ".scroll-bar:vertical .track { -fx-background-color: transparent; }" +
                        ".scroll-bar:vertical .thumb { -fx-background-color: #666666; -fx-background-radius: 5em; }";

        String descriptionColor = isDarkMode ? "#AAAAAA" : "#666666";

        css += ".description-label { -fx-font-size: 12px; -fx-text-fill: " + descriptionColor
                + "; -fx-padding: 2 0 8 0; -fx-line-spacing: 2px; }";

        return "data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(css.getBytes());
    }

    // Yardım maddelerini oluşturan yardımcı metot
    private VBox createHelpSection(String title, String content, String titleColor, String contentColor) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(0, 0, 10, 0)); // Alt boşluk

        Label lblTitle = new Label(title);
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        lblTitle.setTextFill(Color.web(titleColor));

        Label lblContent = new Label(content);
        lblContent.setFont(Font.font("Arial", 13));
        lblContent.setTextFill(Color.web(contentColor));
        lblContent.setWrapText(true); // Uzun yazıları alt satıra geçir
        lblContent.setStyle("-fx-line-spacing: 4px;"); // Satır aralığını aç, okunabilir olsun

        section.getChildren().addAll(lblTitle, lblContent);
        return section;
    }

    private static class ScheduleRunResult {
        Map<String, List<StudentExam>> schedule;
        Map<String, String> reasons;

        ScheduleRunResult(Map<String, List<StudentExam>> s, Map<String, String> r) {
            this.schedule = s;
            this.reasons = r;
        }
    }

    private static class ScheduleScore {
        int unscheduledCount;
        int daysUsed;
        double studentLoadVariance;

        boolean betterThan(ScheduleScore other) {
            if (other == null)
                return true;
            if (this.unscheduledCount != other.unscheduledCount)
                return this.unscheduledCount < other.unscheduledCount;
            if (this.daysUsed != other.daysUsed)
                return this.daysUsed < other.daysUsed;
            return this.studentLoadVariance < other.studentLoadVariance;
        }
    }

    private ScheduleScore computeScore(
            Map<String, List<StudentExam>> schedule,
            Map<String, String> reasons) {

        ScheduleScore score = new ScheduleScore();

        // 1) Unscheduled course sayısı
        score.unscheduledCount = reasons.size();

        // 2) Kullanılan gün sayısı
        Set<LocalDate> days = new HashSet<>();
        for (List<StudentExam> exams : schedule.values()) {
            for (StudentExam se : exams) {
                if (se.getTimeslot() != null) {
                    days.add(se.getTimeslot().getDate());
                }
            }
        }
        score.daysUsed = days.size();

        // 3) Öğrenci sınav yükü varyansı
        List<Integer> loads = new ArrayList<>();
        for (List<StudentExam> exams : schedule.values()) {
            loads.add(exams.size());
        }

        double avg = loads.stream().mapToInt(i -> i).average().orElse(0);
        double variance = 0;
        for (int l : loads) {
            variance += Math.pow(l - avg, 2);
        }
        score.studentLoadVariance = loads.isEmpty() ? 0 : variance / loads.size();

        return score;
    }

    private ScheduleRunResult runSchedulerOnce(
            long seed,
            List<Student> students,
            List<Course> courses,
            List<Enrollment> enrollments,
            List<Classroom> classrooms,
            List<DayWindow> dayWindows) {

        Random rnd = new Random(seed);

        Collections.shuffle(students, rnd);
        Collections.shuffle(courses, rnd);
        Collections.shuffle(enrollments, rnd);
        Collections.shuffle(classrooms, rnd);
        Collections.shuffle(dayWindows, rnd);

        ExamScheduler scheduler = new ExamScheduler();
        Map<String, List<StudentExam>> result = scheduler.run(students, courses, enrollments, classrooms, dayWindows);

        return new ScheduleRunResult(result, scheduler.getUnscheduledReasons());
    }

    // Tabloyu alıp "Card" görünümlü bir VBox içine koyar
    private VBox wrapTableInCard(TableView<?> table) {
        VBox card = new VBox(table);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(0));

        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMaxHeight(Double.MAX_VALUE);

        BorderPane.setMargin(card, new Insets(10, 10, 10, 5));

        return card;
    }

    public static void main(String[] args) {
        launch(args);
    }
}