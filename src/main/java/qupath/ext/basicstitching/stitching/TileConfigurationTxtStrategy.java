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
                    Map<String, Position> positionMap = parseTileConfig(configPath, pixelSizeInMicrons, baseDownsample);
                    try (DirectoryStream<Path> tifStream = Files.newDirectoryStream(path, "*.tif*")) {
                        for (Path tifPath : tifStream) {
                            String filename = tifPath.getFileName().toString();
                            Position pos = positionMap.get(filename);
                            Map<String, Integer> dims = UtilityFunctions.getTiffDimensions(tifPath.toFile());
                            if (pos != null && dims != null) {
                                ImageRegion region = ImageRegion.createInstance(
                                        (int)pos.x, (int)pos.y, dims.get("width"), dims.get("height"), 0, 0
                                );
                                mappings.add(new TileMapping(
                                        tifPath.toFile(), region, path.getFileName().toString()
                                ));
                                logger.debug("Mapped {} at ({}, {}) from config", filename, pos.x, pos.y);
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
