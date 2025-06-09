# QuPath Tiles-to-Pyramid Extension: Workflow Overview

This document describes the complete workflow for stitching tiled images into a pyramidal OME-TIFF using QuPath, particularly using the `TileConfigurationTxt` strategy.

## Workflow Steps

### 1. **MenuStartup** (Entry Point)

File: `MenuStartup.java`

Registers a new menu option in QuPath:

```java
fileNameStitching.setOnAction(e -> {
    logger.info("GUI menu click detected");
    StitchingGUI.createGUI();
});
```

### 2. **StitchingGUI** (User Dialog)

File: `StitchingGUI.java`

Displays a dialog to collect parameters (e.g., tile folder, compression, pixel size):

```java
String finalImageName = StitchingWorkflow.run(
    stitchingType,
    folderPath,
    outputPath,
    compressionType,
    pixelSize,
    downsample,
    matchingString,
    1.0,
    null
);
```

### 3. **StitchingWorkflow** (Orchestrator)

File: `StitchingWorkflow.java`

Executes the workflow logic:

```java
StitchingStrategy strategy = StitchingStrategyFactory.getStrategy(stitchingType);
List<TileMapping> mappings = strategy.prepareStitching(folderPath, pixelSizeMicrons, baseDownsample, matchingString);
SparseImageServer server = ImageAssembler.assemble(mappings, pixelSizeMicrons, zSpacingMicrons);
String written = PyramidImageWriter.write(server, outputPath, outBase, compressionType, baseDownsample);
```

### 4. **TileConfigurationTxtStrategy** (Mapping Tiles)

File: `TileConfigurationTxtStrategy.java`

- Parses `TileConfiguration.txt`.
- Matches TIFF files to tile configuration entries.
- Creates `TileMapping` objects.

### 5. **ImageAssembler** (Building Stitched Image)

File: `ImageAssembler.java`

Converts tile mappings into a stitched `SparseImageServer`:

```java
SparseImageServer server = ImageAssembler.assemble(mappings, pixelSizeMicrons, zSpacingMicrons);
```

### 6. **PyramidImageWriter** (OME-TIFF Output)

File: `PyramidImageWriter.java`

Writes the assembled image as a pyramidal OME-TIFF:

```java
String written = PyramidImageWriter.write(
    server,
    outputPath,
    outBase,
    compressionType,
    baseDownsample
);
```

## Workflow Call Sequence

```
MenuStartup (menu click)
├─ StitchingGUI (collect input)
│  └─ processDialogResult
│     └─ StitchingWorkflow.run()
│        ├─ StitchingStrategyFactory.getStrategy()
│        ├─ TileConfigurationTxtStrategy.prepareStitching()
│        ├─ ImageAssembler.assemble()
│        └─ PyramidImageWriter.write()
└─ Final output: OME-TIFF file
```

## Component Responsibilities

| Component                  | Responsibility                        |
|----------------------------|---------------------------------------|
| **MenuStartup**            | Menu entry, GUI initialization        |
| **StitchingGUI**           | User input dialog                     |
| **StitchingWorkflow**      | Workflow orchestration                |
| **StitchingStrategyFactory**| Strategy selection                    |
| **TileConfigurationTxtStrategy** | Mapping tiles via configuration file|
| **ImageAssembler**         | Image server assembly                 |
| **PyramidImageWriter**     | Writing the pyramidal OME-TIFF        |

## Extending the Workflow

- **New strategies:** Implement `StitchingStrategy`.
- **Custom writers:** Substitute `PyramidImageWriter`.
- **Integration:** Callable from GUI, CLI, or scripting.

---

For detailed examples and documentation, refer to the [GitHub repository](https://github.com/MichaelSNelson/qupath-extension-tiles-to-pyramid).
