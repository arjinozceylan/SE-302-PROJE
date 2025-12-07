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
    private boolean scheduleLoadedFromDB = false;


    // --- DATA HOLDERS ---
    private List<Student> allStudents = new ArrayList<>();
    private List<Course> allCourses = new ArrayList<>();
    private List<Classroom> allClassrooms = new ArrayList<>();
    private List<Enrollment> allEnrollments = new ArrayList<>();

    private final List<File> loadedFileCache = new ArrayList<>();

    // --- ERROR LOGGING ---
    private final List<String> errorLog = new ArrayList<>();

    // Map: StudentID -> List of Scheduled Exams (Result from ExamScheduler)
    private Map<String, List<StudentExam>> studentScheduleMap = new HashMap<>();
    // ExamScheduler içinden gelen "neden schedule edilemedi" mesajları
    private Map<String, String> lastUnscheduledReasons = new HashMap<>();
    // UI Table Data Sources
    private ObservableList<Student> studentObservableList = FXCollections.observableArrayList();
    private ObservableList<Course> examObservableList = FXCollections.observableArrayList();

    // UI Components
    private BorderPane root;
    private HBox topMenu, bottomBar;
    private VBox leftPane;
    private Label lblErrorCount, lblSectionTitle, lblDate, lblBlock, lblTime, lblUploaded, lblStats;
    private ListView<String> uploadedFilesList;
    private Button btnHelp, btnImport, btnExport, btnApply;
    private TextField txtSearch, txtBlockStart, txtBlockEnd, txtTimeStart, txtTimeEnd;
    private DatePicker startDate, endDate;
    private ToggleButton tglStudents, tglExams, tglDays;
    private ToggleSwitch themeSwitch;
    private Stage primaryStage;
    private Stage loadingStage;

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("DB PATH = " + new java.io.File("scheduler.db").getAbsolutePath());
            DBManager.initializeDatabase();
            System.out.println("DATABASE INITIALIZED");
            Map<String, List<StudentExam>> loaded = DBManager.loadSchedule();

            if (!loaded.isEmpty()) {
                System.out.println("Loaded schedule from DB: " + loaded.size() + " students");
                studentScheduleMap = loaded;
                scheduleLoadedFromDB = true;

                // Öğrenci listesini DB’den doldur
                studentObservableList.setAll(
                        loaded.keySet().stream()
                                .map(Student::new)
                                .toList()
                );





            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        this.primaryStage = primaryStage;
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

        lblErrorCount.setOnMouseClicked(e -> showErrorLogDialog());

        btnImport = createStyledButton("Import \u2193");
        btnImport.setOnAction(e -> showImportDialog(primaryStage));

        btnExport = createStyledButton("Export \u2191");
        btnExport.setOnAction(e -> showExportDialog(primaryStage));

        btnApply = createStyledButton("Apply");
        btnApply.setOnAction(e -> runSchedulerLogic());

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

        topMenu.getChildren().addAll(btnHelp, lblErrorCount, btnImport, btnExport, btnApply, txtSearch, filters, spacer,
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

                    btnRemove.setOnAction(event -> {
                        // 1. Dosyayı listeden ve cache'den sil
                        String itemToRemove = item;
                        uploadedFilesList.getItems().remove(itemToRemove);

                        // Dosya adını güvenli şekilde al
                        String fileNameToRemove = itemToRemove.split("\n")[0].trim();
                        loadedFileCache.removeIf(f -> f.getName().equals(fileNameToRemove));

                        // 2. YÜKLENİYOR PENCERESİNİ AÇ
                        showLoading();

                        // 3. ARKA PLAN GÖREVİ (Sessiz Yeniden Yükleme)
                        Task<Void> reloadTask = new Task<>() {
                            // Geçici listeler (Veri çakışmasını önlemek için)
                            final List<Student> tempStudents = new ArrayList<>();
                            final List<Course> tempCourses = new ArrayList<>();
                            final List<Classroom> tempClassrooms = new ArrayList<>();
                            final List<Enrollment> tempEnrollments = new ArrayList<>();

                            @Override
                            protected Void call() {
                                // Cache'deki kalan dosyaları oku
                                for (File file : new ArrayList<>(loadedFileCache)) {
                                    try {
                                        String name = file.getName().toLowerCase();
                                        if (name.contains("allstudents") || name.contains("std_id")) {
                                            tempStudents.addAll(CsvDataLoader.loadStudents(file.toPath()));
                                        } else if (name.contains("allcourses") || name.contains("courses")) {
                                            tempCourses.addAll(CsvDataLoader.loadCourses(file.toPath()));
                                        } else if (name.contains("allclassrooms") || name.contains("capacities")) {
                                            tempClassrooms.addAll(CsvDataLoader.loadClassrooms(file.toPath()));
                                        } else if (name.contains("allattendancelists") || name.contains("attendance")) {
                                            tempEnrollments.addAll(CsvDataLoader.loadEnrollments(file.toPath()));
                                        }
                                    } catch (Exception ex) {
                                        // HATA PENCERESİ YOK - Sadece konsola yaz
                                        ex.printStackTrace();
                                    }
                                }
                                return null;
                            }

                            // İŞLEM BAŞARILI BİTERSE
                            @Override
                            protected void succeeded() {
                                // Ana verileri temizle
                                allStudents.clear();
                                allCourses.clear();
                                allClassrooms.clear();
                                allEnrollments.clear();
                                studentScheduleMap.clear();
                                lastUnscheduledReasons.clear();

                                // Yeni verileri aktar
                                allStudents.addAll(tempStudents);
                                allCourses.addAll(tempCourses);
                                allClassrooms.addAll(tempClassrooms);
                                allEnrollments.addAll(tempEnrollments);

                                // UI Güncelle
                                studentObservableList.setAll(allStudents);
                                examObservableList.setAll(allCourses);
                                updateStats();

                                // Tabloyu yenile
                                if (tglStudents.isSelected())
                                    showStudentList();
                                else if (tglExams.isSelected())
                                    showExamList();

                                // Yükleniyor penceresini kapat
                                hideLoading();
                            }

                            // İŞLEM HATA ALIRSA
                            @Override
                            protected void failed() {
                                // HATA PENCERESİ YOK - Sadece yükleniyor'u kapat
                                hideLoading();
                                getException().printStackTrace();
                            }
                        };

                        // Thread'i başlat
                        Thread t = new Thread(reloadTask);
                        t.setDaemon(true);
                        t.start();
                    });

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
        Platform.runLater(() -> {
            refreshStudentsTab();
            refreshExamsTab();
            refreshDaysTab();
        });
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
            // Dikkat çekmesi için arka planı daha parlak yapabiliriz (isteğe bağlı)
        });

        System.err.println(logEntry); // Konsola da bas
    }

    private void showErrorLogDialog() {
        if (errorLog.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "No errors recorded.");
            styleDialog(alert);
            alert.showAndWait();
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Error Log");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        layout.setStyle("-fx-background-color: " + bg + ";");

        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monospace';");

        // Tüm hataları birleştirip yaz
        StringBuilder sb = new StringBuilder();
        for (String err : errorLog) {
            sb.append(err).append("\n");
        }
        textArea.setText(sb.toString());

        Button btnClear = new Button("Clear Errors");
        String btnStyle = "-fx-background-color: " + (isDarkMode ? DARK_BTN : LIGHT_BTN) +
                "; -fx-text-fill: " + (isDarkMode ? DARK_TEXT : LIGHT_TEXT) + ";";
        btnClear.setStyle(btnStyle);
        btnClear.setOnAction(e -> {
            errorLog.clear();
            lblErrorCount.setText("Errors: 0");
            dialog.close();
        });

        layout.getChildren().addAll(new Label("Session Errors:"), textArea, btnClear);

        ((Label) layout.getChildren().get(0)).setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));

        Scene scene = new Scene(layout, 500, 400);
        dialog.setScene(scene);
        dialog.show();
    }

    // =============================================================
    // FILE PROCESSING
    // =============================================================

    private void processAndLoadFiles(List<File> files) {
        for (File file : files) {
            // Eğer dosya zaten yüklüyse tekrar yükleme (Duplicate önleme)
            if (loadedFileCache.contains(file))
                continue;

            String name = file.getName().toLowerCase();
            boolean success = false; // Yükleme başarılı mı?

            try {
                // 1. STUDENTS
                if (name.contains("allstudents") || name.contains("std_id")) {
                    List<Student> loaded = CsvDataLoader.loadStudents(file.toPath());
                    allStudents.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Students: " + loaded.size() + ")");
                    success = true;
                }
                // 2. COURSES
                else if (name.contains("allcourses") || name.contains("courses")) {
                    List<Course> loaded = CsvDataLoader.loadCourses(file.toPath());
                    allCourses.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Courses: " + loaded.size() + ")");
                    success = true;
                }
                // 3. CLASSROOMS
                else if (name.contains("allclassrooms") || name.contains("capacities")) {
                    List<Classroom> loaded = CsvDataLoader.loadClassrooms(file.toPath());
                    allClassrooms.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Rooms: " + loaded.size() + ")");
                    success = true;
                }
                // 4. ATTENDANCE
                else if (name.contains("allattendancelists") || name.contains("attendance")) {
                    List<Enrollment> loaded = CsvDataLoader.loadEnrollments(file.toPath());
                    allEnrollments.addAll(loaded);
                    uploadedFilesList.getItems().add(file.getName() + "\n(Links: " + loaded.size() + ")");
                    success = true;
                } else {
                    String msg = "Skipping unknown file type: " + file.getName();
                    uploadedFilesList.getItems().add(file.getName() + " [Unknown]");
                    logError(msg);
                }

                // BAŞARILIYSA CACHE'E EKLE
                if (success) {
                    loadedFileCache.add(file);
                }

            } catch (Exception e) {
                String errorMsg = "Error loading " + file.getName() + ": " + e.getMessage();
                logError(errorMsg);
                Alert alert = new Alert(Alert.AlertType.ERROR, errorMsg);
                styleDialog(alert);
                alert.show();
                e.printStackTrace();
            }
        }

        // UI Güncelle
        studentObservableList.setAll(allStudents);
        examObservableList.setAll(allCourses);
        updateStats();

        // Otomatik Tetikleme (Veriler tam ise)
        if (!allStudents.isEmpty() && !allCourses.isEmpty() && !allClassrooms.isEmpty() && !allEnrollments.isEmpty()) {
            runSchedulerLogic();
        }
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
        if (loadingStage != null && loadingStage.isShowing()) {
            return;
        }

        loadingStage = new Stage();
        loadingStage.initOwner(primaryStage);
        loadingStage.initModality(Modality.WINDOW_MODAL);
        loadingStage.setResizable(false);
        loadingStage.setTitle("Scheduling...");

        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));

        ProgressIndicator indicator = new ProgressIndicator();
        Label label = new Label("Exam schedule is being generated...");
        label.setWrapText(true);

        box.getChildren().addAll(indicator, label);

        Scene scene = new Scene(box, 320, 150);
        loadingStage.setScene(scene);
        loadingStage.show();
    }

    private void hideLoading() {
        if (loadingStage != null) {
            loadingStage.close();
            loadingStage = null;
        }
    }

    // =============================================================
    // SCHEDULER LOGIC (Integration Point)
    // =============================================================

    private void runSchedulerLogic() {
        System.out.println("UI: Calling backend scheduler...");

        // 0) Minimum veri kontrolü
        if (allStudents.isEmpty() || allCourses.isEmpty()
                || allClassrooms.isEmpty() || allEnrollments.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "Please import all required CSV files before generating the schedule.");
                styleDialog(alert);
                alert.showAndWait();
            });
            return;
        }

        // 1) Tarih/saat filtrelerinden DayWindow üret
        List<DayWindow> dayWindows = buildDayWindowsFromFilters();
        if (dayWindows.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "Please select a valid date range.");
                styleDialog(alert);
                alert.showAndWait();
            });
            return;
        }

        showLoading();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                ExamScheduler scheduler = new ExamScheduler();

                // 2. Algoritmayı çalıştır
                Map<String, List<StudentExam>> scheduleResult = scheduler.run(
                        allStudents, allCourses, allEnrollments, allClassrooms, dayWindows);
                // === WRITE RESULTS TO DATABASE ===
                int dbCount = 0;
                for (List<StudentExam> list : scheduleResult.values()) {
                    for (StudentExam se : list) {
                        DBManager.insertSchedule(se);
                        dbCount++;
                    }
                }
                System.out.println("DB WRITE COMPLETE: " + dbCount + " schedule rows inserted.");


                // Planlanamayan derslerin sebeplerini al
                Map<String, String> reasons = scheduler.getUnscheduledReasons();

                Platform.runLater(() -> {
                    studentScheduleMap = scheduleResult;
                    lastUnscheduledReasons = reasons;

                    if (!reasons.isEmpty()) {
                        // 1. Her bir hatayı Errors sistemine kaydet
                        for (Map.Entry<String, String> entry : reasons.entrySet()) {
                            String courseId = entry.getKey();
                            String reason = entry.getValue();
                            logError("Scheduling Failed: " + courseId + " -> " + reason);
                        }

                        // 2. Kullanıcıya basit bir uyarı göster
                        Alert alert = new Alert(Alert.AlertType.WARNING,
                                "Scheduling completed with errors.\n" +
                                        reasons.size() + " courses could not be scheduled.\n" +
                                        "Please check the 'Errors' log (top left) for details.");
                        styleDialog(alert);
                        alert.show();
                    }

                    int totalScheduledExams = studentScheduleMap.values().stream().mapToInt(List::size).sum();
                    lblStats.setText(String.format("Scheduled: %d total exam entries | %d students assigned",
                            totalScheduledExams, studentScheduleMap.size()));

                    // Görünümü yenile
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
            Throwable ex = task.getException();
            String msg = "Critical Scheduler Error: " + (ex != null ? ex.getMessage() : "Unknown");
            logError(msg);
            ex.printStackTrace();
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
     * - Saat aralığı: sınav aralığı seçilen saat aralığı ile ÖRTÜŞÜYOR mu?
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

    // Bu ders aslında global schedule'da var mı? (Filtreye bakmadan kontrol)
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

    private void showCourseStudentList(Course course) {
        // 1. Ana Kapsayıcı (ScrollPane)
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(
                "-fx-background-color: transparent; -fx-background: " + (isDarkMode ? DARK_BG : LIGHT_BG) + ";");

        // 2. İçerik Kutusu (VBox)
        VBox contentBox = new VBox(20);
        contentBox.setPadding(new Insets(20));
        contentBox.setStyle("-fx-background-color: " + (isDarkMode ? DARK_BG : LIGHT_BG) + ";");

        // --- Header (Geri Butonu ve Başlık) ---
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Button btnBack = new Button("\u2190 Back to Exams");

        // --- Buton Görünümü ---
        String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;
        String txtColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        String borderColor = isDarkMode ? "#666" : "#CCC";

        btnBack.setStyle("-fx-background-color: " + btnColor + "; " +
                "-fx-text-fill: " + txtColor + "; " +
                "-fx-background-radius: 4; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-radius: 4; " +
                "-fx-font-weight: bold;");

        btnBack.setOnAction(e -> showExamList());

        Label lblTitle = new Label("Exam Rolls: " + course.getId());
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        lblTitle.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));

        header.getChildren().addAll(btnBack, lblTitle);
        contentBox.getChildren().addAll(header, new Separator());

        // --- VERİ HAZIRLAMA ---
        List<Student> enrolledStudents = new ArrayList<>();
        Set<String> enrolledIds = new HashSet<>();
        for (Enrollment e : allEnrollments) {
            if (e.getCourseId().equals(course.getId())) {
                enrolledIds.add(e.getStudentId());
            }
        }
        for (Student s : allStudents) {
            if (enrolledIds.contains(s.getId())) {
                enrolledStudents.add(s);
            }
        }

        // Öğrencileri Sınıflarına Göre Grupla
        Map<String, List<Student>> studentsByRoom = new HashMap<>();

        for (Student s : enrolledStudents) {
            String room = findStudentRoom(s.getId(), course.getId());
            studentsByRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(s);
        }

        // Oda İsimlerini Doğal Sıralama ile Sırala
        List<String> sortedRooms = new ArrayList<>(studentsByRoom.keySet());
        sortedRooms.sort((r1, r2) -> {
            if (r1.equals("-"))
                return 1;
            if (r2.equals("-"))
                return -1;
            return naturalCompare(r1, r2);
        });

        // --- ARAYÜZ OLUŞTURMA DÖNGÜSÜ ---
        if (sortedRooms.isEmpty()) {
            Label emptyLbl = new Label("No students enrolled or scheduled.");
            emptyLbl.setTextFill(Color.GRAY);
            contentBox.getChildren().add(emptyLbl);
        }

        for (String room : sortedRooms) {
            List<Student> roomStudents = studentsByRoom.get(room);

            // Öğrencileri doğal sırala
            roomStudents.sort((s1, s2) -> naturalCompare(s1.getId(), s2.getId()));

            // A) Alt Başlık
            String headerText = room.equals("-") ? "Unassigned / Waiting List" : room;
            Label lblRoomHeader = new Label(headerText + " (" + roomStudents.size() + " Students)");
            lblRoomHeader.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            lblRoomHeader.setTextFill(Color.web(ACCENT_COLOR));
            lblRoomHeader.setStyle(
                    "-fx-border-color: transparent transparent #666 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 5 0;");

            // B) Küçük Tablo
            TableView<Student> roomTable = new TableView<>();
            styleTableView(roomTable);
            roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<Student, String> colId = new TableColumn<>("Student ID");
            colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

            roomTable.getColumns().add(colId);
            roomTable.setItems(FXCollections.observableArrayList(roomStudents));

            int rowHeight = 35;
            int headerHeight = 35;
            int tableHeight = (roomStudents.size() * rowHeight) + headerHeight + 5;

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

    private void showStudentList() {
        TableView<Student> table = new TableView<>();
        table.setPlaceholder(new Label("No students data loaded."));
        styleTableView(table);

        // Student ID kolonı
        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));

        // YENİ: Öğrencinin kaç sınavı var? (Exams)
        TableColumn<Student, String> colExamCount = new TableColumn<>("Exams");
        colExamCount.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams = studentScheduleMap.getOrDefault(sid, Collections.emptyList());
            exams = filterExamsByCurrentFilters(exams);
            int count = exams.size();
            return new SimpleStringProperty(String.valueOf(count));
        });

        table.getColumns().addAll(colId, colExamCount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(studentObservableList);

        // Satıra tıklayınca o öğrencinin programını aç
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showStudentScheduleDetail(newVal);
            }
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
                cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getStart().toString() + " - " +
                        cell.getValue().getTimeslot().getEnd().toString()));

        TableColumn<StudentExam, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("classroomId"));

        TableColumn<StudentExam, String> colSeat = new TableColumn<>("Seat");
        colSeat.setCellValueFactory(new PropertyValueFactory<>("seatNo"));

        detailTable.getColumns().addAll(colCourse, colDate, colTime, colRoom, colSeat);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Get data from Results Map
        List<StudentExam> exams = studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());
        exams = filterExamsByCurrentFilters(exams);
        detailTable.setItems(FXCollections.observableArrayList(exams));

        detailView.getChildren().addAll(header, new Separator(), detailTable);
        root.setCenter(detailView);
    }
    // =============================================================
