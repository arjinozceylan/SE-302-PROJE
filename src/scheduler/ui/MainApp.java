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
    private Stage loadingStage;

    // Kural Gruplarını Tutmak İçin Liste
    private final List<RuleGroupPane> ruleGroups = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        try {
            System.out.println("DB PATH = " + new java.io.File("scheduler.db").getAbsolutePath());
            DBManager.initializeDatabase();
            // DB'den veri yükleme kısmı (Opsiyonel, varsa kullanır)
            Map<String, List<StudentExam>> loaded = DBManager.loadSchedule();
            if (!loaded.isEmpty()) {
                studentScheduleMap = loaded;
                scheduleLoadedFromDB = true;
                studentObservableList.setAll(loaded.keySet().stream().map(Student::new).toList());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.primaryStage = primaryStage;

        //
        root = new BorderPane();

        // --- HEADER / TOOLBAR ---
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

        // --- LEFT SIDEBAR ---
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

        // Date Listeners
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

        // Block Listeners
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
        // Önceden yüklenen dosyaları DB'den al
        List<String> prevFiles = DBManager.loadUploadedFiles();
        for (String name : prevFiles) {
            uploadedFilesData.add(new UploadedFileItem(null, name));
        }

        uploadedFilesList.setPrefHeight(200);
        VBox.setVgrow(uploadedFilesList, Priority.ALWAYS);
        uploadedFilesList.setPlaceholder(new Label("No files loaded"));

        // Custom Cell Factory (Checkbox + Text + Remove + Confirmation)
        uploadedFilesList.setCellFactory(param -> new ListCell<UploadedFileItem>() {
            @Override
            protected void updateItem(UploadedFileItem item, boolean empty) {
                super.updateItem(item, empty);

                // Tema renklerini belirle
                String btnColor = isDarkMode ? DARK_BTN : LIGHT_BTN;
                String textColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
                setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: " + textColor + ";");

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    // 1. Checkbox (Seçim Durumu)
                    CheckBox cbSelect = new CheckBox();
                    cbSelect.selectedProperty().bindBidirectional(item.isSelected);

                    // 2. Dosya İsmi
                    Label label = new Label(item.displayText);
                    label.setTextFill(Color.web(textColor));
                    label.setWrapText(true);
                    label.setMaxWidth(140);
                    HBox.setHgrow(label, Priority.ALWAYS);

                    // 3. Silme Butonu (X)
                    Button btnRemove = new Button("X");
                    btnRemove.setStyle(
                            "-fx-text-fill: #FF6B6B; -fx-font-weight: bold; -fx-background-color: transparent;");

                    btnRemove.setOnAction(event -> {
                        // --- ONAY PENCERESİ ---
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Confirm Remove");
                        alert.setHeaderText("Remove File?");
                        alert.setContentText("Are you sure you want to remove this file?\n\n" + item.displayText +
                                "\n\nThis will clear currently loaded data to ensure consistency.");

                        // Temayı pencereye uygula
                        styleDialog(alert);

                        // Kullanıcının cevabını bekle
                        Optional<ButtonType> result = alert.showAndWait();

                        // "OK" tuşuna basıldıysa sil"
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            // Listeden ve Cache'den sil
                            uploadedFilesData.remove(item);
                            loadedFileCache.remove(item.file);

                            // Hafızadaki verileri temizle
                            allStudents.clear();
                            allCourses.clear();
                            allClassrooms.clear();
                            allEnrollments.clear();
                            studentScheduleMap.clear();
                            lastUnscheduledReasons.clear();

                            // UI Tablolarını temizle
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

        leftPane.getChildren().addAll(
                lblSectionTitle, new Separator(),
                dateBox, new Separator(),
                blockBox, new Separator(),
                timeBox,
                new Separator(),
                btnCustomize,
                sepFiles,
                lblUploaded,
                uploadedFilesList);

        // Bottom Bar
        bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        lblStats = new Label("Total Exams: 0 | Total Students: 0 | Total Classes: 0");
        lblStats.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        bottomBar.getChildren().add(lblStats);

        // BorderPane Yerleşimi
        root.setTop(topMenu);
        root.setLeft(leftPane);
        root.setBottom(bottomBar);

        loadingOverlay = new VBox(20);
        loadingOverlay.setAlignment(Pos.CENTER);

        loadingOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6);");
        loadingOverlay.setVisible(false); // Başlangıçta gizli

        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(60, 60);
        pi.setStyle("-fx-progress-color: " + ACCENT_COLOR + ";");

        Label lblLoad = new Label("Processing Data...");
        lblLoad.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        lblLoad.setTextFill(Color.WHITE);
        lblLoad.setEffect(new DropShadow(5, Color.BLACK));

        loadingOverlay.getChildren().addAll(pi, lblLoad);

        // --- STACKPANE ---
        // En altta 'root' (uygulama), en üstte 'loadingOverlay'
        mainStack = new StackPane(root, loadingOverlay);

        applyTheme();
        showStudentList();

        Scene scene = new Scene(mainStack, 1100, 775);
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
        });

        System.err.println(logEntry); // Konsola da bas
    }

    private void showErrorLogDialog() {
        if (errorLog.isEmpty()) {
            // Hata yoksa küçük bir bilgi verebilirsin veya hiçbir şey yapmazsın
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(root.getScene().getWindow());
        dialog.setTitle("Error Log");

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        String bg = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        layout.setStyle("-fx-background-color: " + bg + ";");

        // Başlık
        Label lblHeader = new Label("Session Errors (" + errorLog.size() + ")");
        lblHeader.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        lblHeader.setTextFill(Color.web(isDarkMode ? DARK_TEXT : LIGHT_TEXT));

        // Hata Alanı (Daha geniş ve okunaklı)
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true); // Satır kaydırma açık
        textArea.setFont(Font.font("Consolas", 12)); // Okunaklı font

        // Alanın dikeyde uzamasını sağla
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Hataları birleştir
        StringBuilder sb = new StringBuilder();
        for (String err : errorLog) {
            sb.append("• ").append(err).append("\n\n"); // Maddeler halinde ve boşluklu
        }
        textArea.setText(sb.toString());

        // Butonlar
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnClear = new Button("Clear Log");
        btnClear.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white;");
        btnClear.setOnAction(e -> {
            errorLog.clear();
            lblErrorCount.setText("Errors: 0");
            dialog.close();
        });

        Button btnClose = new Button("Close");
        btnClose.setOnAction(e -> dialog.close());

        btnBox.getChildren().addAll(btnClear, btnClose);

        layout.getChildren().addAll(lblHeader, textArea, btnBox);

        // Pencere Boyutu (Daha Geniş)
        Scene scene = new Scene(layout, 700, 500);
        dialog.setScene(scene);
        dialog.show();
    }

    // =============================================================
    // FILE PROCESSING
    // =============================================================

    private void processAndLoadFiles(List<File> files) {
        // Yükleme ekranını aç (Kullanıcıyı beklet)
        showLoading();

        // 1. ADIM: UI Thread üzerindeyken mevcut dosya yollarının bir kopyasını al.
        Set<String> existingPaths = new HashSet<>();
        for (UploadedFileItem item : uploadedFilesData) {
            existingPaths.add(item.file.getAbsolutePath());
        }

        // 2. ADIM: Arka Plan Görevi (Sadece dosya filtreleme)
        Task<List<File>> task = new Task<>() {
            @Override
            protected List<File> call() {
                List<File> filesToAdd = new ArrayList<>();

                // Gelen dosyaları kontrol et
                for (File f : files) {
                    // Kopyaladığımız listeden kontrol ediyoruz
                    if (!existingPaths.contains(f.getAbsolutePath())) {
                        filesToAdd.add(f);
                    }
                }
                return filesToAdd;
            }
        };

        // 3. ADIM: İşlem Bittiğinde (UI Thread'e Geri Dönüş)
        task.setOnSucceeded(e -> {
            List<File> newFiles = task.getValue();

            if (!newFiles.isEmpty()) {
                // Artık UI Thread'deyiz, arayüz nesnelerini güvenle oluşturabiliriz
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

                    // Listeye ekle (Varsayılan olarak tikli)
                    DBManager.saveUploadedFile(file.getName());
                    uploadedFilesData.add(new UploadedFileItem(file, file.getName() + "\n(" + type + ")"));
                    loadedFileCache.add(file);
                }
            }

            // Yükleniyor ekranını kapat
            hideLoading();
        });

        // Hata Olursa
        task.setOnFailed(e -> {
            hideLoading();
            logError("File import error: " + task.getException().getMessage());
            task.getException().printStackTrace();
        });

        // Thread'i başlat
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
        System.out.println("UI: Reloading data from CHECKED files...");

        // 1. Önce hafızadaki ESKİ verileri temizle
        allStudents.clear();
        allCourses.clear();
        allClassrooms.clear();
        allEnrollments.clear();
        studentScheduleMap.clear();
        lastUnscheduledReasons.clear();

        // 2. Listede sadece 'Tik'li (Selected) olan dosyaları oku
        boolean anyFileChecked = false;

        for (UploadedFileItem item : uploadedFilesData) {
            if (item.isSelected.get()) { // Sadece seçili olanlar
                anyFileChecked = true;
                File file = item.file;
                try {
                    String name = file.getName().toLowerCase();
                    // Dosya tipine göre ilgili listeye yükle
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

        if (!ruleGroups.isEmpty()) {
            System.out.println("Re-applying custom rules to fresh data...");
            for (RuleGroupPane pane : ruleGroups) {

                pane.applyRulesToSelectedCourses();
            }
        }

        // 3. UI Tablolarını güncelle
        studentObservableList.setAll(allStudents);
        examObservableList.setAll(allCourses);
        updateStats();

        // 4. Hiç dosya seçilmediyse uyarı ver
        if (!anyFileChecked) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "No files are checked! Please check files in the list.");
                styleDialog(alert);
                alert.showAndWait();
            });
            return;
        }

        // 5. Eksik veri kontrolü
        if (allStudents.isEmpty() || allCourses.isEmpty() || allClassrooms.isEmpty() || allEnrollments.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Missing required data. Check files.");
                styleDialog(alert);
                alert.showAndWait();
            });
            return;
        }

        // 6. Tarih/Saat filtrelerini al
        List<DayWindow> dayWindows = buildDayWindowsFromFilters();
        if (dayWindows.isEmpty()) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please select a valid date range.");
                styleDialog(alert);
                alert.showAndWait();
            });
            return;
        }

        // 7. Yükleniyor ekranını aç
        showLoading();

        // 8. Arka Plan Görevi
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DBManager.clearScheduleTable();
                DBManager.clearConflictLog();

                ExamScheduler scheduler = new ExamScheduler();

                // Algoritmayı çalıştır
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
            roomTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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

    private void showStudentList() {
        currentDetailItem = null;
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
        // 1. Hafızaya al (Tema değişirse buraya döneceğiz)
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
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
            if (duration == 0)
                duration = 90;

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

        // --- 1. Sığdırma Politikası (Yatay kaydırmayı engeller) ---
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // --- Renklendirme ve Status Formatı ---
        javafx.util.Callback<TableColumn<Course, String>, TableCell<Course, String>> coloredCellFactory = column -> new TableCell<Course, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                } else {
                    // SATIR VERİSİNİ AL
                    TableRow<Course> currentRow = getTableRow();
                    Course course = (currentRow != null) ? currentRow.getItem() : null;

                    // METNİ AYARLA (Sadece Unscheduled ise sadeleştir)
                    String displayText = item;
                    if (course != null && getCourseStatusText(course.getId()).contains("UNSCHEDULED")) {
                        displayText = "UNSCHEDULED"; // Detayı gizle, sadece başlık
                        // Detay için Tooltip ekle
                        Tooltip tip = new Tooltip(item); // Orijinal uzun mesaj tooltip'te
                        tip.setMaxWidth(400);
                        tip.setWrapText(true);
                        setTooltip(tip);

                        // Kırmızı Renk
                        String color = isDarkMode ? "#FF6B6B" : "#D32F2F";
                        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-alignment: CENTER-LEFT;");
                    } else {
                        // Normal Durum
                        setTooltip(null);
                        setStyle(
                                "-fx-text-fill: " + (isDarkMode ? "white" : "black") + "; -fx-alignment: CENTER-LEFT;");
                    }

                    // Duration ve Students kolonları için ORTALAMA
                    if (getTableColumn().getText().equals("Duration (min)") ||
                            getTableColumn().getText().equals("Students")) {
                        setStyle(getStyle() + "-fx-alignment: CENTER;");
                    }

                    setText(displayText);
                }
            }
        };

        // 1) Course Code
        TableColumn<Course, String> colCode = new TableColumn<>("Course Code");
        colCode.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getId()));
        colCode.setCellFactory(coloredCellFactory);

        // 2) Duration (Ortalı)
        TableColumn<Course, String> colDur = new TableColumn<>("Duration (min)");
        colDur.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));
        colDur.setCellFactory(coloredCellFactory);
        colDur.setMaxWidth(1000); // Genişlemesini sınırla ki diğerlerine yer kalsın

        // 3) Date
        TableColumn<Course, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(getCourseDate(cell.getValue().getId())));
        colDate.setCellFactory(coloredCellFactory);

        // 4) Time
        TableColumn<Course, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell -> new SimpleStringProperty(getCourseTimeRange(cell.getValue().getId())));
        colTime.setCellFactory(coloredCellFactory);

        // 5) Rooms
        TableColumn<Course, String> colRooms = new TableColumn<>("Rooms");
        colRooms.setCellValueFactory(cell -> new SimpleStringProperty(getCourseRooms(cell.getValue().getId())));
        colRooms.setCellFactory(coloredCellFactory);

        // 6) #Students (Ortalı)
        TableColumn<Course, String> colCount = new TableColumn<>("Students");
        colCount.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(getCourseStudentCount(cell.getValue().getId()))));
        colCount.setCellFactory(coloredCellFactory);
        colCount.setMaxWidth(1000);

        // 7) Status
        TableColumn<Course, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(getCourseStatusText(cell.getValue().getId())));
        colStatus.setCellFactory(coloredCellFactory);

        // Kolonları Ekle
        table.getColumns().setAll(colCode, colDur, colDate, colTime, colRooms, colCount, colStatus);

        // Veriyi Sırala ve Ekle
        javafx.collections.transformation.SortedList<Course> sortedExams = new javafx.collections.transformation.SortedList<>(
                examObservableList);
        sortedExams.setComparator((c1, c2) -> naturalCompare(c1.getId(), c2.getId()));
        table.setItems(sortedExams);

        // Tıklama Olayı
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showCourseStudentList(newVal);
            }
        });

        root.setCenter(table);
    }

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
            if (files != null) {
                dialog.close(); // Önce pencereyi kapat
                processAndLoadFiles(files); // Sonra yüklemeye başla
            }
        });

        dropZone.getChildren().addAll(lblInstruction, new Label("- or -"), btnBrowse);

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
                // Dosya listesi final olmalı veya kopya kullanılmalı
                List<File> finalFiles = files;
                // UI'ın nefes alması için minik bir gecikme iyidir
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

        // --- Aktif görünümü yenile (Kaldığı yeri hatırla) ---
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
                Map<String, List<StudentExam>> schedule = DBManager.loadSchedule();

                writer.write("Student ID,Course ID,Date,Time,Room,Seat");
                writer.newLine();

                for (Map.Entry<String, List<StudentExam>> entry : schedule.entrySet()) {
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
                Map<String, List<StudentExam>> schedule = DBManager.loadSchedule();

                writer.write("Date,Time,Room,Course,Student Count");
                writer.newLine();

                Map<String, DayRow> map = new LinkedHashMap<>();

                for (List<StudentExam> list : schedule.values()) {
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

    // =============================================================
    // ADVANCED EXAM CUSTOMIZATION (Multi-Group Rule Editor)
    // =============================================================

    private void showCustomizationDialog(Stage owner) {
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
        VBox groupsContainer = new VBox(10); // Gruplar alt alta dizilecek
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
        btnAddGroup.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold;"); // Yeşil

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

            int updatedCount = 0;

            // 2. Her bir grubu gez ve kuralları uygula
            for (RuleGroupPane pane : ruleGroups) {
                updatedCount += pane.applyRulesToSelectedCourses();
            }

            dialog.close();

            // 4. Takvimi yeniden oluştur
            runSchedulerLogic();
        });

        // Başlangıçta bir tane boş grup ekleyelim ki ekran boş durmasın
        if (ruleGroups.isEmpty()) {
            RuleGroupPane initialPane = new RuleGroupPane(groupsContainer);
            groupsContainer.getChildren().add(initialPane);
            ruleGroups.add(initialPane);
        } else {
            // Eski grupları yeni pencereye taşı ve parent güncelle
            for (RuleGroupPane pane : ruleGroups) {
                pane.setParentContainer(groupsContainer);
                groupsContainer.getChildren().add(pane);
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
    }

    public static void main(String[] args) {
        launch(args);
    }
}