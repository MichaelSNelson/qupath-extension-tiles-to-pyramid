package qupath.ext.basicstitching.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.basicstitching.stitching.TileMapping;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.SparseImageServer;
import qupath.lib.regions.ImageRegion;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Assembles a list of tile mappings into a SparseImageServer, applying metadata for pixel and z-spacing.
 */
public class ImageAssembler {
    private static final Logger logger = LoggerFactory.getLogger(ImageAssembler.class);

    /**
     * Build a SparseImageServer from the provided list of TileMapping objects.
     * @param mappings Tiles (with file, region, subdirName)
     * @param pixelSizeMicrons Pixel size for the final image (in microns)
     * @param zSpacingMicrons Z spacing for the final image (in microns)
     * @return SparseImageServer with correct metadata, or null on failure
     */
    public static SparseImageServer assemble(List<TileMapping> mappings, double pixelSizeMicrons, double zSpacingMicrons) throws IOException {
        if (mappings == null || mappings.isEmpty()) {
            logger.error("No tile mappings provided to assembler.");
            return null;
        }

        SparseImageServer.Builder builder = new SparseImageServer.Builder();
        int nTiles = 0;
        for (TileMapping mapping : mappings) {
            File file = mapping.file;
            ImageRegion region = mapping.region;
            if (file == null || region == null) {
                logger.warn("Null file or region in mapping, skipping...");
                continue;
            }
            var serverBuilders = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, file.toURI().toString()).getBuilders();
            if (serverBuilders.isEmpty()) {
                logger.error("No server builders for file: {}", file.getName());
                continue;
            }
            builder.jsonRegion(region, 1.0, serverBuilders.get(0));
            nTiles++;
        }

        logger.info("Assembled {} tiles into sparse image server.", nTiles);

        SparseImageServer server = builder.build();
        // Update server metadata
        ImageServerMetadata meta = new ImageServerMetadata.Builder(server.getMetadata())
                .pixelSizeMicrons(pixelSizeMicrons, pixelSizeMicrons)
                .zSpacingMicrons(zSpacingMicrons)
                .build();
        server.setMetadata(meta);

        return server;
    }
}
