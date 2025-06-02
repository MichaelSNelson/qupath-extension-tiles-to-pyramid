// =======================================================================================
// 1. BasicStitchingExtension.java
// =======================================================================================
package qupath.ext.basicstitching;

import javafx.scene.control.MenuItem;
import qupath.ext.basicstitching.functions.StitchingGUI;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * TODO: create public functions so that stitching can be run from command line or a script
 * CHECK: Always build AND publish to maven local for use with qp-scope
 * ./gradlew publishToMavenLocal
 */
public class BasicStitchingExtension implements QuPathExtension {

    // Instance variables - converted from Groovy property syntax
    private final String name = "Basic stitching";
    private final String description = "Basic stitching extension that puts tiles together into pyramidal ome.tif files, no overlap resolution or flat field correction.";
    private final Version quPathVersion = Version.parse("v0.5.0");

    @Override
    public void installExtension(QuPathGUI qupath) {
        addMenuItem(qupath);
    }

    /**
     * Get the description of the extension.
     *
     * @return The description of the extension.
     */
    @Override
    public String getDescription() {
        return "Stitch tiles into a pyramidal ome.tif";
    }

    /**
     * Get the name of the extension.
     *
     * @return The name of the extension.
     */
    @Override
    public String getName() {
        return "BasicStitching";
    }

    private void addMenuItem(QuPathGUI qupath) {
        var menu = qupath.getMenu("Extensions>" + name, true);
        var fileNameStitching = new MenuItem("Basic Stitching Extension");
        fileNameStitching.setOnAction(e -> {
            StitchingGUI.createGUI();
        });
        menu.getItems().add(fileNameStitching);
    }
}