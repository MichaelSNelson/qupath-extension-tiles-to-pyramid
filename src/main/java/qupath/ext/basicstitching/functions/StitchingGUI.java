// =======================================================================================
// 4. StitchingGUI.java
// =======================================================================================
package qupath.ext.basicstitching.functions;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.stitching.StitchingImplementations;
import qupath.ext.basicstitching.utilities.AutoFillPersistentPreferences;
import qupath.lib.gui.scripting.QPEx;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static qupath.ext.basicstitching.utilities.UtilityFunctions.getCompressionTypeList;

/**
 * GUI class for the Basic Stitching Extension.
 * Provides a dialog interface for configuring stitching parameters and executing stitching operations.
 *
 * TODO: Progress bar for stitching
 * TODO: Estimate size of stitched image to predict necessary memory
 * TODO: Warn user if size exceeds QuPath's allowed limits.
 */
public class StitchingGUI {

    private static final Logger logger = LoggerFactory.getLogger(StitchingGUI.class);

    // GUI Components - static fields for persistence across dialog instances
    static TextField folderField = new TextField(AutoFillPersistentPreferences.getFolderLocationSaved());
    static ComboBox<String> compressionBox = new ComboBox<>();
    static TextField pixelSizeField = new TextField(AutoFillPersistentPreferences.getImagePixelSizeInMicronsSaved());
    static TextField downsampleField = new TextField(AutoFillPersistentPreferences.getDownsampleSaved());
    static TextField matchStringField = new TextField(AutoFillPersistentPreferences.getSearchStringSaved());
    static ComboBox<String> stitchingGridBox = new ComboBox<>();
    static Button folderButton = new Button("Select Folder");

    // Labels
    static Label stitchingGridLabel = new Label("Stitching Method:");
    static Label folderLabel = new Label("Folder location:");
    static Label compressionLabel = new Label("Compression type:");
    static Label pixelSizeLabel = new Label("Pixel size, microns:");
    static Label downsampleLabel = new Label("Downsample:");
    static Label matchStringLabel = new Label("Stitch sub-folders with text string:");
    static Hyperlink githubLink = new Hyperlink("GitHub ReadMe");

    // Map to hold the positions of each GUI element
    private static Map<Node, Integer> guiElementPositions = new HashMap<>();

    /**
     * Creates and displays the main GUI dialog for stitching configuration.
     * Handles user input validation, preference saving, and initiates stitching process.
     */
    public static void createGUI() {
        // Create the dialog
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Input Stitching Method and Options");
        dlg.setHeaderText("Enter your settings below:");

        // Set the content
        dlg.getDialogPane().setContent(createContent());

        // Add Okay and Cancel buttons
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Show the dialog and capture the response
        Optional<ButtonType> result = dlg.showAndWait();

        // Handling the response
        if (result.isPresent() && result.get() == ButtonType.OK) {
            processDialogResult();
        }
    }

    /**
     * Processes the dialog result when OK is clicked.
     * Validates input, saves preferences, and initiates stitching.
     */
    private static void processDialogResult() {
        try {
            // Read values from dialog and save to persistent preferences
            String folderPath = folderField.getText();
            AutoFillPersistentPreferences.setFolderLocationSaved(folderPath);

            String compressionType = compressionBox.getValue();
            AutoFillPersistentPreferences.setCompressionTypeSaved(compressionType);

            // Parse numeric fields with validation
            double pixelSize = parseDoubleField(pixelSizeField.getText(), 0.0);
            AutoFillPersistentPreferences.setImagePixelSizeInMicronsSaved(String.valueOf(pixelSize));

            double downsample = parseDoubleField(downsampleField.getText(), 1.0);
            AutoFillPersistentPreferences.setDownsampleSaved(String.valueOf(downsample));

            String matchingString = matchStringField.getText();
            AutoFillPersistentPreferences.setSearchStringSaved(matchingString);

            String stitchingType = stitchingGridBox.getValue();
            AutoFillPersistentPreferences.setStitchingMethodSaved(stitchingType);

            // Call the stitching function with collected data
            String finalImageName = StitchingImplementations.stitchCore(
                    stitchingType, folderPath, folderPath, compressionType,
                    pixelSize, downsample, matchingString
            );

        } catch (Exception e) {
            logger.error("Error processing dialog result", e);
            showAlertDialog("Error processing input: " + e.getMessage());
        }
    }

