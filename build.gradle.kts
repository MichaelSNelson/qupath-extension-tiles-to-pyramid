plugins {
    // Support writing the extension in Groovy (remove this if you don't want to)
    groovy
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
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
// TODO: Configure your extension here (please change the defaults!)
qupathExtension {
    name = "qupath-extension-tiles-to-pyramid"
    group = "io.github.michaelsnelson"
    version = "0.1.0"
    description = "Convert tiles into a pyramidal ome.tif"
    automaticModule = "io.github.qupath.michaelsnelson.extension.tiles-to-pyramid"
}

// TODO: Define your dependencies here
dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    // Add both implementation AND shadow for bioformats
    implementation("io.github.qupath:qupath-extension-bioformats:0.6.0-rc4")
    shadow("io.github.qupath:qupath-extension-bioformats:0.6.0-rc4")

    // Add Bio-Formats explicitly for compile time
    implementation("ome:formats-gpl:7.1.0")
    shadow("ome:formats-gpl:7.1.0")
    // Add Bio-Formats explicitly
    //shadow("ome:formats-gpl:7.1.0")
    // If you aren't using Groovy, this can be removed
    shadow(libs.bundles.groovy)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}
