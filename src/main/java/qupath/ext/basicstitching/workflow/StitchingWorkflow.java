package qupath.ext.basicstitching.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.assembly.ImageAssembler;
import qupath.ext.basicstitching.assembly.PyramidImageWriter;
import qupath.ext.basicstitching.stitching.StitchingStrategy;
import qupath.ext.basicstitching.stitching.StitchingStrategyFactory;
import qupath.ext.basicstitching.stitching.TileMapping;
import qupath.lib.images.servers.SparseImageServer;

import java.util.List;

/**
 * Orchestrates the complete stitching workflow: from mapping tiles to writing the final OME-TIFF pyramid.
 */
public class StitchingWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(StitchingWorkflow.class);

    /**
     * Run the stitching workflow.
     * @param stitchingType Type of stitching (used to select strategy)
     * @param folderPath Path to image tiles root folder
     * @param outputPath Path to save output OME-TIFF
     * @param compressionType Compression type string (e.g. "LZW")
     * @param pixelSizeMicrons Pixel size in microns
     * @param baseDownsample Downsample factor
     * @param matchingString Folder/file filter string
     * @param zSpacingMicrons Z spacing in microns
     * @param outputFilename Optional: override output base name (if null, use folder name)
     * @return Path to written OME-TIFF, or null on failure
     */
    public static String run(
            String stitchingType,
            String folderPath,
            String outputPath,
            String compressionType,
            double pixelSizeMicrons,
            double baseDownsample,
            String matchingString,
            double zSpacingMicrons,
            String outputFilename
    ) {
        try {
            logger.info("Stitching workflow starting: type={} folder={}", stitchingType, folderPath);
            // 1. Select strategy
            StitchingStrategy strategy = StitchingStrategyFactory.getStrategy(stitchingType);
            if (strategy == null) {
                logger.error("No valid stitching strategy for type: {}", stitchingType);
                return null;
            }
            // 2. Prepare tile mappings
            List<TileMapping> mappings = strategy.prepareStitching(
                    folderPath, pixelSizeMicrons, baseDownsample, matchingString);
            if (mappings == null || mappings.isEmpty()) {
                logger.error("No tile mappings produced by strategy");
                return null;
            }
            // 3. Assemble sparse image server
            SparseImageServer server = ImageAssembler.assemble(mappings, pixelSizeMicrons, zSpacingMicrons);
            if (server == null) {
                logger.error("Failed to assemble image server");
                return null;
            }
            // 4. Write pyramid OME-TIFF
            String outBase = outputFilename != null ? outputFilename :
                    java.nio.file.Paths.get(folderPath).getFileName().toString();
            String written = PyramidImageWriter.write(
                    server,
                    outputPath,
                    outBase,
                    compressionType,
                    baseDownsample
            );
            logger.info("Stitching workflow complete. Output: {}", written);
            return written;
        } catch (Exception e) {
            logger.error("Exception in StitchingWorkflow", e);
            return null;
        }
    }
}
