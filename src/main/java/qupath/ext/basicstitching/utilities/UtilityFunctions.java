// =======================================================================================
// 3. UtilityFunctions.java
// =======================================================================================
package qupath.ext.basicstitching.utilities;

import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.writers.ome.OMEPyramidWriter;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class containing utility functions used throughout the application.
 */
public class UtilityFunctions {

    private static final Logger logger = LoggerFactory.getLogger(UtilityFunctions.class);

    /**
     * Gets all available compression types from OMEPyramidWriter.CompressionType enum.
     *
     * @return List of compression type names as strings
     */
    public static List<String> getCompressionTypeList() {
        Class<?> compressionTypeClass = OMEPyramidWriter.CompressionType.class;

        // Get all declared fields in the class
        Field[] fields = compressionTypeClass.getDeclaredFields();

        // Filter only public static final fields and extract names
        return Arrays.stream(fields)
                .filter(field -> Modifier.isPublic(field.getModifiers()) &&
                        Modifier.isStatic(field.getModifiers()) &&
                        Modifier.isFinal(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    /**
     * Gets the compression type for OMEPyramidWriter based on the selected option.
     *
     * @param selectedOption The selected compression option as a string.
     * @return The corresponding OMEPyramidWriter.CompressionType.
     * @throws IllegalArgumentException if the selected option does not match any compression type.
     */
    public static OMEPyramidWriter.CompressionType getCompressionType(String selectedOption)
            throws IllegalArgumentException {
        try {
            // Convert the string to an enum constant
            return OMEPyramidWriter.CompressionType.valueOf(selectedOption);
        } catch (IllegalArgumentException e) {
            // Throw an exception if no matching compression type is found
            throw new IllegalArgumentException("Invalid compression type: " + selectedOption, e);
        }
    }

    /**
     * Generates a unique file path by appending a number to the file name if a file with the
     * same name already exists. The first instance of the file will have no number appended,
     * while the second will have _2, the third _3, and so on.
     *
     * @param originalPath The original file path.
     * @return A unique file path.
     */
    public static String getUniqueFilePath(String originalPath) {
        Path path = Paths.get(originalPath);
        String fileName = path.getFileName().toString();
        String baseName = fileName.replaceAll("\\.ome\\.tif$", "");
        Path parentDir = path.getParent();

        // Start with the original base name
        Path newPath = parentDir.resolve(baseName + ".ome.tif");
        int counter = 2;

        // If the file exists, start appending numbers
        while (Files.exists(newPath)) {
            newPath = parentDir.resolve(baseName + "_" + counter + ".ome.tif");
            counter++;
        }

        return newPath.toString();
    }

    /**
     * Retrieves the dimensions (width and height) of a TIFF image file.
     *
     * @param filePath The file path of the TIFF image.
     * @return A map containing the 'width' and 'height' of the image, or null if an error occurs.
     */
    public static Map<String, Integer> getTiffDimensions(File filePath) {
        // Check if the file exists
        if (!filePath.exists()) {
            logger.info("File not found: {}", filePath);
            return null;
        }

        try {
            // Read the image file
            BufferedImage image = ImageIO.read(filePath);
            if (image == null) {
                logger.info("ImageIO returned null for file: {}", filePath);
                return null;
            }

            // Return the image dimensions as a map
            Map<String, Integer> dimensions = new HashMap<>();
            dimensions.put("width", image.getWidth());
            dimensions.put("height", image.getHeight());
            return dimensions;

        } catch (IOException e) {
            // Log and handle the error
            logger.info("Error reading the image file {}: {}", filePath, e.getMessage());
            return null;
        }
    }
}