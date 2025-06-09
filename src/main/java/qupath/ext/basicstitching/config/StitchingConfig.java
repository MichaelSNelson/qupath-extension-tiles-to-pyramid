package qupath.ext.basicstitching.config;

public class StitchingConfig {
    public final String stitchingType;
    public final String folderPath;
    public final String outputPath;
    public final String compressionType;
    public final double pixelSizeInMicrons;
    public final double baseDownsample;
    public final String matchingString;
    public final double zSpacingMicrons;

    public StitchingConfig(
            String stitchingType,
            String folderPath,
            String outputPath,
            String compressionType,
            double pixelSizeInMicrons,
            double baseDownsample,
            String matchingString,
            double zSpacingMicrons
    ) {
        this.stitchingType = stitchingType;
        this.folderPath = folderPath;
        this.outputPath = outputPath;
        this.compressionType = compressionType;
        this.pixelSizeInMicrons = pixelSizeInMicrons;
        this.baseDownsample = baseDownsample;
        this.matchingString = matchingString;
        this.zSpacingMicrons = zSpacingMicrons;
    }
}
