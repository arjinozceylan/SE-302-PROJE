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
    private ToggleButton tglStudents, tglExams, tglDays,tglClassrooms;
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

        tglStudents.setStyle("-fx-background-radius: 5 0 0 5; -fx-border-radius: 5 0 0 5; -fx-border-width: 1 0 1 1;");
        tglExams.setStyle("-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");
        tglDays.setStyle("-fx-background-radius: 0 5 5 0; -fx-border-radius: 0 5 5 0; -fx-border-width: 1 1 1 1;");

        ToggleGroup group = new ToggleGroup();
        tglClassrooms = createStyledToggleButton("Classrooms");
        tglClassrooms.setToggleGroup(group);
        tglClassrooms.setOnAction(e -> {
            if (tglClassrooms.isSelected()) performSearch(txtSearch.getText());
            updateToggleStyles();
        });
        tglStudents.setToggleGroup(group);
        tglExams.setToggleGroup(group);
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
                "Period Settings",
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
                "Constraints",
                "Set default duration and daily working hours.",
                "Default Duration: Used for courses that do NOT have a duration specified in the CSV file (e.g., 90 min).\nTime Range: The daily working hours (e.g., 09:00 - 17:00).",
                lblBlockTime, txtBlockTime, lblTime, timeInputs);

        // --- C) Customization Card ---
        btnCustomize = new Button("Advanced Rules \u2699");
        btnCustomize.setMaxWidth(Double.MAX_VALUE);
        btnCustomize.setOnAction(e -> showCustomizationDialog(primaryStage));

        VBox cardCustom = createCard(
                "Customization",
                "Define exceptions for capacity & duration.",
                "Click this button to manually override settings for specific courses. For example, you can force 'CS101' to have a duration of 120 mins or require a room with a minimum capacity of 50.",
                btnCustomize);

        // Kartları Ekle
        leftPane.getChildren().addAll(cardDate, cardConstraints, cardCustom);

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
        }
        else if (tglDays.isSelected()) {
            showDayList(q);
        }
    }

    // O anki aktif sekmeyi yeniden yükler
    private void refreshActiveView() {
        if (tglStudents.isSelected()) {
            showStudentList();
        } else if (tglExams.isSelected()) {
            showExamList();
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
                    String type = "Unknown";
                    String name = file.getName().toLowerCase();
                    if (name.contains("allstudents") || name.contains("std_id"))
                        type = "Students";
                    else if (name.contains("allcourses") || name.contains("courses"))
                        type = "Courses";
                    else if (name.contains("allclassrooms") || name.contains("capacities"))
                        type = "Rooms";
                    else if (name.contains("allattendancelists") || name.contains("attendance"))
                        type = "Links";

                    DBManager.saveUploadedFile(file.getAbsolutePath());

                    uploadedFilesData.add(new UploadedFileItem(file, file.getName() + "\n(" + type + ")"));
                    loadedFileCache.add(file);
                }
            }
            hideLoading();
            refreshActiveView();
        });

        task.setOnFailed(e -> hideLoading());
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
                    String name = file.getName().toLowerCase();
                    if (name.contains("allstudents") || name.contains("std_id")) {
                        allStudents.addAll(CsvDataLoader.loadStudents(file.toPath()));
                    } else if (name.contains("allcourses") || name.contains("courses")) {
                        allCourses.addAll(CsvDataLoader.loadCourses(file.toPath()));
                    } else if (name.contains("allclassrooms") || name.contains("capacities")) {
                        allClassrooms.addAll(CsvDataLoader.loadClassrooms(file.toPath()));
                    } else if (name.contains("allattendancelists") || name.contains("attendance")) {
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

        if (allStudents.isEmpty() || allCourses.isEmpty() || allClassrooms.isEmpty() || allEnrollments.isEmpty()) {
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

                    if (tglStudents.isSelected())
                        showStudentList();
                    else if (tglExams.isSelected())
                        showExamList();
                    else if (tglDays.isSelected())
                        showDayList();
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> hideLoading());
        task.setOnFailed(e -> {
            hideLoading();
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
                    .filter(s -> s.getId().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
            studentObservableList.setAll(filtered);
        }

        TableView<Student> table = new TableView<>();
        // Dinamik placeholder kullan
        table.setPlaceholder(getTablePlaceholder());
        styleTableView(table);

        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        TableColumn<Student, String> colExamCount = new TableColumn<>("Exams");
        colExamCount.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams = studentScheduleMap.getOrDefault(sid, Collections.emptyList());
            exams = filterExamsByCurrentFilters(exams);
            int count = exams.size();
            return new SimpleStringProperty(String.valueOf(count));
        });

        table.getColumns().addAll(colId, colExamCount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Tabloyu listeye bağla
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
                    setTooltip(null);
                    return;
                }
                Course course = getTableRow().getItem();
                String statusText = getCourseStatusText(course.getId());
                boolean isUnscheduled = statusText.contains("UNSCHEDULED");
                setText(item);
                String textColor = isUnscheduled ? (isDarkMode ? "#FF6B6B" : "#D32F2F")
                        : (isDarkMode ? "white" : "black");
                String fontWeight = isUnscheduled ? "bold" : "normal";
                if (getTableColumn().getText().equals("Status") && isUnscheduled) {
                    setTooltip(new Tooltip(statusText));
                } else
                    setTooltip(null);

                // Hizalama ayarı
                String alignment = "CENTER-LEFT";
                if (getTableColumn().getText().equals("Duration") || getTableColumn().getText().equals("Students")) {
                    alignment = "CENTER";
                }

                setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: " + fontWeight + "; -fx-alignment: "
                        + alignment + ";");
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
    // --- showExamList metodunun bittiği yer ---

    private void showClassroomList(String filterQuery) {
        currentDetailItem = null;
        TableView<Classroom> table = new TableView<>();
        table.setPlaceholder(new Label("No classroom data loaded."));
        styleTableView(table);

        TableColumn<Classroom, String> colId = new TableColumn<>("Classroom ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        TableColumn<Classroom, String> colCap = new TableColumn<>("Capacity");
        colCap.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getCapacity())));

        table.getColumns().addAll(colId, colCap);

        // Verileri filtrele ve yükle
        ObservableList<Classroom> classroomList = FXCollections.observableArrayList(allClassrooms);
        if (filterQuery != null && !filterQuery.isEmpty()) {
            classroomList = classroomList.filtered(c -> c.getId().toLowerCase().contains(filterQuery.toLowerCase()));
        }
        table.setItems(classroomList);

        // Bir sınıfa tıklandığında detayları göster
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) showClassroomScheduleDetail(newVal);
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
        btnBack.setOnAction(e -> showClassroomList("")); // Listeye geri dönmeyi sağlar

        Label lblTitle = new Label("Classroom Schedule: " + classroom.getId() + " (Cap: " + classroom.getCapacity() + ")");
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));
        header.getChildren().addAll(btnBack, lblTitle);

        // Tablo: Bu sınıftaki sınavlar
        TableView<DayRow> scheduleTable = new TableView<>();
        styleTableView(scheduleTable); // Ortak tablo stilini uygular

        TableColumn<DayRow, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(new PropertyValueFactory<>("courseId"));

        TableColumn<DayRow, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<DayRow, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));

        TableColumn<DayRow, String> colStudents = new TableColumn<>("# Students");
        colStudents.setCellValueFactory(new PropertyValueFactory<>("studentCount"));

        scheduleTable.getColumns().addAll(colCourse, colDate, colTime, colStudents);

        // Veriyi masterDayList içinden bu sınıfa ait olanları süzerek getir
        List<DayRow> classroomExams = new ArrayList<>();
        String targetId = classroom.getId().trim();
        // Filtresiz tarama yapıyoruz
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (se.getClassroomId() != null && se.getClassroomId().trim().equalsIgnoreCase(targetId)) {

                    // Bu sınavın bu sınıfta daha önce eklenip eklenmediğini kontrol et (Duplicate önleme)
                    boolean alreadyAdded = classroomExams.stream().anyMatch(r ->
                            r.getCourseId().equals(se.getCourseId()) &&
                                    r.getTime().contains(se.getTimeslot().getStart().toString()) &&
                                    r.getDate().equals(se.getTimeslot().getDate().toString())
                    );
                   

                    if (!alreadyAdded) {
                        classroomExams.add(new DayRow(
                                se.getTimeslot().getDate().toString(),
                                se.getTimeslot().getStart().toString() + " - " + se.getTimeslot().getEnd().toString(),
                                se.getClassroomId(),
                                se.getCourseId(),
                                getCourseStudentCount(se.getCourseId())
                        ));
                    }
                }
            }
        }

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

        VBox rootLayout = new VBox(15);
        rootLayout.setPadding(new Insets(20));
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        rootLayout.setStyle("-fx-background-color: " + bg + ";");

        // 1. DROP ZONE (Sürükle Bırak Alanı)
        VBox dropZone = new VBox(10);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(30));
        String panel = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String border = isDarkMode ? "#666" : "#CCC";

        dropZone.setStyle("-fx-border-color: " + border + "; -fx-border-style: dashed; -fx-border-width: 2; " +
                "-fx-background-color: " + panel + "; -fx-background-radius: 5; -fx-border-radius: 5;");

        Label lblInstruction = new Label("Drag and drop CSV files here");
        lblInstruction.setTextFill(Color.web(text));
        lblInstruction.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Label lblSub = new Label("Supported: Students, Courses, Classrooms, Attendance");
        lblSub.setTextFill(Color.web(isDarkMode ? "#AAAAAA" : "#666666"));
        lblSub.setFont(Font.font("Arial", 11));

        Button btnBrowse = new Button("Browse Files");
        btnBrowse.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-cursor: hand;");

        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null)
                processAndLoadFiles(files);
        });

        dropZone.getChildren().addAll(lblInstruction, lblSub, new Label("- or -"), btnBrowse);

        // Sürükle Bırak Olayları
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

        // 2. YÜKLÜ DOSYALAR LİSTESİ (Data Files)
        Label lblListHeader = new Label("Loaded Files (Select to Include):");
        lblListHeader.setTextFill(Color.web(text));
        lblListHeader.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        // ListView ayarları
        if (uploadedFilesList == null)
            uploadedFilesList = new ListView<>(uploadedFilesData);
        uploadedFilesList.setPrefHeight(200);
        uploadedFilesList.setStyle("-fx-background-color: " + panel + "; -fx-control-inner-background: " + panel + ";");

        // List Cell Factory
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
                    Label nameLabel = new Label(item.displayText.split("\n")[0]); // Sadece dosya adı
                    nameLabel.setTextFill(Color.web(text));
                    Label typeLabel = new Label(item.displayText.contains("(") ? item.displayText.split("\n")[1] : "");
                    typeLabel.setFont(Font.font("Arial", 10));
                    typeLabel.setTextFill(Color.gray(0.6));
                    infoBox.getChildren().addAll(nameLabel, typeLabel);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button btnRemove = new Button("✕"); // Çarpı ikonu
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-background-color: transparent; -fx-font-weight: bold; -fx-cursor: hand;");
                    btnRemove.setOnAction(event -> {
                        if (showConfirmDialog("Remove File?", "Remove " + item.file.getName() + "?")) {
                            uploadedFilesData.remove(item);
                            loadedFileCache.remove(item.file);
                            DBManager.removeUploadedFile(item.file.getAbsolutePath());
                            // Verileri temizle
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
                    setStyle("-fx-background-color: transparent; -fx-border-color: " + border
                            + "; -fx-border-width: 0 0 1 0;");
                }
            }
        });

        Button btnClose = new Button("Close");
        btnClose.setMaxWidth(Double.MAX_VALUE);
        btnClose.setStyle(
                "-fx-background-color: " + (isDarkMode ? DARK_BTN : LIGHT_BTN) + "; -fx-text-fill: " + text + ";");
        btnClose.setOnAction(e -> dialog.close());

        rootLayout.getChildren().addAll(dropZone, new Separator(), lblListHeader, uploadedFilesList, btnClose);

        Scene dialogScene = new Scene(rootLayout, 500, 600);
        dialogScene.getStylesheets().add(getThemeCSS());

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
        ComboBox<String> cmbType = new ComboBox<>(FXCollections.observableArrayList(
                "Student List",
                "Exam Schedule (Detailed per Student)",
                "Course Schedule (Exams Tab)",
                "Day Schedule"));
        cmbType.getSelectionModel().selectFirst();

        Label lblName = new Label("File Name (without extension)");
        lblName.setTextFill(Color.web(text));
        TextField txtName = new TextField("export_data");

        Button btnDoExport = new Button("Choose Location & Export");
        btnDoExport.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");

        btnDoExport.setOnAction(e -> {
            String type = cmbType.getValue();
            String defaultName = txtName.getText().trim();
            if (defaultName.isEmpty())
                defaultName = "export_data";

            // Dosya uzantısı kontrolü
            if (!defaultName.toLowerCase().endsWith(".csv")) {
                defaultName += ".csv";
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Export File");
            fileChooser.setInitialFileName(defaultName);
            // Sadece CSV filtresi
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

            File selectedFile = fileChooser.showSaveDialog(dialog);

            if (selectedFile != null) {
                // Eğer kullanıcı elle uzantı yazmadıysa biz ekleyelim
                if (!selectedFile.getName().toLowerCase().endsWith(".csv")) {
                    selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".csv");
                }

                // 3. parametre 'true' -> Her zaman Excel Uyumlu (Noktalı Virgül ve BOM)
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
                dialog.close(); // Dialogu kapat
            }
        });

        Label descType = createDescriptionLabel(
                "Choose 'Student List' for counts or 'Exam Schedule' for detailed dates.");
        layout.getChildren().addAll(lblType, descType, cmbType, lblName, txtName, btnDoExport);

        Scene s = new Scene(layout, 300, 250);
        dialog.setScene(s);
        dialog.show();
    }

    private void applyTheme() {
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;

        // 1. Ana Arka Plan
        root.setStyle("-fx-background-color: " + bg + ";");

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

        // Students (Sol)
        tglStudents.setStyle(common + (tglStudents.isSelected() ? selectedStyle : normalStyle) +
                "-fx-background-radius: 5 0 0 5; -fx-border-radius: 5 0 0 5; -fx-border-width: 1 0 1 1;");
        // Classroom butonu ortada olduğu için köşeleri düz tutulur
        if (tglClassrooms != null) {
            tglClassrooms.getStyleClass().removeAll("toggle-left", "toggle-center", "toggle-right");
            tglClassrooms.getStyleClass().add("toggle-center");
        }

        // tglDays en sağda olduğu için "toggle-right" onda kalmalı
        if (tglDays != null) {
            tglDays.getStyleClass().removeAll("toggle-left", "toggle-center", "toggle-right");
            tglDays.getStyleClass().add("toggle-right");
        }
        // Exams (Orta)
        tglExams.setStyle(common + (tglExams.isSelected() ? selectedStyle : normalStyle) +
                "-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");
        if (tglClassrooms != null) {
            tglClassrooms.setStyle(common + (tglClassrooms.isSelected() ? selectedStyle : normalStyle) +
                    "-fx-background-radius: 0; -fx-border-radius: 0; -fx-border-width: 1 0 1 1;");
        }
        // Days (Sağ)
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

        // 1. BAŞLIK VE YARDIM BUTONU (Hizalama eklendi)
        if (title != null) {
            HBox titleRow = new HBox(5);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Label lblTitle = new Label(title);
            lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 15));

            // Küçük Mavi Soru İşareti Butonu
            Button btnInfo = new Button("?");
            btnInfo.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; " +
                    "-fx-background-radius: 12; -fx-font-size: 10px; -fx-font-weight: bold; " +
                    "-fx-padding: 2 7 2 7; -fx-cursor: hand;");

            // Tıklayınca açılacak pencere
            btnInfo.setOnAction(e -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(title + " Help");
                alert.setHeaderText(title + " Guide");
                alert.setContentText(helpContent);
                styleDialog(alert); // Mevcut diyalog stilini uygula
                alert.showAndWait();
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS); // Butonu en sağa iter

            titleRow.getChildren().addAll(lblTitle, spacer, btnInfo);
            card.getChildren().add(titleRow);
        }

        // 2. AÇIKLAMA (Alt başlık)
        if (description != null && !description.isEmpty()) {
            Label descLbl = createDescriptionLabel(description);
            descLbl.setStyle("-fx-padding: 2 0 5 0; -fx-font-size: 12px; -fx-text-fill: "
                    + (isDarkMode ? "#AAAAAA" : "#666666") + ";");
            card.getChildren().add(descLbl);
        }

        card.getChildren().add(new Separator());

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
            Label iconLabel = new Label(initialValue ? "🌙" : "☀️");
            // Yazı boyutunu 14px yaparak büyüttük ve kalınlaştırdık
            iconLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            iconLabel.mouseTransparentProperty().set(true);

            // İkonu tam merkezlemek için StackPane içinde hizalayalım
            StackPane.setAlignment(iconLabel, Pos.CENTER);
            trigger.setFill(Color.WHITE);
            trigger.setEffect(new DropShadow(2, Color.gray(0.2)));

            // Arka plan, daire ve ikonu üst üste ekliyoruz
            getChildren().addAll(background, trigger, iconLabel);

            // Başlangıç Durumu Rengi
            if (initialValue) {
                trigger.setTranslateX(width / 2 - radius - 2);
                iconLabel.setTranslateX(11);
                background.setFill(Color.web("#0E639C"));
                background.setStroke(Color.web("#0E639C"));
            } else {
                trigger.setTranslateX(-(width / 2 - radius - 2));
                iconLabel.setTranslateX(-11);
                background.setFill(Color.web("#E9E9EA"));
                background.setStroke(Color.web("#E9E9EA"));
            }

            setOnMouseClicked(event -> switchedOn.set(!switchedOn.get()));

            switchedOn.addListener((obs, oldState, newState) -> {
                boolean isOn = newState;
                // switchedOn.addListener içinde:
                double targetX = isOn ? 11 : -11; // 12 yerine 11 veya 10 deneyerek tam merkezi bulabilirsin

                iconLabel.setText(isOn ? "🌙" : "☀️");
                TranslateTransition iconTransit = new TranslateTransition(Duration.seconds(0.25), iconLabel);
                iconTransit.setToX(targetX);
                iconTransit.play();
                translateAnimation.setNode(trigger);
                translateAnimation.setToX(isOn ? width / 2 - radius - 2 : -(width / 2 - radius - 2));
                translateAnimation.setNode(trigger);
                fillAnimation.setShape(background);

                fillAnimation.setToValue(isOn ? Color.web("#0E639C") : Color.web("#E9E9EA"));

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
    private static class DayRow {
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
        dialog.setTitle("Customize Exam Rules (Layered Rules)");

        // Ana Layout
        BorderPane mainLayout = new BorderPane();
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        mainLayout.setStyle("-fx-background-color: " + bg + ";");

        Label headerDesc = new Label(
                "Add custom constraints for specific courses (e.g., Duration, Capacity). Use 'Exclude' to skip a course.");
        headerDesc.setWrapText(true);
        headerDesc.setTextFill(Color.web(isDarkMode ? "#AAAAAA" : "#666666"));
        headerDesc.setPadding(new Insets(10, 15, 0, 15));
        mainLayout.setTop(headerDesc); // BorderPane'in üst kısmına koyuyoruz

        // Kural Gruplarının Listesi
        VBox groupsContainer = new VBox(10);
        groupsContainer.setPadding(new Insets(10));
        groupsContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(groupsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + bg + ";");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Butonlar
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setStyle("-fx-background-color: " + (isDarkMode ? DARK_PANEL : LIGHT_PANEL)
                + "; -fx-border-color: #666; -fx-border-width: 1 0 0 0;");

        Button btnAddGroup = createStyledButton("+ Add Rule Group");
        btnAddGroup.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;");

        Button btnApplyAll = createStyledButton("Save & Regenerate Schedule");
        btnApplyAll
                .setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white; -fx-font-weight: bold;");

        // "Add Group" Aksiyonu
        btnAddGroup.setOnAction(e -> {
            RuleGroupPane groupPane = new RuleGroupPane(groupsContainer);
            groupsContainer.getChildren().add(groupPane);
            ruleGroups.add(groupPane);
        });

        // "Save & Apply" Aksiyonu
        btnApplyAll.setOnAction(e -> {

            for (RuleGroupPane pane : ruleGroups) {
                pane.saveToDB(0);
            }
            dialog.close();
            runSchedulerLogic(true);
        });

        // --- EKRANI DOLDURMA MANTIĞI ---
        groupsContainer.getChildren().clear(); // Temizle

        if (ruleGroups.isEmpty()) {
            // Hiç kural yoksa bir tane boş aç
            RuleGroupPane initialPane = new RuleGroupPane(groupsContainer);
            groupsContainer.getChildren().add(initialPane);
            ruleGroups.add(initialPane);
        } else {
            // Varsa olan kural gruplarını ekle
            for (RuleGroupPane pane : ruleGroups) {
                // Eğer pane başka bir pencereye bağlıysa oradan sök
                if (pane.getParent() != null) {
                    ((Pane) pane.getParent()).getChildren().remove(pane);
                }

                // Yeni container'a ekle
                groupsContainer.getChildren().add(pane);

                // Pane'in içindeki "Remove" butonu çalışsın diye yeni ebeveyni tanıt
                pane.setParentContainer(groupsContainer);
            }
        }

        bottomBar.getChildren().addAll(btnAddGroup, btnApplyAll);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(bottomBar);

        Scene scene = new Scene(mainLayout, 600, 600);
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

        // Ana Konteyner
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // Tema Renkleri
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        String text = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String headerColor = ACCENT_COLOR;

        root.setStyle("-fx-background-color: " + bg + ";");

        Label mainHeader = new Label("Exam Management System Guide");
        mainHeader.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        mainHeader.setTextFill(Color.web(text));
        root.getChildren().add(mainHeader);

        // --- İÇERİK KISIMLARI ---
        root.getChildren().add(createHelpSection("1. Toolbar (Top Menu)",
                "• Errors (Red Box): Shows the number of errors occurred. Click it to see the detailed error log.\n" +
                        "• Import: Load CSV files (Students, Courses, Rooms, Enrollment) into the system.\n" +
                        "• Export: Save the current schedule or lists as a CSV file (Excel compatible).\n" +
                        "• Apply: The most important button! It saves settings, runs the scheduling algorithm, and saves results to the database.\n"
                        +
                        "• Search: Filter the visible table rows by Student ID or Course Code.\n" +
                        "• View Tabs (Students/Exams/Days): Switch between different views of the schedule.",
                headerColor, text));

        root.getChildren().add(createHelpSection("2. Filter Options (Left Panel)",
                "• Duration (Days): Sets the total length of the exam period.\n" +
                        "• Date Range: Automatically updates based on Duration. Defines the start and end dates.\n" +
                        "• Default Duration: Used for courses that do NOT have a duration specified in the CSV file (e.g., 90 min).\n"
                        +
                        "• Time Range: The daily working hours (e.g., 09:00 - 17:00). Exams will not be placed outside these hours.",
                headerColor, text));

        root.getChildren().add(createHelpSection("3. Customize Exam Rules",
                "Click this button to manually override settings for specific courses.\n" +
                        "For example, you can force 'CS101' to have a duration of 120 mins or require a room with a minimum capacity of 50.",
                headerColor, text));

        root.getChildren().add(createHelpSection("4. Uploaded Files List",
                "• Checkboxes: Only checked files are included in the scheduling process.\n" +
                        "• 'X' Button: Permanently removes the file from the database and memory.\n" +
                        "• File Types: The system automatically detects file types (Students, Courses, etc.) based on filenames.",
                headerColor, text));

        root.getChildren().add(createHelpSection("5. Understanding the Views",
                "• Students Tab: Shows how many exams each student has. Click a row to see that student's personal schedule.\n"
                        +
                        "• Exams Tab: Lists all courses. Red text (UNSCHEDULED) means the algorithm failed to find a slot for that course.\n"
                        +
                        "• Days Tab: A timeline view showing exams grouped by Date and Time.",
                headerColor, text));

        root.getChildren().add(createHelpSection("⚠️ Troubleshooting & Unscheduled Exams",
                "If a course appears in RED:\n" +
                        "1. Check 'Errors' log for constraints (e.g., 'Room capacity exceeded').\n" +
                        "2. Ensure the Date/Time range is wide enough.\n" +
                        "3. Check if 'Default Duration' is set correctly.\n" +
                        "4. Verify that Room Capacities in CSV are sufficient.",
                isDarkMode ? "#FF6B6B" : "#D32F2F", text));

        // ScrollPane Ayarları
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Temel stil (Beyaz çerçeveyi kaldırmak için)
        scrollPane.setStyle(
                "-fx-background: " + bg + "; -fx-background-color: transparent; -fx-control-inner-background: " + bg
                        + "; -fx-background-insets: 0;");

        Scene scene = new Scene(scrollPane, 600, 700);

        // Hem açık hem koyu modda CSS'i yüklüyoruz çünkü buton/header stilleri orada.
        scene.getStylesheets().add(getThemeCSS());

        dialog.setScene(scene);
        dialog.show();
    }

    // MODERN TEMA MOTORU (CSS)

    // MODERN TEMA MOTORU (CSS) - (DÜZELTİLMİŞ)
    private String getThemeCSS() {
        // 'inputBg' değişkeni buradan kaldırıldı çünkü kullanılmıyordu
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
                        ".text-field, .date-picker .text-field { -fx-padding: 7px; -fx-background-radius: 4px; -fx-font-size: 13px; }"
                        +

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