    /**
     * Safely parses a string to double with a default fallback value.
     */
    private static double parseDoubleField(String text, double defaultValue) {
        if (text == null || text.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid number format: {}, using default: {}", text, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Creates and returns a GridPane containing all the components for the GUI.
     * This method initializes the positions of each component in the grid,
     * adds the components to the grid, and sets their initial visibility.
     *
     * @return A GridPane containing all the configured components.
     */
    private static GridPane createContent() {
        // Create a new GridPane for layout
        GridPane pane = new GridPane();

        // Set horizontal and vertical gaps between grid cells
        pane.setHgap(10);
        pane.setVgap(10);

        // Initialize the positions of each component in the grid
        initializePositions();

        // Add various components to the grid pane
        addStitchingGridComponents(pane);
        addFolderSelectionComponents(pane);
        addMatchStringComponents(pane);
        addCompressionComponents(pane);
        addPixelSizeComponents(pane);
        addDownsampleComponents(pane);
        addGitHubLinkComponent(pane);

        // Update the components' visibility based on the current selection
        updateComponentsBasedOnSelection(pane);

        return pane;
    }

    /**
     * Adds a label and its associated control to the specified GridPane.
     */
    private static void addToGrid(GridPane pane, Node label, Node control) {
        Integer rowIndex = guiElementPositions.get(label);
        if (rowIndex != null) {
            pane.add(label, 0, rowIndex);
            pane.add(control, 1, rowIndex);
        } else {
            logger.error("Row index not found for component: {}", label);
        }
    }

    /**
     * Adds a GitHub repository hyperlink to the GridPane.
     */
    private static void addGitHubLinkComponent(GridPane pane) {
        githubLink.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/MichaelSNelson/BasicStitching"));
            } catch (Exception ex) {
                logger.error("Error opening link", ex);
            }
        });

