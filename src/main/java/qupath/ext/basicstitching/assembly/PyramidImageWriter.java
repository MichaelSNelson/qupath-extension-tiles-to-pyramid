package qupath.ext.basicstitching.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.writers.ome.OMEPyramidWriter;
import qupath.ext.basicstitching.utilities.UtilityFunctions;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes an assembled SparseImageServer to disk as a pyramid OME-TIFF file.
 */
public class PyramidImageWriter {
    private static final Logger logger = LoggerFactory.getLogger(PyramidImageWriter.class);

    /**
     * Write the server as a pyramidal OME-TIFF using the specified options.
     * @param server The assembled image server
     * @param outputPath Folder for output
     * @param filename Filename (no extension)
     * @param compressionType Compression type (QuPath enum or String, e.g. \"LZW\")
     * @param baseDownsample Downsample factor (typically 1)
     * @return Absolute path to output file, or null on failure
     */
    public static String write(ImageServer<BufferedImage> server, String outputPath, String filename,
                               String compressionType, double baseDownsample) {
        try {
            Path outFile = (baseDownsample == 1)
                    ? Paths.get(outputPath).resolve(filename + ".ome.tif")
                    : Paths.get(outputPath).resolve(filename + "_" + (int)baseDownsample + "x_downsample.ome.tif");
            String output = UtilityFunctions.getUniqueFilePath(outFile.toString());

            OMEPyramidWriter.CompressionType comp = UtilityFunctions.getCompressionType(compressionType);
            logger.info("Writing pyramid OME-TIFF: {} (compression={}, tileSize=512, downsample={})",
                    output, comp, baseDownsample);

            // Pyramidalize server (in case original was not)
            ImageServer<BufferedImage> pyramidServer = ImageServers.pyramidalize(server);

            long t0 = System.currentTimeMillis();

            new OMEPyramidWriter.Builder(pyramidServer)
                    .tileSize(512)
                    .channelsInterleaved()
                    .parallelize(true)
                    .compression(comp)
                    .scaledDownsampling(baseDownsample, 4)
                    .build()
                    .writeSeries(output);

            pyramidServer.close();

            logger.info("Finished writing pyramid in {:.1f}s: {}", (System.currentTimeMillis() - t0)/1000.0, output);
            return output;
        } catch (Exception e) {
            logger.error("Failed to write pyramid OME-TIFF", e);
            return null;
        }
    }
}
