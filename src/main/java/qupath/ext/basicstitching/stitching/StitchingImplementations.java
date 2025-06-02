package qupath.ext.basicstitching.stitching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.functions.StitchingGUI;
import qupath.ext.basicstitching.utilities.UtilityFunctions;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.SparseImageServer;
import qupath.lib.images.writers.ome.OMEPyramidWriter;
import qupath.lib.regions.ImageRegion;
import qupath.fx.dialogs.Dialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main class responsible for managing stitching strategies and executing the stitching process.
 * This class sets the appropriate stitching strategy based on the given type and coordinates the stitching process.
 *
 * @author Converted from Groovy
 */
public class StitchingImplementations {
    private static final Logger logger = LoggerFactory.getLogger(StitchingImplementations.class);
    private static StitchingStrategy strategy;

    /**
     * Interface for stitching strategies.
     * Defines the contract for different stitching algorithms.
     */
    public interface StitchingStrategy {
        /**
         * Prepares stitching by processing directories and generating file-region mappings.
         *
         * @param folderPath The path to the root directory containing image files
         * @param pixelSizeInMicrons The pixel size in microns for scaling calculations
         * @param baseDownsample The base downsample value for the stitching process
         * @param matchingString A string to match for selecting relevant subdirectories
         * @return A list of maps containing file, region, and subdirName information for stitching
         */
        List<Map<String, Object>> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                                   double baseDownsample, String matchingString);
    }

    /**
     * Strategy for stitching images based on file names with coordinate information.
     * This class implements the StitchingStrategy interface and provides a specific
     * algorithm for stitching based on the naming convention of image files containing [x,y] coordinates.
     */
    public static class FileNameStitchingStrategy implements StitchingStrategy {
        private static final Logger strategyLogger = LoggerFactory.getLogger(FileNameStitchingStrategy.class);

        /**
         * Prepares stitching by processing each subdirectory within the specified root directory.
         * This method iterates over each subdirectory that matches the given criteria and
         * aggregates file-region mapping information for stitching.
         *
         * @param folderPath The path to the root directory containing image files
         * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @param matchingString A string to match for selecting relevant subdirectories
         * @return A list of maps, each map containing file, region, and subdirName information for stitching
         */
        @Override
        public List<Map<String, Object>> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                                          double baseDownsample, String matchingString) {
            strategyLogger.info("Starting FileNameStitchingStrategy preparation for folder: {}", folderPath);
            strategyLogger.debug("Parameters - pixelSize: {}, downsample: {}, matching: '{}'",
                    pixelSizeInMicrons, baseDownsample, matchingString);

            Path rootdir = Paths.get(folderPath);
            List<Map<String, Object>> allFileRegionMaps = new ArrayList<>();

            try {
                strategyLogger.debug("Beginning directory iteration for root: {}", rootdir);

                // Iterate over each subdirectory in the root directory
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootdir)) {
                    for (Path path : stream) {
                        strategyLogger.debug("Examining path: {} (isDirectory: {}, contains matching string: {})",
                                path, Files.isDirectory(path),
                                path.getFileName().toString().contains(matchingString));

                        if (Files.isDirectory(path) && path.getFileName().toString().contains(matchingString)) {
                            strategyLogger.info("Processing matching subdirectory: {}", path);

                            // Process each subdirectory and collect file-region mappings
                            List<Map<String, Object>> fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample);

                            if (fileRegionMaps != null && !fileRegionMaps.isEmpty()) {
                                allFileRegionMaps.addAll(fileRegionMaps);
                                strategyLogger.debug("Added {} file-region mappings from subdirectory {}",
                                        fileRegionMaps.size(), path.getFileName());
                            } else {
                                strategyLogger.warn("No file-region mappings found in subdirectory: {}", path);
                            }
                        }
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading directory stream for: {}", rootdir, e);
                    throw new RuntimeException("Failed to read directory: " + rootdir, e);
                }

                strategyLogger.info("Directory iteration completed. Total file-region mappings found: {}",
                        allFileRegionMaps.size());

                // Check if any valid file-region mappings were found
                if (allFileRegionMaps.isEmpty()) {
                    strategyLogger.warn("No valid tile configurations found in any subdirectory matching '{}'", matchingString);
                    Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.");
                    return new ArrayList<>();
                }

                strategyLogger.info("FileNameStitchingStrategy preparation completed successfully with {} mappings",
                        allFileRegionMaps.size());
                return allFileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error during FileNameStitchingStrategy preparation", e);
                throw new RuntimeException("Failed to prepare stitching", e);
            }
        }

        /**
         * Processes a single subdirectory to generate file-region mappings for stitching.
         * This method collects all TIFF files in the directory, builds tile configurations,
         * and creates a mapping of each file to its corresponding image region.
         *
         * @param dir The path to the subdirectory to be processed
         * @param pixelSizeInMicrons The pixel size in microns for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @return A list of maps, each map containing file, region, and subdirName for stitching
         */
        private static List<Map<String, Object>> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
            strategyLogger.info("Processing slide in folder: {}", dir);

            try {
                // Collect all TIFF files in the directory
                List<File> files = new ArrayList<>();

                strategyLogger.debug("Collecting TIFF files from directory: {}", dir);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tif*")) {
                    for (Path path : stream) {
                        files.add(path.toFile());
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading TIFF files from directory: {}", dir, e);
                    return new ArrayList<>();
                }

                strategyLogger.info("Found {} TIFF files in directory: {}", files.size(), dir);

                if (files.isEmpty()) {
                    strategyLogger.warn("No TIFF files found in directory: {}", dir);
                    return new ArrayList<>();
                }

                // Build tile configurations from the collected files
                strategyLogger.debug("Building tile configuration with minimum coordinates");
                TileConfigResult tileConfigOutput = buildTileConfigWithMinCoordinates(dir);

                if (tileConfigOutput == null) {
                    strategyLogger.error("Failed to build tile configuration for directory: {}", dir);
                    return new ArrayList<>();
                }

                List<Map<String, Object>> tileConfig = tileConfigOutput.getTileConfig();
                int[] minimumXY = tileConfigOutput.getMinimumXY();

                strategyLogger.info("Built tile configuration with {} tiles, minimum coordinates: [{}, {}]",
                        tileConfig.size(), minimumXY[0], minimumXY[1]);

                // Create file-region mappings for each file
                List<Map<String, Object>> fileRegionMaps = new ArrayList<>();

                strategyLogger.debug("Creating file-region mappings for {} files", files.size());
                for (File file : files) {
                    try {
                        strategyLogger.debug("Processing file: {}", file.getName());

                        ImageRegion region = parseRegionFromOffsetTileConfig(file, tileConfig, minimumXY, pixelSizeInMicrons);

                        if (region != null) {
                            Map<String, Object> mapping = new HashMap<>();
                            mapping.put("file", file);
                            mapping.put("region", region);
                            mapping.put("subdirName", dir.getFileName().toString());
                            fileRegionMaps.add(mapping);

                            strategyLogger.debug("Successfully created mapping for file: {} with region: [{},{} {}x{}]",
                                    file.getName(), region.getX(), region.getY(),
                                    region.getWidth(), region.getHeight());
                        } else {
                            strategyLogger.warn("Failed to create region for file: {}", file.getName());
                        }
                    } catch (Exception e) {
                        strategyLogger.error("Error processing file: {}", file.getName(), e);
                    }
                }

                strategyLogger.info("Created {} file-region mappings for directory: {}", fileRegionMaps.size(), dir);
                return fileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error processing subdirectory: {}", dir, e);
                return new ArrayList<>();
            }
        }

        /**
         * Parses an image region from offset tile configuration.
         * This method calculates the region of an image file based on its position within a larger stitched image.
         *
         * @param file The image file for which to parse the region
         * @param tileConfig A list of maps containing tile configurations, each with image name and its coordinates
         * @param minimumXY The minimum x and y coordinates among all tiles, used for offset calculations
         * @param pixelSizeInMicrons The size of a pixel in microns, used for scaling coordinates
         * @param z The z-plane index of the image (default is 0)
         * @param t The timepoint index of the image (default is 0)
         * @return An ImageRegion object representing the specified region of the image or null if not found
         */
        public static ImageRegion parseRegionFromOffsetTileConfig(File file, List<Map<String, Object>> tileConfig,
                                                                  int[] minimumXY, double pixelSizeInMicrons,
                                                                  int z, int t) {
            strategyLogger.debug("Parsing region for file: {} with {} tile configs", file.getName(), tileConfig.size());

            String imageName = file.getName();

            // Find the configuration for this image
            Map<String, Object> config = tileConfig.stream()
                    .filter(map -> imageName.equals(map.get("imageName")))
                    .findFirst()
                    .orElse(null);

            if (config != null) {
                try {
                    int x = (int) Math.round(((Integer) config.get("x") - minimumXY[0]) / pixelSizeInMicrons);
                    int y = (int) Math.round(((Integer) config.get("y") - minimumXY[1]) / pixelSizeInMicrons);

                    strategyLogger.debug("Calculated offset coordinates for {}: x={}, y={}", imageName, x, y);

                    Map<String, Integer> dimensions = UtilityFunctions.getTiffDimensions(file);
                    if (dimensions == null) {
                        strategyLogger.warn("Could not retrieve dimensions for image: {}", imageName);
                        return null;
                    }

                    int width = dimensions.get("width");
                    int height = dimensions.get("height");

                    strategyLogger.debug("Creating ImageRegion for {}: [{},{} {}x{} z={} t={}]",
                            imageName, x, y, width, height, z, t);

                    return ImageRegion.createInstance(x, y, width, height, z, t);

                } catch (Exception e) {
                    strategyLogger.error("Error parsing region for image: {}", imageName, e);
                    return null;
                }
            } else {
                strategyLogger.warn("No configuration found for image: {}", imageName);
                return null;
            }
        }

        // Overloaded method with default z=0, t=0
        public static ImageRegion parseRegionFromOffsetTileConfig(File file, List<Map<String, Object>> tileConfig,
                                                                  int[] minimumXY, double pixelSizeInMicrons) {
            return parseRegionFromOffsetTileConfig(file, tileConfig, minimumXY, pixelSizeInMicrons, 0, 0);
        }

        /**
         * Builds tile configurations with minimum coordinates for a given directory.
         * This method scans a directory for image files and extracts their coordinates from the file names.
         * It then calculates the minimum X and Y coordinates among all images.
         *
         * @param dir The directory path containing the image files
         * @return A TileConfigResult containing the list of image configurations and minimum coordinates
         */
        public static TileConfigResult buildTileConfigWithMinCoordinates(Path dir) {
            strategyLogger.debug("Building tile configuration for directory: {}", dir);

            List<Map<String, Object>> images = new ArrayList<>();
            Pattern pattern = Pattern.compile(".*\\[(\\d+),(\\d+)\\].*\\.(tif|tiff|ome\\.tif)$");

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{tif,tiff,ome.tif}")) {
                for (Path path : stream) {
                    String filename = path.getFileName().toString();
                    Matcher matcher = pattern.matcher(filename);

                    strategyLogger.debug("Checking filename pattern for: {}", filename);

                    if (matcher.matches()) {
                        try {
                            String imageName = filename;
                            int x = Integer.parseInt(matcher.group(1));
                            int y = Integer.parseInt(matcher.group(2));

                            Map<String, Object> imageMap = new HashMap<>();
                            imageMap.put("imageName", imageName);
                            imageMap.put("x", x);
                            imageMap.put("y", y);
                            images.add(imageMap);

                            strategyLogger.debug("Added tile configuration: {} at [{}, {}]", imageName, x, y);

                        } catch (NumberFormatException e) {
                            strategyLogger.error("Error parsing coordinates from filename: {}", filename, e);
                        }
                    } else {
                        strategyLogger.debug("Filename does not match coordinate pattern: {}", filename);
                    }
                }
            } catch (IOException e) {
                strategyLogger.error("Error reading directory for tile configuration: {}", dir, e);
                return null;
            }

            if (images.isEmpty()) {
                strategyLogger.warn("No images with coordinate patterns found in directory: {}", dir);
                return null;
            }

            // Calculate minimum coordinates
            int minX = images.stream()
                    .mapToInt(map -> (Integer) map.get("x"))
                    .min()
                    .orElse(0);

            int minY = images.stream()
                    .mapToInt(map -> (Integer) map.get("y"))
                    .min()
                    .orElse(0);

            strategyLogger.info("Built tile configuration with {} images, minimum coordinates: [{}, {}]",
                    images.size(), minX, minY);

            return new TileConfigResult(images, new int[]{minX, minY});
        }

        /**
         * Helper class to hold tile configuration results.
         */
        public static class TileConfigResult {
            private final List<Map<String, Object>> tileConfig;
            private final int[] minimumXY;

            public TileConfigResult(List<Map<String, Object>> tileConfig, int[] minimumXY) {
                this.tileConfig = tileConfig;
                this.minimumXY = minimumXY;
            }

            public List<Map<String, Object>> getTileConfig() {
                return tileConfig;
            }

            public int[] getMinimumXY() {
                return minimumXY;
            }
        }
    }

    /**
     * Strategy for stitching images based on tile configurations specified in a TileConfiguration.txt file.
     * This class implements the StitchingStrategy interface, providing an algorithm
     * for processing and stitching images based on their defined tile configurations.
     */
    public static class TileConfigurationTxtStrategy implements StitchingStrategy {
        private static final Logger strategyLogger = LoggerFactory.getLogger(TileConfigurationTxtStrategy.class);

        /**
         * Prepares stitching by processing each subdirectory within the specified root directory.
         * This method iterates over each subdirectory that matches the given criteria and
         * aggregates file-region mapping information for stitching.
         *
         * @param folderPath The path to the root directory containing image files
         * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @param matchingString A string to match for selecting relevant subdirectories
         * @return A list of maps, each map containing file, region, and subdirName information for stitching
         */
        @Override
        public List<Map<String, Object>> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                                          double baseDownsample, String matchingString) {
            strategyLogger.info("Starting TileConfigurationTxtStrategy preparation for folder: {}", folderPath);
            strategyLogger.debug("Parameters - pixelSize: {}, downsample: {}, matching: '{}'",
                    pixelSizeInMicrons, baseDownsample, matchingString);

            Path rootdir = Paths.get(folderPath);
            List<Map<String, Object>> allFileRegionMaps = new ArrayList<>();

            try {
                strategyLogger.debug("Beginning directory iteration for root: {}", rootdir);

                // Iterate over each subdirectory in the root directory
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootdir)) {
                    for (Path path : stream) {
                        strategyLogger.debug("Examining path: {} (isDirectory: {}, contains matching string: {})",
                                path, Files.isDirectory(path),
                                path.getFileName().toString().contains(matchingString));

                        if (Files.isDirectory(path) && path.getFileName().toString().contains(matchingString)) {
                            strategyLogger.info("Processing matching subdirectory: {}", path);

                            // Process each subdirectory and collect file-region mappings
                            List<Map<String, Object>> fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample);

                            if (fileRegionMaps != null && !fileRegionMaps.isEmpty()) {
                                allFileRegionMaps.addAll(fileRegionMaps);
                                strategyLogger.debug("Added {} file-region mappings from subdirectory {}",
                                        fileRegionMaps.size(), path.getFileName());
                            } else {
                                strategyLogger.warn("No file-region mappings found in subdirectory: {}", path);
                            }
                        }
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading directory stream for: {}", rootdir, e);
                    throw new RuntimeException("Failed to read directory: " + rootdir, e);
                }

                strategyLogger.info("Directory iteration completed. Total file-region mappings found: {}",
                        allFileRegionMaps.size());

                // Check if any valid file-region mappings were found
                if (allFileRegionMaps.isEmpty()) {
                    strategyLogger.warn("No valid tile configurations found in any subdirectory matching '{}'", matchingString);
                    Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.");
                    return new ArrayList<>();
                }

                strategyLogger.info("TileConfigurationTxtStrategy preparation completed successfully with {} mappings",
                        allFileRegionMaps.size());
                return allFileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error during TileConfigurationTxtStrategy preparation", e);
                throw new RuntimeException("Failed to prepare stitching", e);
            }
        }

        /**
         * Processes a single subdirectory to generate file-region mappings for stitching.
         * This method reads the TileConfiguration.txt file in the directory (if present),
         * collects all TIFF files, and creates a mapping of each file to its corresponding image region.
         *
         * @param dir The path to the subdirectory to be processed
         * @param pixelSizeInMicrons The pixel size in microns for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @return A list of maps, each map containing file, region, and subdirName for stitching
         */
        private static List<Map<String, Object>> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
            strategyLogger.info("Processing slide in folder: {}", dir);

            try {
                // Check for the existence of TileConfiguration.txt in the directory
                Path tileConfigPath = dir.resolve("TileConfiguration.txt");
                if (!Files.exists(tileConfigPath)) {
                    strategyLogger.warn("Skipping folder as TileConfiguration.txt is missing: {}", dir);
                    return new ArrayList<>();
                }

                strategyLogger.debug("Found TileConfiguration.txt at: {}", tileConfigPath);

                // Parse tile configuration
                List<Map<String, Object>> tileConfig = parseTileConfiguration(tileConfigPath.toString(),
                        pixelSizeInMicrons, baseDownsample);

                if (tileConfig == null || tileConfig.isEmpty()) {
                    strategyLogger.error("Failed to parse tile configuration or configuration is empty for: {}", dir);
                    return new ArrayList<>();
                }

                strategyLogger.info("Completed parseTileConfiguration with {} entries", tileConfig.size());

                // Collect all TIFF files in the directory
                List<File> files = new ArrayList<>();

                strategyLogger.debug("Collecting TIFF files from directory: {}", dir);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tif*")) {
                    for (Path path : stream) {
                        files.add(path.toFile());
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading TIFF files from directory: {}", dir, e);
                    return new ArrayList<>();
                }

                strategyLogger.info("Found {} TIFF files in directory: {}", files.size(), dir);

                // Extract file names from tileConfig and directory for validation
                Set<String> tileConfigFileNames = tileConfig.stream()
                        .map(map -> (String) map.get("imageName"))
                        .collect(Collectors.toSet());

                Set<String> directoryFileNames = files.stream()
                        .map(File::getName)
                        .collect(Collectors.toSet());

                strategyLogger.debug("TileConfig file names: {}", tileConfigFileNames);
                strategyLogger.debug("Directory file names: {}", directoryFileNames);

                // Check if tileConfig file names match with the actual file names in the directory
                if (!tileConfigFileNames.equals(directoryFileNames)) {
                    strategyLogger.warn("Mismatch between tile configuration file names and actual file names in directory: {}", dir);
                    strategyLogger.warn("Config files: {}", tileConfigFileNames);
                    strategyLogger.warn("Directory files: {}", directoryFileNames);
                    return new ArrayList<>(); // Optionally skip processing if there is a mismatch
                }

                // Create file-region mappings for each file
                List<Map<String, Object>> fileRegionMaps = new ArrayList<>();

                strategyLogger.debug("Creating file-region mappings for {} files", files.size());
                for (File file : files) {
                    try {
                        strategyLogger.debug("Processing file: {}", file.getName());

                        ImageRegion region = parseRegionFromTileConfig(file, tileConfig);

                        if (region != null) {
                            Map<String, Object> mapping = new HashMap<>();
                            mapping.put("file", file);
                            mapping.put("region", region);
                            mapping.put("subdirName", dir.getFileName().toString());
                            fileRegionMaps.add(mapping);

                            strategyLogger.debug("Successfully created mapping for file: {} with region: [{},{} {}x{}]",
                                    file.getName(), region.getX(), region.getY(),
                                    region.getWidth(), region.getHeight());
                        } else {
                            strategyLogger.warn("Failed to create region for file: {}", file.getName());
                        }
                    } catch (Exception e) {
                        strategyLogger.error("Error processing file: {}", file.getName(), e);
                    }
                }

                strategyLogger.info("Created {} file-region mappings for directory: {}", fileRegionMaps.size(), dir);
                return fileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error processing subdirectory: {}", dir, e);
                return new ArrayList<>();
            }
        }

        /**
         * Parses the 'TileConfiguration.txt' file to extract image names and their coordinates.
         * The function reads each line of the file, ignoring comments and blank lines.
         * It extracts the image name and coordinates, then stores them in a list.
         *
         * @param filePath The path to the 'TileConfiguration.txt' file
         * @param pixelSizeInMicrons The pixel size in microns for coordinate scaling
         * @param baseDownsample The base downsample value for coordinate scaling
         * @return A list of maps, each containing the image name and its coordinates (x, y)
         */
        public static List<Map<String, Object>> parseTileConfiguration(String filePath, double pixelSizeInMicrons,
                                                                       double baseDownsample) {
            strategyLogger.debug("Parsing tile configuration from: {}", filePath);
            strategyLogger.debug("Scaling parameters - pixelSize: {}, downsample: {}", pixelSizeInMicrons, baseDownsample);

            List<Map<String, Object>> imageCoordinates = new ArrayList<>();

            try {
                List<String> lines = Files.readAllLines(Paths.get(filePath));
                strategyLogger.debug("Read {} lines from tile configuration file", lines.size());

                for (String line : lines) {
                    // Skip comments and empty lines
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }

                    strategyLogger.debug("Processing line: {}", line);

                    String[] parts = line.split(";");
                    if (parts.length >= 3) {
                        try {
                            String imageName = parts[0].trim();
                            String coordinatesPart = parts[2].trim().replaceAll("[()]", "");
                            String[] coordinates = coordinatesPart.split(",");

                            if (coordinates.length >= 2) {
                                double x = Double.parseDouble(coordinates[0].trim());
                                double y = Double.parseDouble(coordinates[1].trim());

                                // Apply scaling if parameters are non-zero
                                if (pixelSizeInMicrons != 0 && baseDownsample != 0) {
                                    x /= (pixelSizeInMicrons * baseDownsample);
                                    y /= (pixelSizeInMicrons * baseDownsample);
                                    strategyLogger.debug("Scaled coordinates for {}: ({}, {}) -> ({}, {})",
                                            imageName,
                                            Double.parseDouble(coordinates[0].trim()),
                                            Double.parseDouble(coordinates[1].trim()),
                                            x, y);
                                }

                                Map<String, Object> imageMap = new HashMap<>();
                                imageMap.put("imageName", imageName);
                                imageMap.put("x", x);
                                imageMap.put("y", y);
                                imageCoordinates.add(imageMap);

                                strategyLogger.debug("Added image configuration: {} at ({}, {})", imageName, x, y);
                            } else {
                                strategyLogger.warn("Invalid coordinate format in line: {}", line);
                            }
                        } catch (NumberFormatException e) {
                            strategyLogger.error("Error parsing coordinates from line: {}", line, e);
                        }
                    } else {
                        strategyLogger.warn("Line does not have expected format (3+ parts separated by ';'): {}", line);
                    }
                }

                strategyLogger.info("Successfully parsed {} image coordinates from tile configuration", imageCoordinates.size());
                return imageCoordinates;

            } catch (IOException e) {
                strategyLogger.error("Error reading tile configuration file: {}", filePath, e);
                return new ArrayList<>();
            } catch (Exception e) {
                strategyLogger.error("Unexpected error parsing tile configuration file: {}", filePath, e);
                return new ArrayList<>();
            }
        }

        /**
         * Parse an ImageRegion from the TileConfiguration.txt data and TIFF file dimensions.
         *
         * @param file The image file for which to get the region
         * @param tileConfig List of tile configurations parsed from TileConfiguration.txt
         * @param z index of z plane (default 0)
         * @param t index of timepoint (default 0)
         * @return An ImageRegion object representing the specified region of the image
         */
        public static ImageRegion parseRegionFromTileConfig(File file, List<Map<String, Object>> tileConfig, int z, int t) {
            strategyLogger.debug("Parsing region for file: {} with {} tile configs", file.getName(), tileConfig.size());

            String imageName = file.getName();

            // Find the configuration for this image
            Map<String, Object> config = tileConfig.stream()
                    .filter(map -> imageName.equals(map.get("imageName")))
                    .findFirst()
                    .orElse(null);

            if (config != null) {
                try {
                    int x = ((Number) config.get("x")).intValue();
                    int y = ((Number) config.get("y")).intValue();

                    strategyLogger.debug("Found coordinates for {}: x={}, y={}", imageName, x, y);

                    Map<String, Integer> dimensions = UtilityFunctions.getTiffDimensions(file);
                    if (dimensions == null) {
                        strategyLogger.warn("Could not retrieve dimensions for image: {}", imageName);
                        return null;
                    }

                    int width = dimensions.get("width");
                    int height = dimensions.get("height");

                    strategyLogger.debug("Creating ImageRegion for {}: [{},{} {}x{} z={} t={}]",
                            imageName, x, y, width, height, z, t);

                    return ImageRegion.createInstance(x, y, width, height, z, t);

                } catch (Exception e) {
                    strategyLogger.error("Error parsing region for image: {}", imageName, e);
                    return null;
                }
            } else {
                strategyLogger.warn("No configuration found for image: {}", imageName);
                return null;
            }
        }

        // Overloaded method with default z=0, t=0
        public static ImageRegion parseRegionFromTileConfig(File file, List<Map<String, Object>> tileConfig) {
            return parseRegionFromTileConfig(file, tileConfig, 0, 0);
        }
    }

    /**
     * Strategy for stitching images based on Vectra metadata.
     * This class implements the StitchingStrategy interface, providing an algorithm
     * for processing and stitching images based on metadata from Vectra imaging systems.
     */
    public static class VectraMetadataStrategy implements StitchingStrategy {
        private static final Logger strategyLogger = LoggerFactory.getLogger(VectraMetadataStrategy.class);

        /**
         * Prepares stitching by processing each subdirectory within the specified root directory.
         * This method iterates over each subdirectory that matches the given criteria and
         * aggregates file-region mapping information for stitching.
         *
         * @param folderPath The path to the root directory containing image files
         * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @param matchingString A string to match for selecting relevant subdirectories
         * @return A list of maps, each map containing file, region, and subdirName information for stitching
         */
        @Override
        public List<Map<String, Object>> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                                          double baseDownsample, String matchingString) {
            strategyLogger.info("Starting VectraMetadataStrategy preparation for folder: {}", folderPath);
            strategyLogger.debug("Parameters - pixelSize: {}, downsample: {}, matching: '{}'",
                    pixelSizeInMicrons, baseDownsample, matchingString);

            Path rootdir = Paths.get(folderPath);
            List<Map<String, Object>> allFileRegionMaps = new ArrayList<>();

            try {
                strategyLogger.debug("Beginning directory iteration for root: {}", rootdir);

                // Iterate over each subdirectory in the root directory
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootdir)) {
                    for (Path path : stream) {
                        strategyLogger.debug("Examining path: {} (isDirectory: {}, contains matching string: {})",
                                path, Files.isDirectory(path),
                                path.getFileName().toString().contains(matchingString));

                        if (Files.isDirectory(path) && path.getFileName().toString().contains(matchingString)) {
                            strategyLogger.info("Processing matching subdirectory: {}", path);

                            // Process each subdirectory and collect file-region mappings
                            List<Map<String, Object>> fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample);

                            if (fileRegionMaps != null && !fileRegionMaps.isEmpty()) {
                                allFileRegionMaps.addAll(fileRegionMaps);
                                strategyLogger.debug("Added {} file-region mappings from subdirectory {}",
                                        fileRegionMaps.size(), path.getFileName());
                            } else {
                                strategyLogger.warn("No file-region mappings found in subdirectory: {}", path);
                            }
                        }
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading directory stream for: {}", rootdir, e);
                    throw new RuntimeException("Failed to read directory: " + rootdir, e);
                }

                strategyLogger.info("Directory iteration completed. Total file-region mappings found: {}",
                        allFileRegionMaps.size());

                // Check if any valid file-region mappings were found
                if (allFileRegionMaps.isEmpty()) {
                    strategyLogger.warn("No valid tile configurations found in any subdirectory matching '{}'", matchingString);
                    Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.");
                    return new ArrayList<>();
                }

                strategyLogger.info("VectraMetadataStrategy preparation completed successfully with {} mappings",
                        allFileRegionMaps.size());
                return allFileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error during VectraMetadataStrategy preparation", e);
                throw new RuntimeException("Failed to prepare stitching", e);
            }
        }

        /**
         * Processes a single subdirectory to generate file-region mappings for stitching.
         * This method collects all TIFF files in the directory and creates a mapping of each file
         * to its corresponding image region based on Vectra metadata.
         *
         * @param dir The path to the subdirectory to be processed
         * @param pixelSizeInMicrons The pixel size in microns for calculating image regions
         * @param baseDownsample The base downsample value for the stitching process
         * @return A list of maps, each map containing file, region, and subdirName for stitching
         */
        private static List<Map<String, Object>> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
            strategyLogger.info("Processing slide in folder: {}", dir);

            try {
                // Collect all TIFF files in the directory
                List<File> files = new ArrayList<>();

                strategyLogger.debug("Collecting TIFF files from directory: {}", dir);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tif*")) {
                    for (Path path : stream) {
                        files.add(path.toFile());
                    }
                } catch (IOException e) {
                    strategyLogger.error("Error reading TIFF files from directory: {}", dir, e);
                    return new ArrayList<>();
                }

                strategyLogger.info("Parsing regions from {} files...", files.size());

                if (files.isEmpty()) {
                    strategyLogger.warn("No TIFF files found in directory: {}", dir);
                    return new ArrayList<>();
                }

                // Create file-region mappings for each file
                List<Map<String, Object>> fileRegionMaps = new ArrayList<>();

                strategyLogger.debug("Creating file-region mappings for {} files", files.size());
                for (File file : files) {
                    try {
                        strategyLogger.debug("Processing file: {}", file.getName());

                        ImageRegion region = parseRegion(file);

                        if (region != null) {
                            Map<String, Object> mapping = new HashMap<>();
                            mapping.put("file", file);
                            mapping.put("region", region);
                            mapping.put("subdirName", dir.getFileName().toString());
                            fileRegionMaps.add(mapping);

                            strategyLogger.debug("Successfully created mapping for file: {} with region: [{},{} {}x{}]",
                                    file.getName(), region.getX(), region.getY(),
                                    region.getWidth(), region.getHeight());
                        } else {
                            strategyLogger.warn("Failed to create region for file: {}", file.getName());
                        }
                    } catch (Exception e) {
                        strategyLogger.error("Error processing file: {}", file.getName(), e);
                    }
                }

                strategyLogger.info("Created {} file-region mappings for directory: {}", fileRegionMaps.size(), dir);
                return fileRegionMaps;

            } catch (Exception e) {
                strategyLogger.error("Unexpected error processing subdirectory: {}", dir, e);
                return new ArrayList<>();
            }
        }

        /**
         * Parses the image region from a given file based on Vectra metadata.
         * This method checks if the file is a TIFF and then parses the region from the TIFF file.
         *
         * @param file The image file for which to parse the region
         * @param z The z-plane index of the image (default is 0)
         * @param t The timepoint index of the image (default is 0)
         * @return An ImageRegion object representing the specified region of the image, or null if not found
         */
        public static ImageRegion parseRegion(File file, int z, int t) {
            strategyLogger.debug("Parsing region for file: {} (z={}, t={})", file.getName(), z, t);

            if (checkTIFF(file)) {
                try {
                    return parseRegionFromTIFF(file, z, t);
                } catch (Exception e) {
                    strategyLogger.error("Error parsing region from TIFF file: {}", file.getName(), e);
                    return null;
                }
            } else {
                strategyLogger.warn("File is not a valid TIFF: {}", file.getName());
                return null;
            }
        }

        // Overloaded method with default z=0, t=0
        public static ImageRegion parseRegion(File file) {
            return parseRegion(file, 0, 0);
        }

        /**
         * Checks if the provided file is a TIFF image by examining its 'magic number'.
         * TIFF files typically start with a specific byte order indicator (0x4949 for little-endian
         * or 0x4d4d for big-endian), followed by a fixed number (42 or 43).
         *
         * @param file The file to be checked
         * @return True if the file is a TIFF image, false otherwise
         */
        public static boolean checkTIFF(File file) {
            strategyLogger.debug("Checking if file is TIFF: {}", file.getName());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = new byte[4];
                int bytesRead = fis.read(bytes);

                if (bytesRead < 4) {
                    strategyLogger.debug("File too small to be TIFF: {}", file.getName());
                    return false;
                }

                short byteOrder = toShort(bytes[0], bytes[1]);

                // Interpret the next two bytes based on the byte order
                int val;
                if (byteOrder == 0x4949) { // Little-endian
                    val = toShort(bytes[3], bytes[2]);
                    strategyLogger.debug("Little-endian byte order detected for: {}", file.getName());
                } else if (byteOrder == 0x4d4d) { // Big-endian
                    val = toShort(bytes[2], bytes[3]);
                    strategyLogger.debug("Big-endian byte order detected for: {}", file.getName());
                } else {
                    strategyLogger.debug("Invalid byte order for TIFF file: {} (0x{:04x})", file.getName(), byteOrder);
                    return false;
                }

                boolean isTiff = (val == 42 || val == 43); // TIFF magic number
                strategyLogger.debug("TIFF magic number check for {}: {} (value: {})", file.getName(), isTiff, val);
                return isTiff;

            } catch (IOException e) {
                strategyLogger.error("Error checking TIFF magic number for file: {}", file.getName(), e);
                return false;
            }
        }

        /**
         * Converts two bytes into a short, in the specified byte order.
         *
         * @param b1 The first byte
         * @param b2 The second byte
         * @return The combined short value
         */
        public static short toShort(byte b1, byte b2) {
            return (short) ((b1 << 8) + (b2 & 0xFF));
        }

        /**
         * Parses the image region from a TIFF file using metadata information.
         * Reads TIFF metadata to determine the image's physical position and dimensions,
         * then calculates and returns the corresponding ImageRegion object.
         *
         * @param file The TIFF image file to parse
         * @param z The z-plane index of the image (default is 0)
         * @param t The timepoint index of the image (default is 0)
         * @return An ImageRegion object representing the specified region of the image
         */
        public static ImageRegion parseRegionFromTIFF(File file, int z, int t) {
            strategyLogger.debug("Parsing region from TIFF metadata for file: {}", file.getName());

            int x, y, width, height;

            try (FileInputStream fis = new FileInputStream(file);
                 ImageInputStream iis = ImageIO.createImageInputStream(fis)) {

                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("TIFF");
                if (!readers.hasNext()) {
                    strategyLogger.error("No TIFF readers available for file: {}", file.getName());
                    return null;
                }

                ImageReader reader = readers.next();
                reader.setInput(iis);

                try {
                    var metadata = reader.getImageMetadata(0);
                    TIFFDirectory tiffDir = TIFFDirectory.createFromMetadata(metadata);

                    strategyLogger.debug("Successfully created TIFF directory from metadata for: {}", file.getName());

                    // Extract resolution and position values from the metadata
                    double xRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_RESOLUTION);
                    double yRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_RESOLUTION);
                    double xPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_POSITION);
                    double yPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_POSITION);

                    strategyLogger.debug("TIFF metadata for {}: xRes={}, yRes={}, xPos={}, yPos={}",
                            file.getName(), xRes, yRes, xPos, yPos);

                    // Extract image dimensions from the metadata
                    width = (int) tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_WIDTH).getAsLong(0);
                    height = (int) tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_LENGTH).getAsLong(0);

                    // Calculate the x and y coordinates in the final stitched image
                    x = (int) Math.round(xRes * xPos);
                    y = (int) Math.round(yRes * yPos);

                    strategyLogger.debug("Calculated coordinates for {}: x={}, y={}, width={}, height={}",
                            file.getName(), x, y, width, height);

                    return ImageRegion.createInstance(x, y, width, height, z, t);

                } finally {
                    reader.dispose();
                }

            } catch (IOException e) {
                strategyLogger.error("IO error parsing TIFF metadata from file: {}", file.getName(), e);
                return null;
            } catch (Exception e) {
                strategyLogger.error("Unexpected error parsing TIFF metadata from file: {}", file.getName(), e);
                return null;
            }
        }

        /**
         * Extracts a rational number from TIFF metadata based on the specified tag.
         * The rational number is represented as a fraction (numerator/denominator).
         *
         * @param tiffDir The TIFFDirectory object containing the metadata
         * @param tag The metadata tag to extract the rational number from
         * @return The rational number as a double
         */
        public static double getRational(TIFFDirectory tiffDir, int tag) {
            try {
                long[] rational = tiffDir.getTIFFField(tag).getAsRational(0);
                double result = rational[0] / (double) rational[1];
                strategyLogger.debug("Extracted rational for tag {}: {}/{} = {}", tag, rational[0], rational[1], result);
                return result;
            } catch (Exception e) {
                strategyLogger.error("Error extracting rational value for tag: {}", tag, e);
                return 0.0;
            }
        }
    }

    /**
     * Sets the stitching strategy to be used.
     *
     * @param strategy The stitching strategy to be set
     */
    public static void setStitchingStrategy(StitchingStrategy strategy) {
        logger.info("Setting stitching strategy to: {}", strategy.getClass().getSimpleName());
        StitchingImplementations.strategy = strategy;
    }

    /**
     * Core method to perform stitching based on the specified stitching type and other parameters.
     * This method determines the stitching strategy, prepares stitching, and then performs the stitching process.
     *
     * @param stitchingType The type of stitching to be performed
     * @param folderPath The path to the folder containing images to be stitched
     * @param outputPath The path where the stitched output will be saved
     * @param compressionType The type of compression to be used in the stitching output
     * @param pixelSizeInMicrons The size of a pixel in microns
     * @param baseDownsample The base downsample value for the stitching process
     * @param matchingString A string to match for selecting relevant subdirectories or files
     * @param zSpacingMicrons The z-spacing in microns (default 1.0)
     * @return The path to the created stitched image file, or null if failed
     */
    public static String stitchCore(String stitchingType, String folderPath, String outputPath,
                                    String compressionType, double pixelSizeInMicrons,
                                    double baseDownsample, String matchingString, double zSpacingMicrons) {
        logger.info("=== Starting stitching process ===");
        logger.info("Stitching type: {}", stitchingType);
        logger.info("Folder path: {}", folderPath);
        logger.info("Output path: {}", outputPath);
        logger.info("Compression type: {}", compressionType);
        logger.info("Pixel size (microns): {}", pixelSizeInMicrons);
        logger.info("Base downsample: {}", baseDownsample);
        logger.info("Matching string: '{}'", matchingString);
        logger.info("Z-spacing (microns): {}", zSpacingMicrons);

        try {
            // Determine the stitching strategy based on the provided type
            logger.info("=== Determining stitching strategy ===");
            switch (stitchingType) {
                case "Filename[x,y] with coordinates in microns":
                    logger.info("Selected FileNameStitchingStrategy");
                    setStitchingStrategy(new FileNameStitchingStrategy());
                    break;
                case "Vectra tiles with metadata":
                    logger.info("Selected VectraMetadataStrategy");
                    setStitchingStrategy(new VectraMetadataStrategy());
                    break;
                case "Coordinates in TileConfiguration.txt file":
                    logger.info("Selected TileConfigurationTxtStrategy");
                    setStitchingStrategy(new TileConfigurationTxtStrategy());
                    break;
                default:
                    logger.error("Unrecognized stitching type: {}", stitchingType);
                    StitchingGUI.showAlertDialog("Error with choosing a stitching method, code here should not be reached in StitchingImplementations.java");
                    return null; // Safely exit the method if the stitching type is not recognized
            }

            // Proceed with the stitching process if a valid strategy is set
            if (strategy == null) {
                logger.error("No valid stitching strategy set");
                return null;
            }

            logger.info("=== Preparing stitching with selected strategy ===");
            // Prepare stitching by processing the folder with the selected strategy
            List<Map<String, Object>> fileRegionPairs = strategy.prepareStitching(folderPath, pixelSizeInMicrons,
                    baseDownsample, matchingString);

            // Check if valid file-region pairs were obtained
            if (fileRegionPairs == null || fileRegionPairs.isEmpty()) {
                logger.error("No valid file-region pairs found");
                StitchingGUI.showAlertDialog("No valid folders found matching the criteria.");
                return null; // Exit the method if no valid file-region pairs are found
            }

            logger.info("=== Building image server ===");
            logger.info("Processing {} file-region pairs", fileRegionPairs.size());

            OMEPyramidWriter.CompressionType compression = UtilityFunctions.getCompressionType(compressionType);
            SparseImageServer.Builder builder = new SparseImageServer.Builder();

            String subdirName = null;
            int processedPairs = 0;

            // Process each file-region pair to build the image server for stitching
            for (Map<String, Object> pair : fileRegionPairs) {
                if (pair == null) {
                    logger.warn("Encountered a null pair in fileRegionPairs");
                    continue; // Skip this iteration if the pair is null
                }

                File file = (File) pair.get("file");
                ImageRegion region = (ImageRegion) pair.get("region");
                subdirName = (String) pair.get("subdirName"); // Extract subdirName from the pair

                if (file == null) {
                    logger.warn("File is null in pair: {}", pair);
                    continue; // Skip this iteration if the file is null
                }

                if (region == null) {
                    logger.warn("Region is null in pair: {}", pair);
                    continue; // Skip this iteration if the region is null
                }

                try {
                    logger.debug("Processing file-region pair {}/{}: {} -> [{},{} {}x{}]",
                            ++processedPairs, fileRegionPairs.size(),
                            file.getName(), region.getX(), region.getY(),
                            region.getWidth(), region.getHeight());

                    // Add the region to the image server builder
                    var serverBuilders = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class,
                            file.toURI().toString()).getBuilders();
                    if (serverBuilders.isEmpty()) {
                        logger.error("No server builders available for file: {}", file.getName());
                        continue;
                    }

                    var serverBuilder = serverBuilders.get(0);
                    builder.jsonRegion(region, 1.0, serverBuilder);

                    logger.debug("Successfully added region for file: {}", file.getName());

                } catch (Exception e) {
                    logger.error("Error processing file-region pair for file: {}", file.getName(), e);
                    continue;
                }
            }

            logger.info("=== Building and configuring server ===");
            // Build and pyramidalize the server for the final stitched image
            var server = builder.build();

            var metadataNew = new ImageServerMetadata.Builder(server.getMetadata())
                    .pixelSizeMicrons(pixelSizeInMicrons, pixelSizeInMicrons)
                    .zSpacingMicrons(zSpacingMicrons)
                    .build();

            server.setMetadata(metadataNew);
            server = ImageServers.pyramidalize(server);

            logger.info("Server built successfully with metadata: {}x{} pixels, {:.3f} μm/pixel",
                    server.getWidth(), server.getHeight(), pixelSizeInMicrons);

            logger.info("=== Writing stitched image ===");
            // Write the final stitched image
            long startTime = System.currentTimeMillis();

            String filename = subdirName != null ? subdirName : Paths.get(folderPath).getFileName().toString();
            Path outputFilePath = baseDownsample == 1 ?
                    Paths.get(outputPath).resolve(filename + ".ome.tif") :
                    Paths.get(outputPath).resolve(filename + "_" + (int) baseDownsample + "x_downsample.ome.tif");

            String pathOutput = outputFilePath.toAbsolutePath().toString();
            pathOutput = UtilityFunctions.getUniqueFilePath(pathOutput);

            logger.info("Writing image to: {}", pathOutput);
            logger.info("Using compression: {}, tile size: 512, downsample: {}", compression, baseDownsample);

            new OMEPyramidWriter.Builder(server)
                    .tileSize(512)
                    .channelsInterleaved()
                    .parallelize(true)
                    .compression(compression)
                    .scaledDownsampling(baseDownsample, 4)
                    .build()
                    .writeSeries(pathOutput);

            long endTime = System.currentTimeMillis();
            double durationSeconds = (endTime - startTime) / 1000.0;

            logger.info("=== Stitching completed successfully ===");
            logger.info("Image written to: {}", pathOutput);
            logger.info("Processing time: {} seconds", GeneralTools.formatNumber(durationSeconds, 1));

            server.close();
            return pathOutput;

        } catch (Exception e) {
            logger.error("Fatal error during stitching process", e);
            return null;
        }
    }

    // Overloaded method with default zSpacingMicrons=1.0
    public static String stitchCore(String stitchingType, String folderPath, String outputPath,
                                    String compressionType, double pixelSizeInMicrons,
                                    double baseDownsample, String matchingString) {
        return stitchCore(stitchingType, folderPath, outputPath, compressionType,
                pixelSizeInMicrons, baseDownsample, matchingString, 1.0);
    }
}