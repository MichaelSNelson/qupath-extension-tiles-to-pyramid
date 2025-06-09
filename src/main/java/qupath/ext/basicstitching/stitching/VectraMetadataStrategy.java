package qupath.ext.basicstitching.stitching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.utilities.UtilityFunctions;
import qupath.lib.regions.ImageRegion;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Stitching strategy for Vectra multiplex TIFFs using metadata for position.
 * Builds TileMapping list for all TIFF files in matching subfolders.
 */
public class VectraMetadataStrategy implements StitchingStrategy {
    private static final Logger logger = LoggerFactory.getLogger(VectraMetadataStrategy.class);

    @Override
    public List<TileMapping> prepareStitching(String folderPath, double pixelSizeInMicrons,
                                              double baseDownsample, String matchingString) {
        logger.info("Preparing stitching using Vectra metadata strategy for folder: {}", folderPath);
        List<TileMapping> mappings = new ArrayList<>();
        Path rootdir = Paths.get(folderPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootdir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && path.getFileName().toString().contains(matchingString)) {
                    logger.info("Processing subdir: {}", path);
                    try (DirectoryStream<Path> tifStream = Files.newDirectoryStream(path, "*.tif*")) {
                        for (Path tifPath : tifStream) {
                            String filename = tifPath.getFileName().toString();
                            UtilityFunctions.VectraRegionInfo info = UtilityFunctions.getVectraPositionAndDimensions(tifPath.toFile());
                            if (info != null) {
                                // If info.xPx/yPx are already in pixels, no scaling by pixelSizeInMicrons needed
                                ImageRegion region = ImageRegion.createInstance(
                                        info.xPx, info.yPx, info.width, info.height, 0, 0
                                );
                                mappings.add(new TileMapping(
                                        tifPath.toFile(), region, path.getFileName().toString()
                                ));
                                logger.debug("Added mapping for Vectra file {} at ({}, {})", filename, info.xPx, info.yPx);
                            } else {
                                logger.warn("Failed to extract Vectra metadata for {}", filename);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in VectraMetadataStrategy", e);
        }
        logger.info("Total Vectra tiles mapped: {}", mappings.size());
        return mappings;
    }

    /** Dummy class. Replace with your actual Vectra position extraction logic. */
    public static class VectraPositionInfo {
        public final double x;
        public final double y;
        public VectraPositionInfo(double x, double y) { this.x = x; this.y = y; }
    }
}
