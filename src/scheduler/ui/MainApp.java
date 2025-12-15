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

    private final List<File> loadedFileCache = new ArrayList<>();

    // --- Aktif Detay Ekranını Hatırlamak İçin ---
    private Object currentDetailItem = null;

    // --- ERROR LOGGING ---
    private final List<String> errorLog = new ArrayList<>();

    // Map: StudentID -> List of Scheduled Exams
    private Map<String, List<StudentExam>> studentScheduleMap = new HashMap<>();
    private Map<String, String> lastUnscheduledReasons = new HashMap<>();

    // UI Table Data Sources
    private ObservableList<Student> studentObservableList = FXCollections.observableArrayList();
    private ObservableList<Course> examObservableList = FXCollections.observableArrayList();

    // UI Components

    private BorderPane root;
    private HBox topMenu, bottomBar;
    private VBox leftPane;
    private Label lblErrorCount, lblSectionTitle, lblDate, lblBlock, lblTime, lblUploaded, lblStats, lblDays,
            lblBlockTime;
    private StackPane mainStack; // Ana kapsayıcı (En dış katman)
    private VBox loadingOverlay; // Yükleniyor katmanı

    private ObservableList<UploadedFileItem> uploadedFilesData = FXCollections.observableArrayList();
    private ListView<UploadedFileItem> uploadedFilesList;

    // Gün Sayısı ve Sabit Süre
    private TextField txtDays, txtBlockTime;

    private Button btnHelp, btnImport, btnExport, btnApply, btnCustomize;
    private TextField txtSearch, txtBlockStart, txtBlockEnd, txtTimeStart, txtTimeEnd;
    private DatePicker startDate, endDate;
    private ToggleButton tglStudents, tglExams, tglDays;
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
        applyTheme();
        showStudentList(); // Tabloyu ilk başta boş göster

        Scene scene = new Scene(mainStack, 1100, 775);
        primaryStage.setTitle("MainApp - Exam Management System");
        primaryStage.setScene(scene);
        primaryStage.show();

        // ============================================================
        // 4. VERİLERİ GERİ YÜKLEME (PERSISTENCE)
        // ============================================================

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
                    studentObservableList.setAll(allStudents);
                }

                // Ders Listesini DB'den doldur
                List<Course> dbCourses = DBManager.loadCoursesFromDB();
                if (!dbCourses.isEmpty()) {
                    allCourses.clear();
                    allCourses.addAll(dbCourses);
                    examObservableList.setAll(allCourses);
                }

                // Sınıfları doldur
                List<Classroom> dbRooms = DBManager.loadClassroomsFromDB();
                if (!dbRooms.isEmpty()) {
                    allClassrooms.clear();
                    allClassrooms.addAll(dbRooms);
                }

                updateStats();
                System.out.println("Loaded previous state from Database.");
            }
        } catch (Exception e) {
            logError("Failed to load data from DB: " + e.getMessage());
        }
    }

    // UI Bileşenlerini oluşturan yardımcı metot
    private void setupUI() {
        // --- 1. HEADER / TOOLBAR ---
        topMenu = new HBox(15);
        topMenu.setPadding(new Insets(10));
        topMenu.setAlignment(Pos.CENTER_LEFT);

        btnHelp = createStyledButton("?");
        lblErrorCount = new Label("Errors: 0");
        lblErrorCount.setTextFill(Color.WHITE);
        lblErrorCount.setStyle(
                "-fx-background-color: #D11212; -fx-padding: 3 8 3 8; -fx-background-radius: 10; -fx-font-weight: bold;");
        lblErrorCount.setOnMouseClicked(e -> showErrorLogDialog());

        btnImport = createStyledButton("Import \u2193");
        btnImport.setOnAction(e -> showImportDialog(primaryStage));

        btnExport = createStyledButton("Export \u2191");
        btnExport.setOnAction(e -> showExportDialog(primaryStage));

        btnApply = createStyledButton("Apply");
        btnApply.setOnAction(e -> runSchedulerLogic());

        txtSearch = createStyledTextField("Search...");
        txtSearch.setPrefWidth(200);
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> filterStudentList(newVal));

        // Filters
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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        themeSwitch = new ToggleSwitch(true);
        themeSwitch.switchOnProperty().addListener((obs, oldVal, newVal) -> {
            isDarkMode = newVal;
            applyTheme();
        });

        topMenu.getChildren().addAll(btnHelp, lblErrorCount, btnImport, btnExport, btnApply, txtSearch, filters, spacer,
                themeSwitch);

        // --- 2. LEFT SIDEBAR ---
        leftPane = new VBox(15);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(260);

        lblSectionTitle = new Label("Filter Options");
        lblSectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // Date Section
        VBox dateBox = new VBox(5);
        lblDays = new Label("Duration (Days):");
        txtDays = createStyledTextField("9");
        txtDays.setText("9");
        lblDate = new Label("Date Range:");
        startDate = new DatePicker(LocalDate.now());
        endDate = new DatePicker(LocalDate.now().plusDays(9));
        startDate.setPromptText("Start Date");
        startDate.setMaxWidth(Double.MAX_VALUE);
        endDate.setPromptText("End Date");
        endDate.setMaxWidth(Double.MAX_VALUE);

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
            if (newVal != null && startDate.getValue() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(startDate.getValue(), newVal);
                if (!txtDays.isFocused())
                    txtDays.setText(String.valueOf(Math.max(0, days)));
            }
        });
        dateBox.getChildren().addAll(lblDays, txtDays, lblDate, startDate, endDate);

        // Block Section
        VBox blockBox = new VBox(5);
        lblBlockTime = new Label("Block Time (min):");
        txtBlockTime = createStyledTextField("90");
        txtBlockTime.setText("90");
        lblBlock = new Label("Block Range (Min - Max):");
        HBox blockInputs = new HBox(5);
        txtBlockStart = createStyledTextField("Min");
        txtBlockStart.setText("90");
        txtBlockEnd = createStyledTextField("Max");
        txtBlockEnd.setText("90");
        blockInputs.getChildren().addAll(txtBlockStart, txtBlockEnd);

        txtBlockTime.textProperty().addListener((obs, oldVal, newVal) -> {
            if (txtBlockTime.isFocused()) {
                txtBlockStart.setText(newVal);
                txtBlockEnd.setText(newVal);
            }
        });
        javafx.beans.value.ChangeListener<String> rangeListener = (obs, oldVal, newVal) -> {
            if (txtBlockStart.isFocused() || txtBlockEnd.isFocused()) {
                if (!txtBlockStart.getText().equals(txtBlockEnd.getText())) {
                    txtBlockTime.clear();
                    txtBlockTime.setPromptText("-");
                } else {
                    txtBlockTime.setText(txtBlockStart.getText());
                }
            }
        };
        txtBlockStart.textProperty().addListener(rangeListener);
        txtBlockEnd.textProperty().addListener(rangeListener);
        blockBox.getChildren().addAll(lblBlockTime, txtBlockTime, lblBlock, blockInputs);

        // Time Range
        VBox timeBox = new VBox(5);
        lblTime = new Label("Time Range:");
        HBox timeInputs = new HBox(5);
        txtTimeStart = createStyledTextField("09:00");
        txtTimeEnd = createStyledTextField("17:00");
        timeInputs.getChildren().addAll(txtTimeStart, txtTimeEnd);
        timeBox.getChildren().addAll(lblTime, timeInputs);

        // Customize Button
        btnCustomize = new Button("Customize Exam Rules \u2699");
        btnCustomize.setMaxWidth(Double.MAX_VALUE);
        btnCustomize.setOnAction(e -> showCustomizationDialog(primaryStage));

        // Uploaded Files
        Separator sepFiles = new Separator();
        lblUploaded = new Label("Uploaded Files:");
        lblUploaded.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        uploadedFilesList = new ListView<>(uploadedFilesData);
        uploadedFilesList.setPrefHeight(200);
        VBox.setVgrow(uploadedFilesList, Priority.ALWAYS);
        uploadedFilesList.setPlaceholder(new Label("No files loaded"));

        // Cell Factory
        uploadedFilesList.setCellFactory(param -> new ListCell<UploadedFileItem>() {
            @Override
            protected void updateItem(UploadedFileItem item, boolean empty) {
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
                    CheckBox cbSelect = new CheckBox();
                    cbSelect.selectedProperty().bindBidirectional(item.isSelected);
                    Label label = new Label(item.displayText);
                    label.setTextFill(Color.web(textColor));
                    label.setWrapText(true);
                    label.setMaxWidth(140);
                    HBox.setHgrow(label, Priority.ALWAYS);

                    Button btnRemove = new Button("X");
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-font-weight: bold; -fx-background-color: transparent;");

                    btnRemove.setOnAction(event -> {
                        boolean confirmed = showConfirmDialog("Remove File?",
                                "Are you sure you want to remove this file?\n\n" + item.displayText
                                        + "\n\nThis will clear current loaded data.");
                        if (confirmed) {
                            uploadedFilesData.remove(item);
                            loadedFileCache.remove(item.file);

                            DBManager.removeUploadedFile(item.file.getAbsolutePath());

                            // Temizlik
                            allStudents.clear();
                            allCourses.clear();
                            allClassrooms.clear();
                            allEnrollments.clear();
                            studentScheduleMap.clear();
                            lastUnscheduledReasons.clear();
                            studentObservableList.clear();
                            examObservableList.clear();

                            updateStats();
                        }
                    });
                    box.getChildren().addAll(cbSelect, label, btnRemove);
                    setGraphic(box);
                }
            }
        });

        leftPane.getChildren().addAll(lblSectionTitle, new Separator(), dateBox, new Separator(), blockBox,
                new Separator(), timeBox, new Separator(), btnCustomize, sepFiles, lblUploaded, uploadedFilesList);

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

    private void loadSettingsFromDB() {
        String sDays = DBManager.loadSetting("days");
        if (sDays != null && !sDays.isEmpty())
            txtDays.setText(sDays);

        String sBlock = DBManager.loadSetting("blockTime");
        if (sBlock != null && !sBlock.isEmpty())
            txtBlockTime.setText(sBlock);

        String sBlockMin = DBManager.loadSetting("blockMin");
        if (sBlockMin != null && !sBlockMin.isEmpty())
            txtBlockStart.setText(sBlockMin);

        String sBlockMax = DBManager.loadSetting("blockMax");
        if (sBlockMax != null && !sBlockMax.isEmpty())
            txtBlockEnd.setText(sBlockMax);

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

    // =============================================================
    // ERROR HANDLING SYSTEM
    // =============================================================

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

    // =============================================================
    // FILE PROCESSING
    // =============================================================

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

    // =============================================================
    // SCHEDULER LOGIC (Integration Point)
    // =============================================================

    private void runSchedulerLogic() {

        saveCurrentState();

        System.out.println("UI: Reloading data from CHECKED files...");

        // 2. Temizlik
        allStudents.clear();
        allCourses.clear();
        allClassrooms.clear();
        allEnrollments.clear();
        studentScheduleMap.clear();
        lastUnscheduledReasons.clear();

        // 3. Dosyaları Oku
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

        // 4. Kuralları Yükle ve Uygula
        // Eğer hafızada hiç kural yoksa (Program yeni açıldıysa), DB'den geri yükle
        if (ruleGroups.isEmpty()) {
            restoreRulesFromDB();
        }

        // Kuralları Taze Verilere (allCourses) Uygula
        if (!ruleGroups.isEmpty()) {
            System.out.println("Applying " + ruleGroups.size() + " rule groups...");
            for (RuleGroupPane pane : ruleGroups) {
                pane.applyRulesToSelectedCourses();
            }
        }

        // 5. UI Güncelle
        studentObservableList.setAll(allStudents);
        examObservableList.setAll(allCourses);
        updateStats();

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

        // 6. Arka Plan Görevi (Scheduler)
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // Veritabanı tablolarını temizle (Çift kaydı önler)
                DBManager.clearScheduleTable();
                DBManager.clearConflictLog();

                ExamScheduler scheduler = new ExamScheduler();
                Map<String, List<StudentExam>> scheduleResult = scheduler.run(
                        allStudents, allCourses, allEnrollments, allClassrooms, dayWindows);

                // Sonuçları DB'ye yaz
                for (List<StudentExam> list : scheduleResult.values()) {
                    for (StudentExam se : list) {
                        DBManager.insertSchedule(se);
                    }
                }

                Map<String, String> reasons = scheduler.getUnscheduledReasons();

                Platform.runLater(() -> {
                    studentScheduleMap = scheduleResult;
                    lastUnscheduledReasons = reasons;

                    if (!reasons.isEmpty()) {
                        for (Map.Entry<String, String> entry : reasons.entrySet()) {
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

    // =============================================================
    // DATE / TIME FILTER HELPERS (LEFT SIDEBAR)
    // =============================================================

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

    private int findCourseDuration(String courseId) {
        for (Course c : allCourses) {
            if (c.getId().equals(courseId)) {
                return c.getDurationMinutes();
            }
        }
        return 0;
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

    // =============================================================
    // COURSE DETAIL VIEW (Students in a specific Exam)
    // =============================================================

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private void showStudentList() {
        currentDetailItem = null;
        TableView<Student> table = new TableView<>();
        table.setPlaceholder(new Label("No students data loaded."));
        styleTableView(table);

        // Student ID kolonu
        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        // Öğrencinin kaç sınavı var? (Exams)
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
        table.setItems(studentObservableList);

        // Satıra tıklayınca o öğrencinin programını aç
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showStudentScheduleDetail(newVal);
            }
        });

        root.setCenter(table);
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

        Button btnBack = new Button("\u2190 Back List");
        btnBack.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + text
                + "; -fx-background-radius: 4; -fx-border-color: #666; -fx-border-radius: 4;");
        btnBack.setOnAction(e -> showStudentList());

        Label lblTitle = new Label("Exam Schedule: " + student.getId());
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.web(text));

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

    // =============================================================
    // SHOW EXAM LIST (Sınavlar Sekmesi)
    // =============================================================

    @SuppressWarnings("unchecked")
    private void showExamList() {
        TableView<Course> table = new TableView<>();
        table.setPlaceholder(new Label("No courses loaded or no schedule generated."));
        styleTableView(table);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // --- ÖZELLEŞTİRİLMİŞ HÜCRE FABRİKASI ---
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

                String header = getTableColumn().getText();

                // --- METİN İÇERİĞİ AYARLAMA ---
                String textToShow = item;

                if (isUnscheduled) {
                    // Unscheduled ise bazı kolonlara tire koy
                    if (header.equals("Date") || header.equals("Time") || header.equals("Rooms")
                            || header.equals("Students")) {
                        textToShow = "-";
                    } else if (header.equals("Status")) {
                        textToShow = "UNSCHEDULED";
                    }
                }

                setText(textToShow);

                // --- STİL VE RENK AYARLAMA ---
                String textColor;
                String fontWeight = "normal";

                if (isUnscheduled) {
                    textColor = isDarkMode ? "#FF6B6B" : "#D32F2F"; // Kırmızı tonları
                    fontWeight = "bold";

                    // Status kolonu için detay sebebini Tooltip olarak ekle
                    if (header.equals("Status")) {
                        Tooltip tip = new Tooltip(statusText);
                        tip.setMaxWidth(400);
                        tip.setWrapText(true);
                        setTooltip(tip);
                    } else {
                        setTooltip(null);
                    }
                } else {
                    textColor = isDarkMode ? "white" : "black";
                    setTooltip(null);
                }

                // Hizalama
                String alignment = "CENTER-LEFT";
                if (header.equals("Duration (min)") || header.equals("Students")) {
                    alignment = "CENTER";
                }

                setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: " + fontWeight + "; -fx-alignment: "
                        + alignment + ";");
            }
        };

        // 1) Course Code
        TableColumn<Course, String> colCode = new TableColumn<>("Course Code");
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colCode.setCellFactory(customCellFactory);

        // 2) Duration
        TableColumn<Course, String> colDur = new TableColumn<>("Duration (min)");
        colDur.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));
        colDur.setCellFactory(customCellFactory);
        colDur.setMaxWidth(1000);

        // 3) Date
        TableColumn<Course, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(getCourseDate(cell.getValue().getId())));
        colDate.setCellFactory(customCellFactory);

        // 4) Time
        TableColumn<Course, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(getCourseTimeRange(cell.getValue().getId())));
        colTime.setCellFactory(customCellFactory);

        // 5) Rooms
        TableColumn<Course, String> colRooms = new TableColumn<>("Rooms");
        colRooms.setCellValueFactory(cell -> new SimpleStringProperty(getCourseRooms(cell.getValue().getId())));
        colRooms.setCellFactory(customCellFactory);

        // 6) Students
        TableColumn<Course, String> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(getCourseStudentCount(cell.getValue().getId()))));
        colCount.setCellFactory(customCellFactory);
        colCount.setMaxWidth(1000);

        // 7) Status
        TableColumn<Course, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(getCourseStatusText(cell.getValue().getId())));
        colStatus.setCellFactory(customCellFactory);

        // Tabloya Ekle
        table.getColumns().setAll(colCode, colDur, colDate, colTime, colRooms, colCount, colStatus);

        // Veriyi Sırala
        javafx.collections.transformation.SortedList<Course> sortedExams = new javafx.collections.transformation.SortedList<>(
                examObservableList);
        sortedExams.setComparator((c1, c2) -> naturalCompare(c1.getId(), c2.getId()));
        table.setItems(sortedExams);

        // Tıklama Olayı (Detayları Gör)
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showCourseStudentList(newVal);
            }
        });

        root.setCenter(table);
    }

    @SuppressWarnings("unchecked")
    private void showDayList() {
        currentDetailItem = null;
        TableView<DayRow> table = new TableView<>();
        table.setPlaceholder(new Label("No schedule generated yet."));
        styleTableView(table);

        // 1) studentScheduleMap'ten gün-saat-sınıf bazlı özet çıkar
        Map<String, DayRow> map = new LinkedHashMap<>();

        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                Timeslot ts = se.getTimeslot();
                if (ts == null)
                    continue;
                // Tarih / saat filtrelerini uygula
                if (!timeslotMatchesFilters(ts))
                    continue;

                String dateStr = ts.getDate().toString();
                String timeStr = ts.getStart().toString() + " - " + ts.getEnd().toString();
                String room = se.getClassroomId();
                String courseId = se.getCourseId();

                String key = dateStr + "|" + timeStr + "|" + room + "|" + courseId;

                DayRow row = map.get(key);
                if (row == null) {
                    row = new DayRow(dateStr, timeStr, room, courseId, 1);
                    map.put(key, row);
                } else {
                    row.increment();
                }
            }
        }

        // 2) Map'ten listeye al ve sırala (tarih -> saat -> sınıf)
        List<DayRow> rows = new ArrayList<>(map.values());
        rows.sort(Comparator
                .comparing(DayRow::getDate)
                .thenComparing(DayRow::getTime)
                .thenComparing(DayRow::getRoom));

        ObservableList<DayRow> data = FXCollections.observableArrayList(rows);

        // 3) Kolonları tanımla
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
        table.setItems(data);

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
            if (files != null) {
                dialog.close(); // Önce pencereyi kapat
                processAndLoadFiles(files); // Sonra yüklemeye başla
            }
        });

        Label lblOr = new Label("- or -");

        lblOr.setTextFill(Color.web(text));

        dropZone.getChildren().addAll(lblInstruction, lblOr, btnBrowse);

        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            // 1. Dosyaları al
            List<File> files = null;
            if (db.hasFiles()) {
                files = new ArrayList<>(db.getFiles());
                success = true;
            }

            // 2. İşletim sistemine "Tamamdır" de
            event.setDropCompleted(success);
            event.consume();

            // 3. Pencereyi kapat
            dialog.close();

            // 4. İşlemi başlat
            if (success && files != null) {
                List<File> finalFiles = files;
                Platform.runLater(() -> processAndLoadFiles(finalFiles));
            }
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
                FXCollections.observableArrayList(
                        "Student List",
                        "Exam Schedule (Detailed per Student)",
                        "Course Schedule (Exams Tab)",
                        "Day Schedule"));
        cmbType.getSelectionModel().selectFirst();

        Label lblName = new Label("Default Filename");
        lblName.setTextFill(Color.web(text));
        TextField txtName = new TextField("export_data");

        Button btnDoExport = new Button("Choose Location & Export");
        btnDoExport.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");

        btnDoExport.setOnAction(e -> {
            String type = cmbType.getValue();
            String defaultName = txtName.getText().trim();
            if (defaultName.isEmpty())
                defaultName = "export_data";

            // --- DOSYA SEÇİCİ ---
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Export File");
            fileChooser.setInitialFileName(defaultName + ".csv");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

            // Pencereyi aç ve kullanıcının seçtiği dosyayı al
            File selectedFile = fileChooser.showSaveDialog(dialog);

            if (selectedFile != null) {
                String path = selectedFile.getAbsolutePath();
                // Seçilen dosyaya yaz
                if (path.isEmpty()) {
                    Alert a = new Alert(Alert.AlertType.WARNING, "Please choose a file name.");
                    styleDialog(a);
                    a.showAndWait();
                    return;
                }

                // === DB EXPORT ===
                boolean ok = DBManager.exportScheduleToCSV(path);

                Alert alert;
                if (ok) {
                    alert = new Alert(Alert.AlertType.INFORMATION,
                            "Export Completed:\n" + path);
                } else {
                    alert = new Alert(Alert.AlertType.ERROR,
                            "Export FAILED. Check logs.");
                }

                styleDialog(alert);
                alert.showAndWait();

            }
        });

        layout.getChildren().addAll(lblType, cmbType, lblName, txtName, btnDoExport);

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

        if (lblDays != null)
            lblDays.setTextFill(textColor);
        if (lblBlockTime != null)
            lblBlockTime.setTextFill(textColor);

        // Buttons & Inputs
        String btnStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-background-radius: 4;";
        btnHelp.setStyle(btnStyle);
        btnImport.setStyle(btnStyle);
        btnExport.setStyle(btnStyle);
        btnApply.setStyle(btnStyle);

        String inputStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-prompt-text-fill: "
                + prompt + ";";
        txtSearch.setStyle(inputStyle);
        if (txtDays != null)
            txtDays.setStyle(inputStyle);
        if (txtBlockTime != null)
            txtBlockTime.setStyle(inputStyle);

        // Customize Butonu
        if (btnCustomize != null) {
            btnCustomize.setStyle("-fx-background-color: " + btn + "; -fx-text-fill: " + text
                    + "; -fx-background-radius: 4; -fx-border-width: 0;");
        }
        txtBlockStart.setStyle(inputStyle);
        txtBlockEnd.setStyle(inputStyle);
        txtTimeStart.setStyle(inputStyle);
        txtTimeEnd.setStyle(inputStyle);

        // Date Pickers
        styleDatePicker(startDate, btn, text, prompt);
        styleDatePicker(endDate, btn, text, prompt);

        // Liste arkaplan rengini güncelle
        uploadedFilesList.setStyle("-fx-background-color: " + btn + "; -fx-control-inner-background: " + btn + ";");

        // --- Placeholder ---
        Label placeholder = (Label) uploadedFilesList.getPlaceholder();
        if (placeholder != null) {
            placeholder.setTextFill(textColor); // Temaya uygun metin rengi ata
        }

        // Hücreleri yeniden çiz
        uploadedFilesList.refresh();

        updateToggleStyles();

        // --- Aktif görünümü yenile ---
        if (currentDetailItem != null) {
            // Eğer bir detay sayfasındaysak orayı yenile
            if (currentDetailItem instanceof Student) {
                showStudentScheduleDetail((Student) currentDetailItem);
            } else if (currentDetailItem instanceof Course) {
                showCourseStudentList((Course) currentDetailItem);
            }
        } else {
            // Detayda değilsek normal sekmeyi göster
            if (tglStudents.isSelected())
                showStudentList();
            else if (tglExams.isSelected())
                showExamList();
            else if (tglDays.isSelected())
                showDayList();
        }
    }

    // Helper method for CSV escaping
    private String csvEscape(String s) {
        if (s == null)
            return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String escaped = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private boolean exportData(String type, File file) {
        if (file == null)
            return false;
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {
            if ("Student List".equals(type)) {
                writer.write("Student ID,Total Exams (current filters)");
                writer.newLine();
                for (Student s : allStudents) {
                    List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                    exams = filterExamsByCurrentFilters(exams);
                    writer.write(s.getId() + "," + exams.size());
                    writer.newLine();
                }
            } else if ("Exam Schedule (Detailed per Student)".equals(type)) {
                writer.write("Student ID,Course ID,Date,Time,Room,Seat");
                writer.newLine();
                for (Map.Entry<String, List<StudentExam>> entry : studentScheduleMap.entrySet()) {
                    String sid = entry.getKey();
                    for (StudentExam exam : entry.getValue()) {
                        if (exam.getTimeslot() != null && timeslotMatchesFilters(exam.getTimeslot())) {
                            writer.write(String.format("%s,%s,%s,%s,%s,%d",
                                    sid, exam.getCourseId(), exam.getTimeslot().getDate(),
                                    exam.getTimeslot().getStart() + "-" + exam.getTimeslot().getEnd(),
                                    exam.getClassroomId(), exam.getSeatNo()));
                            writer.newLine();
                        }
                    }
                }
            } else if ("Course Schedule (Exams Tab)".equals(type)) {

                writer.write("Course Code,Duration,Date,Time,Rooms,Students,Status");
                writer.newLine();
                for (Course c : allCourses) {
                }
            } else if ("Day Schedule".equals(type)) {
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void exportSingleStudentSchedule(Student student) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Student Schedule");
        // Varsayılan isim: StudentID_Schedule.csv
        fileChooser.setInitialFileName(student.getId() + "_Schedule.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (Excel)", "*.csv"));

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {

                writer.write('\ufeff');

                // Başlık Satırı
                writer.write("Student ID;Course ID;Date;Time;Room;Seat");
                writer.newLine();

                List<StudentExam> exams = studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());

                exams = filterExamsByCurrentFilters(exams);

                for (StudentExam exam : exams) {

                    String line = String.format("%s,%s,%s,%s,%s,%d",
                            csvEscape(student.getId()),
                            csvEscape(exam.getCourseId()),
                            csvEscape(exam.getTimeslot().getDate().toString()),
                            csvEscape(exam.getTimeslot().getStart() + " - " + exam.getTimeslot().getEnd()),
                            csvEscape(exam.getClassroomId()),
                            exam.getSeatNo());

                    writer.write(line);
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

            // Başlangıç Durumu Rengi
            if (initialValue) {
                trigger.setTranslateX(width / 2 - radius - 2);

                background.setFill(Color.web("#0E639C"));
                background.setStroke(Color.web("#0E639C"));
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

    // =============================================================
    // YARDIMCI: DOĞAL SIRALAMA (Natural Sort Comparator)
    // =============================================================

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

    // =============================================================
    // ADVANCED EXAM CUSTOMIZATION (Multi-Group Rule Editor)
    // =============================================================

    private void showCustomizationDialog(Stage owner) {
        // Eğer dersler yüklü değilse uyarı ver
        if (allCourses.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please load courses first.");
            styleDialog(alert);
            alert.show();
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Customize Exam Rules (Layered Rules)");

        // Ana Layout
        BorderPane mainLayout = new BorderPane();
        String bg = isDarkMode ? DARK_BG : LIGHT_BG;
        mainLayout.setStyle("-fx-background-color: " + bg + ";");

        // --- Kural Gruplarının Listesi ---
        VBox groupsContainer = new VBox(10);
        groupsContainer.setPadding(new Insets(10));
        groupsContainer.setStyle("-fx-background-color: transparent;");

        ScrollPane scrollPane = new ScrollPane(groupsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: " + bg + ";");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // --- Butonlar ---
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
            runSchedulerLogic();
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

    // =============================================================
    // HELPER CLASS: Kural Grubu Paneli (İç Sınıf)
    // =============================================================

    private class RuleGroupPane extends VBox {
        // Seçili dersleri tutan liste
        private final List<Course> selectedCourses = new ArrayList<>();

        private final Label lblSelectionInfo;
        private final TextField txtDuration;
        private final TextField txtMinCap;
        private final TextField txtMaxCap;
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

            // 3. Ayarlar
            GridPane settingsGrid = new GridPane();
            settingsGrid.setHgap(10);
            settingsGrid.setVgap(5);
            String promptColor = isDarkMode ? DARK_PROMPT : LIGHT_PROMPT;
            String inputBg = isDarkMode ? DARK_BTN : LIGHT_BTN;
            String inputText = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
            String commonStyle = "-fx-background-color: " + inputBg + "; -fx-text-fill: " + inputText
                    + "; -fx-prompt-text-fill: " + promptColor + ";";

            Label lblDur = new Label("Duration (min):");
            lblDur.setTextFill(Color.web(inputText));
            txtDuration = new TextField();
            txtDuration.setPromptText("Keep Orig.");
            txtDuration.setStyle(commonStyle);
            txtDuration.setPrefWidth(90);

            Label lblMin = new Label("Min Room Cap:");
            lblMin.setTextFill(Color.web(inputText));
            txtMinCap = new TextField();
            txtMinCap.setPromptText("0 (Any)");
            txtMinCap.setStyle(commonStyle);
            txtMinCap.setPrefWidth(90);

            Label lblMax = new Label("Max Room Cap:");
            lblMax.setTextFill(Color.web(inputText));
            txtMaxCap = new TextField();
            txtMaxCap.setPromptText("0 (No Limit)");
            txtMaxCap.setStyle(commonStyle);
            txtMaxCap.setPrefWidth(90);

            settingsGrid.add(lblDur, 0, 0);
            settingsGrid.add(txtDuration, 0, 1);
            settingsGrid.add(lblMin, 1, 0);
            settingsGrid.add(txtMinCap, 1, 1);
            settingsGrid.add(lblMax, 2, 0);
            settingsGrid.add(txtMaxCap, 2, 1);

            getChildren().addAll(topRow, new Separator(), selectionRow, settingsGrid);
        }

        public void setParentContainer(VBox newParent) {
            this.parentContainer = newParent;
        }

        private void removeSelf() {
            parentContainer.getChildren().remove(this);
            ruleGroups.remove(this);
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

            // --- Çakışma Kontrolü ---
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

                        // Başka grupta var mı?
                        boolean isTaken = unavailableCourseIds.contains(item.getId());
                        // Şu anki grupta zaten seçili mi? (ID kontrolü ile)
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
                                    // Listede yoksa ekle (Duplicate önle)
                                    if (selectedCourses.stream().noneMatch(c -> c.getId().equals(item.getId()))) {
                                        selectedCourses.add(item);
                                    }
                                } else {
                                    // Listeden çıkar (ID ile bulup sil)
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

            // Arama Filtresi
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

        private void updateLabel() {
            lblSelectionInfo.setText(selectedCourses.size() + " courses selected");
        }

        public int applyRulesToSelectedCourses() {
            if (selectedCourses.isEmpty())
                return 0;

            int durationVal = -1;
            int minCapVal = -1;
            int maxCapVal = -1;

            try {
                if (!txtDuration.getText().trim().isEmpty())
                    durationVal = Integer.parseInt(txtDuration.getText().trim());
                if (!txtMinCap.getText().trim().isEmpty())
                    minCapVal = Integer.parseInt(txtMinCap.getText().trim());
                if (!txtMaxCap.getText().trim().isEmpty())
                    maxCapVal = Integer.parseInt(txtMaxCap.getText().trim());
            } catch (NumberFormatException e) {
            }

            // Ana listedeki (allCourses) referansları güncelle
            for (Course selectedC : selectedCourses) {
                // allCourses içindeki gerçek nesneyi bul
                for (Course realC : allCourses) {
                    if (realC.getId().equals(selectedC.getId())) {
                        if (durationVal > 0)
                            realC.setDurationMinutes(durationVal);
                        if (minCapVal >= 0)
                            realC.setMinRoomCapacity(minCapVal);
                        if (maxCapVal >= 0)
                            realC.setMaxRoomCapacity(maxCapVal);
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
                // Seçili her ders için kuralı veritabanına kaydet
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

            // Seçili dersleri listeye at
            this.selectedCourses.clear();
            this.selectedCourses.addAll(coursesToSelect);

            updateLabel();
        }
    }

    // =============================================================
    // CUSTOM CONFIRMATION DIALOG
    // =============================================================
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
        DBManager.saveSetting("blockMin", txtBlockStart.getText());
        DBManager.saveSetting("blockMax", txtBlockEnd.getText());
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

    public static void main(String[] args) {
        launch(args);
    }
}