// REFRESH EXAMS TAB (DB'den yüklenen schedule ile uyumlu)
// =============================================================
    private void refreshExamsTab() {
        // Eğer Exams tab açık değilse bile tabloyu arkaplanda güncelle
        List<Course> generatedCourseList = new ArrayList<>();

        Map<String, List<StudentExam>> grouped = new HashMap<>();
        for (List<StudentExam> list : studentScheduleMap.values()) {
            for (StudentExam se : list) {
                grouped.computeIfAbsent(se.getCourseId(), k -> new ArrayList<>()).add(se);
            }
        }

        for (String courseId : grouped.keySet()) {
            int duration = findCourseDuration(courseId);
            if (duration == 0) duration = 90;

            generatedCourseList.add(new Course(courseId, duration));
        }

        // Observable liste aktar
        examObservableList.setAll(generatedCourseList);

        // Eğer kullanıcı şu anda Exams tabındaysa tabloyu yenile
        if (tglExams.isSelected()) {
            showExamList();
        }
    }



    // =============================================================
    // SHOW EXAM LIST (Sınavlar Sekmesi)
    // =============================================================

    private void showExamList() {
        TableView<Course> table = new TableView<>();
        table.setPlaceholder(new Label("No courses loaded or no schedule generated."));
        styleTableView(table);

        javafx.util.Callback<TableColumn<Course, String>, TableCell<Course, String>> coloredCellFactory = column -> new TableCell<Course, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(""); // Boşsa stili sıfırla
                } else {
                    setText(item);

                    // Satırdaki veriyi (Course) al
                    TableRow<Course> currentRow = getTableRow();
                    Course course = (currentRow != null) ? currentRow.getItem() : null;

                    if (course != null) {
                        // Durumu kontrol et
                        String status = getCourseStatusText(course.getId());
                        if (status != null && status.toUpperCase().contains("UNSCHEDULED")) {
                            // KIRMIZI (Koyu mod için parlak, açık mod için koyu kırmızı)
                            String color = isDarkMode ? "#FF6B6B" : "#D32F2F";
                            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                        } else {

                            setStyle("");
                        }
                    } else {
                        setStyle("");
                    }
                }
            }
        };

        // 1) Course Code
        TableColumn<Course, String> colCode = new TableColumn<>("Course Code");
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colCode.setCellFactory(coloredCellFactory); // Renk uygula

        // 2) Duration
        TableColumn<Course, String> colDur = new TableColumn<>("Duration (min)");
        colDur.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));
        colDur.setCellFactory(coloredCellFactory); // Renk uygula

        // 3) Date
        TableColumn<Course, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(getCourseDate(cell.getValue().getId())));
        colDate.setCellFactory(coloredCellFactory); // Renk uygula

        // 4) Time
        TableColumn<Course, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(getCourseTimeRange(cell.getValue().getId())));
        colTime.setCellFactory(coloredCellFactory); // Renk uygula

        // 5) Rooms
        TableColumn<Course, String> colRooms = new TableColumn<>("Rooms");
        colRooms.setCellValueFactory(cell -> new SimpleStringProperty(getCourseRooms(cell.getValue().getId())));
        colRooms.setCellFactory(coloredCellFactory); // Renk uygula

        // 6) #Students
        TableColumn<Course, String> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(getCourseStudentCount(cell.getValue().getId()))));
        colCount.setCellFactory(coloredCellFactory); // Renk uygula

        // 7) Status / Reason
        TableColumn<Course, String> colStatus = new TableColumn<>("Status / Reason");
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(getCourseStatusText(cell.getValue().getId())));

        // Status kolonu için özel CellFactory (Hem Renk Hem Tooltip)
        colStatus.setCellFactory(column -> new TableCell<Course, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);

                    // --- Tooltip ---
                    Tooltip tip = new Tooltip(item);
                    tip.setWrapText(true);
                    tip.setMaxWidth(400);
                    setTooltip(tip);

                    // --- Renk ---
                    TableRow<Course> currentRow = getTableRow();
                    Course course = (currentRow != null) ? currentRow.getItem() : null;
                    if (course != null) {
                        String status = getCourseStatusText(course.getId());
                        if (status != null && status.toUpperCase().contains("UNSCHEDULED")) {
                            String color = isDarkMode ? "#FF6B6B" : "#D32F2F";
                            setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            }
        });

        colStatus.setMinWidth(200);
        colStatus.setPrefWidth(300);

        // --- TABLO AYARLARI ---
        table.getColumns().setAll(colCode, colDur, colDate, colTime, colRooms, colCount, colStatus);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // --- SIRALAMA VE VERİ ---
        javafx.collections.transformation.SortedList<Course> sortedExams = new javafx.collections.transformation.SortedList<>(
                examObservableList);
        sortedExams.setComparator((c1, c2) -> naturalCompare(c1.getId(), c2.getId()));
        table.setItems(sortedExams);

        // --- TIKLAMA OLAYI ---
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showCourseStudentList(newVal);
            }
        });

        root.setCenter(table);
    }

    private void showDayList() {
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

                // Aynı gün, aynı saat aralığı, aynı sınıf ve aynı ders için tek satır olsun
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
                dialog.close();
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
                // Seçilen dosyaya yaz
                boolean success = exportData(type, selectedFile);

                if (success) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION,
                            "Export Saved to:\n" + selectedFile.getAbsolutePath());
                    dialog.close(); // Başarılıysa kapat
                    styleDialog(alert);
                    alert.show();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Export Failed.");
                    styleDialog(alert);
                    alert.show();
                }
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

        // Buttons & Inputs
        String btnStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-background-radius: 4;";
        btnHelp.setStyle(btnStyle);
        btnImport.setStyle(btnStyle);
        btnExport.setStyle(btnStyle);
        btnApply.setStyle(btnStyle);

        String inputStyle = "-fx-background-color: " + btn + "; -fx-text-fill: " + text + "; -fx-prompt-text-fill: "
                + prompt + ";";
        txtSearch.setStyle(inputStyle);
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

        // Aktif görünümü yenile
        if (tglStudents.isSelected())
            showStudentList();
        else if (tglExams.isSelected())
            showExamList();
        else if (tglDays.isSelected())
            showDayList();
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
        // Dosya seçilmediyse işlem yapma
        if (file == null)
            return false;

        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {

            // STUDENT LIST -> Students tabındaki özet (filtrelere göre)
            if ("Student List".equals(type)) {
                writer.write("Student ID,Total Exams (current filters)");
                writer.newLine();

                for (Student s : allStudents) {
                    List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                    exams = filterExamsByCurrentFilters(exams); // tarih/saat filtresi uygula
                    writer.write(s.getId() + "," + exams.size());
                    writer.newLine();
                }
            }

            // EXAM SCHEDULE (DETAILED) -> Her öğrenci-sınav kaydı (filtrelere göre)
            else if ("Exam Schedule (Detailed per Student)".equals(type)) {
                writer.write("Student ID,Course ID,Date,Time,Room,Seat");
                writer.newLine();

                for (Map.Entry<String, List<StudentExam>> entry : studentScheduleMap.entrySet()) {
                    String sid = entry.getKey();
                    for (StudentExam exam : entry.getValue()) {
                        Timeslot ts = exam.getTimeslot();
                        if (ts == null)
                            continue;
                        // Tarih / saat filtresi uygulanıyor
                        if (!timeslotMatchesFilters(ts))
                            continue;

                        String date = ts.getDate().toString();
                        String time = ts.getStart().toString() + " - " + ts.getEnd().toString();

                        String line = String.format("%s,%s,%s,%s,%s,%d",
                                sid,
                                exam.getCourseId(),
                                date,
                                time,
                                exam.getClassroomId(),
                                exam.getSeatNo());
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            // COURSE SCHEDULE (EXAMS TAB)
            else if ("Course Schedule (Exams Tab)".equals(type)) {
                writer.write("Course Code,Duration (min),Date,Time,Rooms,Students,Status/Reason");
                writer.newLine();

                for (Course c : allCourses) {
                    String courseId = c.getId();
                    String date = getCourseDate(courseId);
                    String timeRange = getCourseTimeRange(courseId);
                    String rooms = getCourseRooms(courseId);
                    int count = getCourseStudentCount(courseId);
                    String status = getCourseStatusText(courseId);

                    String line = String.format("%s,%d,%s,%s,%s,%d,%s",
                            csvEscape(courseId),
                            c.getDurationMinutes(),
                            csvEscape(date),
                            csvEscape(timeRange),
                            csvEscape(rooms),
                            count,
                            csvEscape(status));
                    writer.write(line);
                    writer.newLine();
                }
            }

            // DAY SCHEDULE -> Days tabındakine denk gelen özet (filtrelere göre)
            else if ("Day Schedule".equals(type)) {
                writer.write("Date,Time,Room,Course,Student Count");
                writer.newLine();

                Map<String, DayRow> map = new LinkedHashMap<>();

                for (List<StudentExam> list : studentScheduleMap.values()) {
                    for (StudentExam se : list) {
                        Timeslot ts = se.getTimeslot();
                        if (ts == null)
                            continue;
                        // Tarih / saat filtresi
                        if (!timeslotMatchesFilters(ts))
                            continue;

                        String dateStr = ts.getDate().toString();
                        String timeStr = ts.getStart().toString() + " - " + ts.getEnd().toString();
                        String room = se.getClassroomId();
                        String courseId = se.getCourseId();

                        String key = dateStr + "|" + timeStr + "|" + room + "|" + courseId;

                        DayRow row = map.get(key);
                        if (row == null) {
                            // İlk kez görüyoruz → 1 öğrenci
                            row = new DayRow(dateStr, timeStr, room, courseId, 1);
                            map.put(key, row);
                        } else {
                            // Zaten varsa sayacı artır
                            row.increment();
                        }
                    }
                }

                List<DayRow> rows = new ArrayList<>(map.values());
                rows.sort(Comparator.comparing(DayRow::getDate)
                        .thenComparing(DayRow::getTime)
                        .thenComparing(DayRow::getRoom));

                for (DayRow r : rows) {
                    writer.write(String.format("%s,%s,%s,%s,%d",
                            r.getDate(), r.getTime(), r.getRoom(), r.getCourseId(), r.getStudentCount()));
                    writer.newLine();
                }
            }

            // Tanınmayan type
            else {
                return false;
            }

            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
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
    private void refreshStudentsTab() {
        if (tglStudents.isSelected()) {
            showStudentList();
        }
    }

    private void refreshDaysTab() {
        if (tglDays.isSelected()) {
            showDayList();
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

    public static void main(String[] args) {
        launch(args);
    }
}