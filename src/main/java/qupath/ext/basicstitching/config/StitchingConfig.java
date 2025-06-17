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
    public final double xFudgeFactor;
    public final double yFudgeFactor;

    /**
     * Full constructor with all parameters including fudge factors.
     * Used primarily for Vectra workflows that need fudge factor adjustments.
     */
    public StitchingConfig(
            String stitchingType,
            String folderPath,
            String outputPath,
            String compressionType,
            double pixelSizeInMicrons,
            double baseDownsample,
            String matchingString,
            double zSpacingMicrons,
            double xFudgeFactor,
            double yFudgeFactor
    ) {
        this.stitchingType = stitchingType;
        this.folderPath = folderPath;
        this.outputPath = outputPath;
        this.compressionType = compressionType;
        this.pixelSizeInMicrons = pixelSizeInMicrons;
        this.baseDownsample = baseDownsample;
        this.matchingString = matchingString;
        this.zSpacingMicrons = zSpacingMicrons;
        this.xFudgeFactor = xFudgeFactor;
        this.yFudgeFactor = yFudgeFactor;
    }

    /**
     * Constructor without fudge factors for backward compatibility.
     * Sets fudge factors to 1.0 (no adjustment).
     */
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
        this(stitchingType, folderPath, outputPath, compressionType,
                pixelSizeInMicrons, baseDownsample, matchingString,
                zSpacingMicrons, 1.0, 1.0);
    }
}