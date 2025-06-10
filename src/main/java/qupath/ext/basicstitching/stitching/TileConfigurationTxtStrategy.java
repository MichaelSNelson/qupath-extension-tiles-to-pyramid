package qupath.ext.basicstitching.stitching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.utilities.UtilityFunctions;
import qupath.lib.regions.ImageRegion;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Stitching strategy that reads a TileConfiguration.txt for image positions.
 * Only processes subfolders that match the given string and have a TileConfiguration.txt present.
 */
public class TileConfigurationTxtStrategy implements StitchingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(TileConfigurationTxtStrategy.class);
    /**
     * Prepares tile mappings for image stitching based on coordinates in TileConfiguration.txt files.
     *
     * This method:
     * <ul>
     *     <li>Iterates through subdirectories in the specified root folder whose names contain the given matching string.</li>
     *     <li>For each such subdirectory, parses its TileConfiguration.txt to get image positions (in microns, downsampled as specified).</li>
     *     <li>Finds all TIFF files, matches them to config entries, and creates ImageRegion mappings.</li>
     *     <li>Adjusts the Y coordinate for each tile at runtime so that tiles with the largest Y are placed at the top,
     *         matching the convention of Fiji's Grid/Collection Stitcher.</li>
     * </ul>
     *
     * @param folderPath         The path to the root directory containing tile subdirectories.
     * @param pixelSizeInMicrons The pixel size in microns (used to scale coordinates to pixels).
     * @param baseDownsample     Downsampling factor applied to the coordinates.
     * @param matchingString     String to match in subdirectory names for inclusion.
     * @return A list of TileMapping objects representing each tile's file, image region, and subdirectory.
     */
    @Override
    public List<TileMapping> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                              double baseDownsample, String matchingString) {
        logger.info("Preparing stitching using TileConfiguration.txt strategy for folder: {}", folderPath);
        List<TileMapping> mappings = new ArrayList<>();
        Path rootdir = Paths.get(folderPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootdir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && path.getFileName().toString().contains(matchingString)) {
                    Path configPath = path.resolve("TileConfiguration.txt");
                    if (!Files.exists(configPath)) {
                        logger.warn("No TileConfiguration.txt in subdir: {}", path);
                        continue;
                    }
                    logger.info("Processing subdir: {} with config {}", path, configPath);

                    // Parse positions from config
                    Map<String, Position> positionMap = parseTileConfig(configPath, pixelSizeInMicrons, baseDownsample);

                    // Compute maxY for this subdirectory (needed for Y-flip adjustment)
                    OptionalDouble maxYOpt = positionMap.values().stream()
                            .mapToDouble(pos -> pos.y)
                            .max();
                    if (!maxYOpt.isPresent()) {
                        logger.warn("No tile positions found in config: {}", configPath);
                        continue;
                    }
                    double maxY = maxYOpt.getAsDouble();

                    try (DirectoryStream<Path> tifStream = Files.newDirectoryStream(path, "*.tif*")) {
                        for (Path tifPath : tifStream) {
                            String filename = tifPath.getFileName().toString();
                            Position pos = positionMap.get(filename);
                            Map<String, Integer> dims = UtilityFunctions.getTiffDimensions(tifPath.toFile());
                            if (pos != null && dims != null) {
                                // Flip Y so that larger Y values are at the top (Fiji-style)
                                double adjustedY = maxY - pos.y;
                                ImageRegion region = ImageRegion.createInstance(
                                        (int)Math.round(pos.x),
                                        (int)Math.round(adjustedY),
                                        dims.get("width"),
                                        dims.get("height"),
                                        0, 0
                                );
                                mappings.add(new TileMapping(
                                        tifPath.toFile(), region, path.getFileName().toString()
                                ));
                                logger.debug("Mapped {} at ({}, {} [flipped Y]) from config", filename, pos.x, adjustedY);
                            } else {
                                logger.warn("Missing config position or TIFF dimensions for {}", filename);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in TileConfigurationTxtStrategy", e);
        }
        logger.info("Total tiles mapped from TileConfiguration.txt: {}", mappings.size());
        return mappings;
    }

    /**
     * Parse a TileConfiguration.txt file and return a mapping of file names to positions.
     */
    private static Map<String, Position> parseTileConfig(Path configPath, double pixelSizeInMicrons, double baseDownsample) {
        Map<String, Position> map = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(configPath);
            for (String line : lines) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    String imageName = parts[0].trim();
                    String[] coord = parts[2].replaceAll("[(){}]", "").split(",");
                    if (coord.length >= 2) {
                        double x = Double.parseDouble(coord[0].trim()) / (pixelSizeInMicrons * baseDownsample);
                        double y = Double.parseDouble(coord[1].trim()) / (pixelSizeInMicrons * baseDownsample);
                        map.put(imageName, new Position(x, y));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing TileConfiguration.txt at {}", configPath, e);
        }
        return map;
    }

    /** Holds a 2D position. */
    private static class Position {
        final double x, y;
        Position(double x, double y) { this.x = x; this.y = y; }
    }
}