        Integer rowIndex = guiElementPositions.get(githubLink);
        if (rowIndex != null) {
            pane.add(githubLink, 0, rowIndex, 2, 1);
        }
    }

    /**
     * Initializes the positions of GUI elements in the GridPane.
     */
    private static void initializePositions() {
        int currentPosition = 0;

        guiElementPositions.put(stitchingGridLabel, currentPosition++);
        guiElementPositions.put(folderLabel, currentPosition++);
        guiElementPositions.put(compressionLabel, currentPosition++);
        guiElementPositions.put(pixelSizeLabel, currentPosition++);
        guiElementPositions.put(downsampleLabel, currentPosition++);
        guiElementPositions.put(matchStringLabel, currentPosition++);
        guiElementPositions.put(githubLink, currentPosition++);
    }

    /**
     * Adds stitching grid components to the specified GridPane.
     */
    private static void addStitchingGridComponents(GridPane pane) {
        stitchingGridBox.getItems().clear();
        stitchingGridBox.getItems().addAll(
                "Vectra tiles with metadata",
                "Filename[x,y] with coordinates in microns",
                "Coordinates in TileConfiguration.txt file"
        );

        stitchingGridBox.setValue(AutoFillPersistentPreferences.getStitchingMethodSaved());
        stitchingGridBox.setOnAction(e -> updateComponentsBasedOnSelection(pane));

        addToGrid(pane, stitchingGridLabel, stitchingGridBox);
    }

    /**
     * Adds components for folder selection to the specified GridPane.
     */
    private static void addFolderSelectionComponents(GridPane pane) {
        // Attempt to initialize with default project folder
        try {
            String defaultFolderPath = QPEx.buildPathInProject("Tiles");
            logger.info("Default folder path: {}", defaultFolderPath);
            folderField.setText(defaultFolderPath);
        } catch (Exception e) {
            logger.info("Error setting default folder path, usually due to no project being open", e);
        }

        folderButton.setOnAction(e -> {
            try {
                DirectoryChooser dirChooser = new DirectoryChooser();
                dirChooser.setTitle("Select Folder");

                String initialDirPath = folderField.getText();
                File initialDir = new File(initialDirPath);

                if (initialDir.exists() && initialDir.isDirectory()) {
                    dirChooser.setInitialDirectory(initialDir);
                } else {
                    logger.warn("Initial directory does not exist or is not a directory: {}",
                            initialDir.getAbsolutePath());
                }

                File selectedDir = dirChooser.showDialog(null);
                if (selectedDir != null) {
                    folderField.setText(selectedDir.getAbsolutePath());
                    logger.info("Selected folder path: {}", selectedDir.getAbsolutePath());
                }
            } catch (Exception ex) {
                logger.error("Error selecting folder", ex);
            }
        });

        addToGrid(pane, folderLabel, folderField);

        Integer rowIndex = guiElementPositions.get(folderLabel);
        if (rowIndex != null) {
            pane.add(folderButton, 2, rowIndex);
        } else {
            logger.error("Row index not found for folderButton");
        }
    }

    /**
     * Adds compression selection components to the specified GridPane.
     */
    private static void addCompressionComponents(GridPane pane) {
        List<String> compressionTypes = getCompressionTypeList();
        compressionBox.getItems().clear();
        compressionBox.getItems().addAll(compressionTypes);

        compressionBox.setValue(AutoFillPersistentPreferences.getCompressionTypeSaved());

        Tooltip compressionTooltip = new Tooltip("Select the type of image compression.");
        compressionLabel.setTooltip(compressionTooltip);
        compressionBox.setTooltip(compressionTooltip);

        addToGrid(pane, compressionLabel, compressionBox);
    }

    /**
     * Adds pixel size input components to the specified GridPane.
     */
    private static void addPixelSizeComponents(GridPane pane) {
        addToGrid(pane, pixelSizeLabel, pixelSizeField);
    }

    /**
     * Adds downsample input components to the specified GridPane.
     */
    private static void addDownsampleComponents(GridPane pane) {
        Tooltip downsampleTooltip = new Tooltip(
                "The amount by which the highest resolution plane will be initially downsampled."
        );
        downsampleLabel.setTooltip(downsampleTooltip);
        downsampleField.setTooltip(downsampleTooltip);

        addToGrid(pane, downsampleLabel, downsampleField);
    }

    /**
     * Adds matching string input components to the specified GridPane.
     */
    private static void addMatchStringComponents(GridPane pane) {
        addToGrid(pane, matchStringLabel, matchStringField);
    }

    /**
     * Updates the visibility of certain GUI components based on the current selection
     * in the stitching method combo box.
     */
    private static void updateComponentsBasedOnSelection(GridPane pane) {
        String selectedValue = stitchingGridBox.getValue();
        boolean hidePixelSize = "Vectra multiplex tif".equals(selectedValue) ||
                "Coordinates in TileCoordinates.txt file".equals(selectedValue);

        pixelSizeLabel.setVisible(!hidePixelSize);
        pixelSizeField.setVisible(!hidePixelSize);

        adjustLayout(pane);
    }

    /**
     * Adjusts the layout of the GridPane based on the current positions.
     */
    private static void adjustLayout(GridPane pane) {
        for (Map.Entry<Node, Integer> entry : guiElementPositions.entrySet()) {
            Node node = entry.getKey();
            Integer newRow = entry.getValue();

            if (pane.getChildren().contains(node)) {
                GridPane.setRowIndex(node, newRow);
            }
        }
    }

    /**
     * Shows a warning alert dialog with the specified message.
     */
    public static void showAlertDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning!");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.showAndWait();
    }
}