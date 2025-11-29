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
        try {
            // Attempt to create the database file and all required tables (DDL).
            DBManager.initializeDatabase();
            System.out.println("Database initialized and tables are ready.");
        } catch (Exception e) {
            // CRITICAL FAILURE: If the database cannot be initialized (e.g., driver missing, permission error),
            // the application cannot function, so we must stop it.
            System.err.println("WARNING: Database failed to initialize. Application is shutting down.");
            e.printStackTrace();
            Platform.exit();
            return; // Exit the start method
        }
        
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
                else if (name.contains("allcourses")|| name.contains("courses")) {
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
    // UI'daki tarih/saat alanlarından sınav günü/saat pencereleri üret
    private List<DayWindow> buildDayWindowsFromFilters() {
        LocalDate from = getFilterStartDate();
        LocalDate to   = getFilterEndDate();

        // Hiç tarih seçilmediyse scheduler çalışmasın
        if (from == null || to == null) {
            return Collections.emptyList();
        }

        // Ters girildiyse düzelt (kullanıcı yanlışlıkla ileri/geri girmiş olabilir)
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        LocalTime fromTime = getFilterStartTime();
        LocalTime toTime   = getFilterEndTime();

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
    // =============================================================
    // SCHEDULER LOGIC (Integration Point)
    // =============================================================
    // =============================================================
// SCHEDULER LOGIC (Integration Point)
// =============================================================
    private void runSchedulerLogic() {
        System.out.println("UI: Calling backend scheduler...");

        // 0) UI tarih/saatinden DayWindow listesi üret
        List<DayWindow> dayWindows = buildDayWindowsFromFilters();
        if (dayWindows.isEmpty()) {
            System.out.println("No date range selected, skipping scheduling.");
            return;
        }

        // 1. Backend sınıfı oluştur
        ExamScheduler scheduler = new ExamScheduler();

        // 2. Algoritmayı çalıştır (artık dayWindows parametresi de veriliyor)
        studentScheduleMap = scheduler.run(
                allStudents,
                allCourses,
                allEnrollments,
                allClassrooms,
                dayWindows
        );

        // 3. UI istatistiklerini güncelle
        Platform.runLater(() -> {
            int totalScheduledExams = studentScheduleMap.values().stream().mapToInt(List::size).sum();
            lblStats.setText(String.format("Scheduled: %d total exam entries | %d students assigned",
                    totalScheduledExams, studentScheduleMap.size()));

            if (tglStudents.isSelected())
                showStudentList();
        });
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
        if (field == null) return null;
        String text = field.getText();
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;

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
     * - Saat aralığı:  sınav aralığı seçilen saat aralığı ile ÖRTÜŞÜYOR mu?
     */
    private boolean timeslotMatchesFilters(Timeslot ts) {
        if (ts == null) return false;

        LocalDate fromDate = getFilterStartDate();
        LocalDate toDate   = getFilterEndDate();
        LocalTime fromTime = getFilterStartTime();
        LocalTime toTime   = getFilterEndTime();

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
        LocalTime slotEnd   = ts.getEnd();

        // Filtrede sadece başlangıç varsa: sınav bu saatten önce tamamen bitmişse eleriz
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

    /**
     * Bir öğrencinin sınav listesini mevcut filtrelere göre süzer.
     */
    private List<StudentExam> filterExamsByCurrentFilters(List<StudentExam> exams) {
        if (exams == null || exams.isEmpty()) return Collections.emptyList();
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
    // Belirli bir dersin ilk atanmış sınavından tarihi al (filtreye göre)
    private String getCourseDate(String courseId) {
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId)) continue;
                if (!timeslotMatchesFilters(se.getTimeslot())) continue;
                return se.getTimeslot().getDate().toString();
            }
        }
        return "UNSCHEDULED";
    }

    // Belirli bir dersin ilk atanmış sınavından saat aralığını al
    // Belirli bir dersin ilk atanmış sınavından saat aralığını al (filtreye göre)
    private String getCourseTimeRange(String courseId) {
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId)) continue;
                if (!timeslotMatchesFilters(se.getTimeslot())) continue;
                return se.getTimeslot().getStart().toString() + " - "
                        + se.getTimeslot().getEnd().toString();
            }
        }
        return "-";
    }

    // Belirli bir ders için kullanılan tüm sınıfları topla
    // Belirli bir ders için kullanılan tüm sınıfları topla (filtreye göre)
    private String getCourseRooms(String courseId) {
        java.util.Set<String> rooms = new java.util.LinkedHashSet<>();
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId)) continue;
                if (!timeslotMatchesFilters(se.getTimeslot())) continue;
                rooms.add(se.getClassroomId());
            }
        }
        if (rooms.isEmpty()) {
            return "-";
        }
        return String.join(", ", rooms);
    }

    // Belirli bir ders için toplam kaç öğrenci atanmış?
    // Belirli bir ders için toplam kaç öğrenci atanmış? (filtreye göre)
    private int getCourseStudentCount(String courseId) {
        int count = 0;
        for (List<StudentExam> exams : studentScheduleMap.values()) {
            for (StudentExam se : exams) {
                if (!se.getCourseId().equals(courseId)) continue;
                if (!timeslotMatchesFilters(se.getTimeslot())) continue;
                count++;
            }
        }
        return count;
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

        // Student ID kolonı
        TableColumn<Student, String> colId = new TableColumn<>("Student ID");
        colId.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getId()));

        // YENİ: Öğrencinin kaç sınavı var? (#Exams)
        TableColumn<Student, String> colExamCount = new TableColumn<>("#Exams");
        colExamCount.setCellValueFactory(cell -> {
            String sid = cell.getValue().getId();
            List<StudentExam> exams =
                    studentScheduleMap.getOrDefault(sid, Collections.emptyList());
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
                cell -> new SimpleStringProperty(cell.getValue().getTimeslot().getStart().toString()));

        TableColumn<StudentExam, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(new PropertyValueFactory<>("classroomId"));

        TableColumn<StudentExam, String> colSeat = new TableColumn<>("Seat");
        colSeat.setCellValueFactory(new PropertyValueFactory<>("seatNo"));

        detailTable.getColumns().addAll(colCourse, colDate, colTime, colRoom, colSeat);
        detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Get data from Results Map
        // Get data from Results Map (CURRENT FILTERS APPLIED)
        List<StudentExam> exams =
                studentScheduleMap.getOrDefault(student.getId(), Collections.emptyList());
        exams = filterExamsByCurrentFilters(exams);
        detailTable.setItems(FXCollections.observableArrayList(exams));

        detailView.getChildren().addAll(header, new Separator(), detailTable);
        root.setCenter(detailView);
    }

    private void showExamList() {
        TableView<Course> table = new TableView<>();
        table.setPlaceholder(new Label("No courses loaded or no schedule generated."));
        styleTableView(table);

        // 1) Course Code
        TableColumn<Course, String> colCode = new TableColumn<>("Course Code");
        colCode.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getId()));

        // 2) Duration
        TableColumn<Course, String> colDur = new TableColumn<>("Duration (min)");
        colDur.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getDurationMinutes())));

        // 3) Date (algoritmanın atadığı gün, yoksa UNSCHEDULED)
        TableColumn<Course, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(cell ->
                new SimpleStringProperty(getCourseDate(cell.getValue().getId())));

        // 4) Time (başlangıç-bitiş saati, yoksa "-")
        TableColumn<Course, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell ->
                new SimpleStringProperty(getCourseTimeRange(cell.getValue().getId())));

        // 5) Rooms (bu sınavın kullanıldığı sınıflar, virgülle ayrılmış)
        TableColumn<Course, String> colRooms = new TableColumn<>("Rooms");
        colRooms.setCellValueFactory(cell ->
                new SimpleStringProperty(getCourseRooms(cell.getValue().getId())));

        // 6) #Students (bu sınava atanmış toplam öğrenci sayısı)
        TableColumn<Course, String> colCount = new TableColumn<>("#Students");
        colCount.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(getCourseStudentCount(cell.getValue().getId()))));

        table.getColumns().setAll(colCode, colDur, colDate, colTime, colRooms, colCount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(examObservableList);

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
                if (ts == null) continue;
                // Tarih / saat filtrelerini uygula
                if (!timeslotMatchesFilters(ts)) continue;

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
        colDate.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDate()));

        TableColumn<DayRow, String> colTime = new TableColumn<>("Time");
        colTime.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTime()));

        TableColumn<DayRow, String> colRoom = new TableColumn<>("Room");
        colRoom.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRoom()));

        TableColumn<DayRow, String> colCourse = new TableColumn<>("Course");
        colCourse.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCourseId()));

        TableColumn<DayRow, String> colCount = new TableColumn<>("#Students");
        colCount.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getStudentCount())));

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
        cmbType.getSelectionModel().selectFirst(); // Varsayılan seçili gelsin

        Label lblName = new Label("File Name (without extension)");
        lblName.setTextFill(Color.web(text));
        TextField txtName = new TextField("export_data");

        Button btnDoExport = new Button("Export CSV");
        btnDoExport.setStyle("-fx-background-color: " + ACCENT_COLOR + "; -fx-text-fill: white;");

        btnDoExport.setOnAction(e -> {
            String type = cmbType.getValue();
            String filename = txtName.getText().trim();

            if (filename.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter a file name.");
                styleDialog(alert);
                alert.show();
                return;
            }

            // Export işlemini başlat
            boolean success = exportData(type, filename);

            if (success) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Export Successful!\nSaved as: " + filename + ".csv");
                styleDialog(alert);
                alert.show();
                dialog.close();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Export Failed. Check console for errors.");
                styleDialog(alert);
                alert.show();
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



    private boolean exportData(String type, String filename) {

        File file = new File(filename + ".csv");

        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(file))) {

            if (type.equals("Student List")) {
                writer.write("Student ID,Total Exams");
                writer.newLine();
                for (Student s : allStudents) {
                    List<StudentExam> exams = studentScheduleMap.getOrDefault(s.getId(), Collections.emptyList());
                    writer.write(s.getId() + "," + exams.size());
                    writer.newLine();
                }
            }
            else if (type.equals("Exam Schedule (Detailed)")) {

                writer.write("Student ID,Course ID,Date,Time,Room,Seat");
                writer.newLine();

                for (Map.Entry<String, List<StudentExam>> entry : studentScheduleMap.entrySet()) {
                    String sid = entry.getKey();
                    for (StudentExam exam : entry.getValue()) {
                        String date = (exam.getTimeslot() != null) ? exam.getTimeslot().getDate().toString() : "N/A";
                        String time = (exam.getTimeslot() != null) ? exam.getTimeslot().getStart().toString() : "N/A";

                        String line = String.format("%s,%s,%s,%s,%s,%d",
                                sid,
                                exam.getCourseId(),
                                date,
                                time,
                                exam.getClassroomId(),
                                exam.getSeatNo()
                        );
                        writer.write(line);
                        writer.newLine();
                    }
                }
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

        public String getDate() { return date; }
        public String getTime() { return time; }
        public String getRoom() { return room; }
        public String getCourseId() { return courseId; }
        public int getStudentCount() { return studentCount; }

        public void increment() {
            this.studentCount++;
        }
    }
    // ==== DAY VIEW MODEL SONU ====


    public static void main(String[] args) {
        launch(args);
    }
}