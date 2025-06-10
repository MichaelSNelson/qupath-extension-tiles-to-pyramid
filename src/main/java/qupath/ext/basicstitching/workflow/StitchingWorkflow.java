package qupath.ext.basicstitching.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.assembly.ImageAssembler;
import qupath.ext.basicstitching.assembly.PyramidImageWriter;
import qupath.ext.basicstitching.config.StitchingConfig;
import qupath.ext.basicstitching.stitching.StitchingStrategy;
import qupath.ext.basicstitching.stitching.StitchingStrategyFactory;
import qupath.ext.basicstitching.stitching.TileMapping;
import qupath.lib.images.servers.SparseImageServer;

import java.util.List;


/**
 * Orchestrates the complete stitching workflow:
 * <ul>
 *     <li>Selects the appropriate {@link StitchingStrategy} based on user configuration.</li>
 *     <li>Prepares tile-to-position mappings for all relevant image tiles.</li>
 *     <li>Assembles the tiles into a virtual sparse image server.</li>
 *     <li>Writes the resulting image as a multi-resolution OME-TIFF pyramid.</li>
 * </ul>
 *
 * <p>
 * To run a stitching job, provide a {@link StitchingConfig} object describing the workflow parameters
 * (stitching type, input/output paths, compression, pixel size, downsampling, filter, etc).
 * </p>
 *
 * <p>
 * The workflow logs all major steps and errors to facilitate debugging. The final stitched image is written
 * to the specified output directory. On failure, <code>null</code> is returned.
 * </p>
 *
 * <b>Example usage:</b>
 * <pre>
 *     StitchingConfig config = new StitchingConfig(...);
 *     String outputPath = StitchingWorkflow.run(config);
 *     if (outputPath != null) {
 *         System.out.println("Stitching complete: " + outputPath);
 *     }
 * </pre>
 */
public class StitchingWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(StitchingWorkflow.class);

    /**
     * Runs the entire stitching pipeline from tile mapping to OME-TIFF export.
     *
     * @param config The {@link StitchingConfig} specifying workflow parameters and options.
     * @return The absolute path to the output OME-TIFF, or {@code null} on error.
     */
    public static String run(StitchingConfig config) {
        try {
            logger.info("Stitching workflow starting: type={} folder={}", config.stitchingType, config.folderPath);

            // 1. Select the appropriate strategy for this stitching type.
            StitchingStrategy strategy = StitchingStrategyFactory.getStrategy(config.stitchingType);
            if (strategy == null) {
                logger.error("No valid stitching strategy for type: {}", config.stitchingType);
                return null;
            }

            // 2. Prepare tile mappings (tile file, region, and group info)
            List<TileMapping> mappings = strategy.prepareStitching(
                    config.folderPath,
                    config.pixelSizeInMicrons,
                    config.baseDownsample,
                    config.matchingString
            );
            if (mappings == null || mappings.isEmpty()) {
                logger.error("No tile mappings produced by strategy");
                return null;
            }

            // 3. Assemble sparse image server (virtual mosaic)
            SparseImageServer server = ImageAssembler.assemble(
                    mappings,
                    config.pixelSizeInMicrons,
                    config.zSpacingMicrons
            );
            if (server == null) {
                logger.error("Failed to assemble image server");
                return null;
            }

            // 4. Determine output filename (if config has such a field, otherwise use folder name)
            // ---- Start robust output filename logic ----
            String outBase;
            try {
                // Try to use config.outputFilename if it exists
                java.lang.reflect.Field f = config.getClass().getField("outputFilename");
                String candidate = (String) f.get(config);
                outBase = (candidate != null && !candidate.isBlank())
                        ? candidate
                        : java.nio.file.Paths.get(config.folderPath).getFileName().toString();
            } catch (NoSuchFieldException nsfe) {
                // Field doesn't exist in config; use folder name
                outBase = java.nio.file.Paths.get(config.folderPath).getFileName().toString();
            }
            // ---- End robust output filename logic ----

            // 5. Write output OME-TIFF pyramid
            String written = PyramidImageWriter.write(
                    server,
                    config.outputPath,
                    outBase,
                    config.compressionType,
                    config.baseDownsample
            );
            logger.info("Stitching workflow complete. Output: {}", written);
            return written;
        } catch (Exception e) {
            logger.error("Exception in StitchingWorkflow", e);
            return null;
        }
    }
}