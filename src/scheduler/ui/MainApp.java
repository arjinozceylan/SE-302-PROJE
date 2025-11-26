package scheduler.ui;   

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

public class MainApp extends Application {

    // List visible on the main screen
    private ListView<String> uploadedFilesList;

    @Override
    public void start(Stage primaryStage) {
        // --- 1. MAIN LAYOUT (BORDERPANE) ---
        BorderPane root = new BorderPane();

        // --- 2. HEADER / TOOLBAR ---
        HBox topMenu = new HBox(15);
        topMenu.setPadding(new Insets(10));
        topMenu.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        topMenu.setAlignment(Pos.CENTER_LEFT);

        Button btnHelp = new Button("?");
        Label lblErrorCount = new Label("Errors: 0");
        lblErrorCount.setTextFill(Color.WHITE);
        lblErrorCount.setStyle("-fx-background-color: red; -fx-padding: 3 8 3 8; -fx-background-radius: 10;");

        // (2) Import Button
        Button btnImport = new Button("Import \u2193");
        btnImport.setOnAction(e -> showImportDialog(primaryStage));

        // (3) Export Button (NEW FEATURE)
        Button btnExport = new Button("Export \u2191");
        btnExport.setOnAction(e -> showExportDialog(primaryStage));

        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Search...");
        txtSearch.setPrefWidth(200);

        HBox filters = new HBox(5);
        ToggleButton filter1 = new ToggleButton("Filter 1");
        ToggleButton filter2 = new ToggleButton("Filter 2");
        ToggleButton filter3 = new ToggleButton("Filter 3");
        ToggleGroup group = new ToggleGroup();
        filter1.setToggleGroup(group);
        filter2.setToggleGroup(group);
        filter3.setToggleGroup(group);
        filters.getChildren().addAll(filter1, filter2, filter3);

        topMenu.getChildren().addAll(btnHelp, lblErrorCount, btnImport, btnExport, txtSearch, filters);


        // --- 3. LEFT SIDEBAR / FILTERS ---
        VBox leftPane = new VBox(15);
        leftPane.setPadding(new Insets(10));
        leftPane.setPrefWidth(250);
        leftPane.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;");

        Label lblSectionTitle = new Label("Filter Options");
        lblSectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        // Date Range
        VBox dateBox = new VBox(5);
        dateBox.getChildren().add(new Label("Date Range:"));
        DatePicker startDate = new DatePicker();
        startDate.setPromptText("Start Date");
        startDate.setMaxWidth(Double.MAX_VALUE);
        DatePicker endDate = new DatePicker();
        endDate.setPromptText("End Date");
        endDate.setMaxWidth(Double.MAX_VALUE);
        dateBox.getChildren().addAll(startDate, endDate);

        // Block Range
        VBox blockBox = new VBox(5);
        blockBox.getChildren().add(new Label("Block Range:"));
        HBox blockInputs = new HBox(5);
        TextField txtBlockStart = new TextField(); txtBlockStart.setPromptText("Min");
        TextField txtBlockEnd = new TextField(); txtBlockEnd.setPromptText("Max");
        blockInputs.getChildren().addAll(txtBlockStart, txtBlockEnd);
        blockBox.getChildren().add(blockInputs);

        // Time Range
        VBox timeBox = new VBox(5);
        timeBox.getChildren().add(new Label("Time Range:"));
        HBox timeInputs = new HBox(5);
        TextField txtTimeStart = new TextField(); txtTimeStart.setPromptText("09:00");
        TextField txtTimeEnd = new TextField(); txtTimeEnd.setPromptText("17:00");
        timeInputs.getChildren().addAll(txtTimeStart, txtTimeEnd);
        timeBox.getChildren().add(timeInputs);

        // Uploaded Files List
        Separator sepFiles = new Separator();
        Label lblUploaded = new Label("Uploaded Files:");
        lblUploaded.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        uploadedFilesList = new ListView<>();
        uploadedFilesList.setPrefHeight(200);
        uploadedFilesList.setPlaceholder(new Label("No files loaded"));

        leftPane.getChildren().addAll(
                lblSectionTitle, new Separator(),
                dateBox, new Separator(),
                blockBox, new Separator(),
                timeBox, 
                sepFiles, lblUploaded, uploadedFilesList
        );


        // --- 4. CENTER (TABLE) ---
        TableView<String> mainTable = new TableView<>();
        mainTable.setPlaceholder(new Label("No Data. Search or import files."));
        TableColumn<String, String> col1 = new TableColumn<>("File Name");
        TableColumn<String, String> col2 = new TableColumn<>("Date");
        TableColumn<String, String> col3 = new TableColumn<>("Status");
        mainTable.getColumns().addAll(col1, col2, col3);
        mainTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


        // --- 5. BOTTOM (STATUS BAR) ---
        HBox bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(5, 10, 5, 10));
        bottomBar.setStyle("-fx-background-color: #dcdcdc; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        Label lblStats = new Label("Total Exams: 0 | Total Students: 0 | Total Classes: 0");
        lblStats.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        bottomBar.getChildren().add(lblStats);

        root.setTop(topMenu);
        root.setLeft(leftPane);
        root.setCenter(mainTable);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setTitle("Application Main Screen");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- IMPORT DIALOG (UPDATED WITH FILE CHOOSER) ---
    private void showImportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Import Files");

        VBox dropZone = new VBox(20);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPadding(new Insets(30));
        dropZone.setStyle("-fx-border-color: #666; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #f9f9f9;");

        Label lblInstruction = new Label("Drag and drop files here");
        lblInstruction.setFont(Font.font("Arial", 16));
        Label lblOr = new Label("- or -");
        
        // UPDATED: Functional Browse Button
        Button btnBrowse = new Button("Browse Files");
        btnBrowse.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Files");
            List<File> files = fileChooser.showOpenMultipleDialog(dialog);
            if (files != null) {
                for (File file : files) {
                    uploadedFilesList.getItems().add(file.getName());
                }
                lblInstruction.setText(files.size() + " files added via Browse!");
            }
        });

        dropZone.getChildren().addAll(lblInstruction, lblOr, btnBrowse);

        // Drag and Drop Logic
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                dropZone.setStyle("-fx-border-color: #0078d7; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #e6f7ff;");
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.setStyle("-fx-border-color: #666; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #f9f9f9;");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    uploadedFilesList.getItems().add(file.getName());
                }
                success = true;
                lblInstruction.setText(db.getFiles().size() + " files added via Drag&Drop!");
            }
            event.setDropCompleted(success);
            event.consume();
        });

        Scene dialogScene = new Scene(dropZone, 400, 300);
        dialog.setScene(dialogScene);
        dialog.show();
    }

    // --- NEW: EXPORT DIALOG (BASED ON SKETCH) ---
    private void showExportDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Export");

        VBox layout = new VBox(20);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        // 1. File Type (Top long dropdown from sketch)
        VBox typeBox = new VBox(5);
        Label lblType = new Label("File Type / Source"); 
        ComboBox<String> cmbType = new ComboBox<>();
        cmbType.getItems().addAll("Student List", "Exam Results", "Class Schedule", "Full Report");
        cmbType.setValue("Student List");
        cmbType.setMaxWidth(Double.MAX_VALUE); // Full width
        typeBox.getChildren().addAll(lblType, cmbType);

        // 2. Row for File Name and Format
        HBox nameFormatRow = new HBox(15);
        
        // File Name Section
        VBox nameBox = new VBox(5);
        Label lblName = new Label("File Name"); 
        TextField txtName = new TextField();
        txtName.setPromptText("Enter filename");
        txtName.setPrefWidth(200);
        nameBox.getChildren().addAll(lblName, txtName);
        
        // File Format Section (Small dropdown from sketch)
        VBox formatBox = new VBox(5);
        Label lblFormat = new Label("Format"); 
        ComboBox<String> cmbFormat = new ComboBox<>();
        cmbFormat.getItems().addAll(".csv", ".pdf", ".xlsx", ".json");
        cmbFormat.setValue(".csv");
        cmbFormat.setPrefWidth(80);
        formatBox.getChildren().addAll(lblFormat, cmbFormat);

        nameFormatRow.getChildren().addAll(nameBox, formatBox);

        // 3. Action Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button btnCancel = new Button("Cancel");
        btnCancel.setOnAction(e -> dialog.close());
        
        Button btnExportAction = new Button("Export");
        btnExportAction.setStyle("-fx-background-color: #0078d7; -fx-text-fill: white; -fx-font-weight: bold;");
        btnExportAction.setOnAction(e -> {
            System.out.println("Exporting: " + txtName.getText() + cmbFormat.getValue() + " as " + cmbType.getValue());
            dialog.close();
        });

        buttons.getChildren().addAll(btnCancel, btnExportAction);

        layout.getChildren().addAll(typeBox, nameFormatRow, new Separator(), buttons);

        Scene dialogScene = new Scene(layout, 400, 250);
        dialog.setScene(dialogScene);
        dialog.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}