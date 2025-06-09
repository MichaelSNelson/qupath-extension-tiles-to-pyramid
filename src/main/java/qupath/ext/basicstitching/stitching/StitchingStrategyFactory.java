package qupath.ext.basicstitching.stitching;

public class StitchingStrategyFactory {
    public static StitchingStrategy getStrategy(String name) {
        switch (name) {
            case "Filename[x,y] with coordinates in microns":
                return new FileNameStitchingStrategy();
            case "Vectra tiles with metadata":
                return new VectraMetadataStrategy();
            case "Coordinates in TileConfiguration.txt file":
                return new TileConfigurationTxtStrategy();
            default:
                throw new IllegalArgumentException("Unknown stitching strategy: " + name);
        }
    }
}
