plugins {
    // Support writing the extension in Groovy (remove this if you don't want to)
    groovy
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
    id("maven-publish")
    // Platform detection for native library support (Blosc compression for ZARR)
    id("com.google.osdetector") version "1.7.3"
}

//Required for working with qupath-extension-qpsc in IntelliJ, allowing import statements to work
//Build this with gradle, then use publishToMavenLocal in order for imports like
//import qupath.ext.basicstitching.config.StitchingConfig;
//import qupath.ext.basicstitching.workflow.StitchingWorkflow;
//to work
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.michaelsnelson"
            artifactId = "qupath-extension-tiles-to-pyramid"
            version = "0.1.0"

            from(components["java"])
        }
    }
}
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.scijava.org/content/repositories/releases")
    }
    maven {
        url = uri("https://artifacts.openmicroscopy.org/artifactory/maven/")
    }
}

qupathExtension {
    name = "qupath-extension-tiles-to-pyramid"
    group = "io.github.michaelsnelson"
    version = "0.1.0"
    description = "Convert tiles into pyramidal OME-TIFF or OME-ZARR formats"
    automaticModule = "io.github.qupath.michaelsnelson.extension.tiles-to-pyramid"
}

dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // bioformats plugin needed for OMEPyramidWriter and OMEZarrWriter
    // Note: qupath-extension-bioformats 0.6.0+ includes ZARR support with transitive dependencies:
    //   - OMEZarrReader, JZarr, Blosc compression, JNA for native libraries
    implementation("io.github.qupath:qupath-extension-bioformats:0.6.0-rc4")

    // Add Bio-Formats explicitly for compile time to avoid "class file for loci.formats.FormatException not found"
    shadow("ome:formats-gpl:7.1.0")

    // If you aren't using Groovy, this can be removed
    shadow(libs.bundles.groovy)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}
