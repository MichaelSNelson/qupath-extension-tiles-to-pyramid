// =======================================================================================
// 3. UtilityFunctions.java
// =======================================================================================
package qupath.ext.basicstitching.utilities;

import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.writers.ome.OMEPyramidWriter;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

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


    /**
     * Parse a Vectra TIFF for spatial coordinates and image dimensions using baseline TIFF tags.
     *
     * @param file TIFF file to extract from
     * @return VectraRegionInfo object with pixel coordinates and image size, or null on failure
     */
    public static VectraRegionInfo getVectraPositionAndDimensions(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ImageInputStream iis = ImageIO.createImageInputStream(fis)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("TIFF");
            if (!readers.hasNext()) {
                logger.warn("No TIFF readers available for file: {}", file.getName());
                return null;
            }
            ImageReader reader = readers.next();
            reader.setInput(iis);
            IIOMetadata metadata = reader.getImageMetadata(0);

            // Parse as TIFFDirectory
            TIFFDirectory tiffDir = TIFFDirectory.createFromMetadata(metadata);
            logger.debug("TIFFDirectory parsed for file: {}", file.getName());

            // Extract rational values for resolution and position
            double xRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_RESOLUTION);
            double yRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_RESOLUTION);
            double xPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_POSITION);
            double yPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_POSITION);

            // Extract width and height (pixels)
            int width = (int) tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_WIDTH).getAsLong(0);
            int height = (int) tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_LENGTH).getAsLong(0);

            reader.dispose();

            // Calculate position in pixels (rounded to nearest integer)
            int xPx = (int) Math.round(xRes * xPos);
            int yPx = (int) Math.round(yRes * yPos);

            logger.info("Extracted Vectra region from {}: x={}, y={}, width={}, height={}",
                    file.getName(), xPx, yPx, width, height);
            return new VectraRegionInfo(xPx, yPx, width, height);
        } catch (Exception e) {
            logger.error("Failed to extract Vectra region info from {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Helper function to extract a rational number from a TIFFDirectory.
     * Returns 0.0 if tag is missing or malformed.
     *
     * @param tiffDir TIFFDirectory object
     * @param tag TIFF tag ID (see BaselineTIFFTagSet constants)
     * @return tag value as double (numerator / denominator)
     */
    public static double getRational(TIFFDirectory tiffDir, int tag) {
        try {
            long[] rational = tiffDir.getTIFFField(tag).getAsRational(0);
            double result = rational[0] / (double) rational[1];
            logger.debug("Extracted rational tag {}: {}/{} = {}", tag, rational[0], rational[1], result);
            return result;
        } catch (Exception e) {
            logger.warn("Could not extract rational value for tag {}", tag, e);
            return 0.0;
        }
    }

    /**
     * Holds both position and dimensions.
     * Coordinates are in pixels, width/height are in pixels.
     */
    public static class VectraRegionInfo {
        public final int xPx, yPx, width, height;
        public VectraRegionInfo(int xPx, int yPx, int width, int height) {
            this.xPx = xPx;
            this.yPx = yPx;
            this.width = width;
            this.height = height;
        }
    }

}