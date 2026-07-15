# 🚀 AR Test – Augmented Reality Model Placement Engine

> **A modern proof-of-concept Android AR application demonstrating real-time 3D model placement with Google ARCore and Filament.**

---

## ✨ What Makes This Unique

This isn't just another AR demo. **AR Test** is built on cutting-edge technology that prioritizes **stability, precision, and real-time performance**:

### 🎯 Anchor-Based Placement
- **No Drift**: Models attach to ARCore Anchors, not fixed world coordinates
- **Self-Correcting**: ARCore continuously refines its room map every frame, keeping objects perfectly planted
- **Walk Around Confidently**: As you move through the space, models stay exactly where you placed them

### 🎨 Flexible 3D Asset Pipeline
- **Drop & Play**: Throw `.glb` files into `assets/models/` – they automatically appear in the app
- **No Rebuilding**: Add new models without code changes
- **Optimized Rendering**: Filament memory-maps GLB files directly from the APK for maximum performance

### 📱 Enterprise-Grade Architecture
- **ARCore Required**: Play Store will only serve this app to ARCore-capable devices
- **OpenGL ES 3.0+**: Guaranteed GPU support for smooth 60fps rendering
- **Modern Android Stack**: Built with Kotlin, AndroidX, Material Design 3
- **Proper Permission Handling**: Intelligent camera permission flow with fallback options

---

## 🛠️ Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| **ARCore** | 1.48.0 | Google's spatial computing framework |
| **ARSceneView** | 2.3.0 | Kotlin wrapper for ARCore + Filament |
| **Filament** | Latest | Real-time 3D rendering engine |
| **Kotlin** | 17+ | Modern Android development |
| **AndroidX** | Latest | Backward-compatible Android components |
| **Material Design 3** | 1.12.0 | Modern UI components |

---

## 🎮 Features

### Core Functionality
- ✅ **Real-time plane detection** – Automatically identifies floor surfaces
- ✅ **Tap-to-place** – Place 3D models on detected planes with a single tap
- ✅ **Model selection** – Bottom sheet UI to choose from available 3D models
- ✅ **Clear all** – Instantly remove all placed models
- ✅ **Tracking feedback** – Visual indicators for AR tracking state

### User Experience
- ✅ **Portrait-locked** – Optimized for single-handed AR interaction
- ✅ **Responsive bottom sheet** – Smooth model selection interface
- ✅ **Smart permission flow** – Handles both new and permanent permission denials
- ✅ **Failure reason reporting** – Tells users why tracking failed and how to fix it

---

## 🚀 Getting Started

### Prerequisites
- Android device with ARCore support (Android 5.0+, API 24+)
- Android Studio 2024.1 or later
- Gradle 8.0+
- Java/Kotlin 17+

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/ar_test.git
cd ar_test

# Build and run
./gradlew installDebug

# Or use Android Studio
# File → Open → Select the ar_test folder
```

### Adding 3D Models

The app automatically loads all `.glb` files from the `assets/models/` directory:

```
app/src/main/assets/models/
├── model1.glb
├── model2.glb
└── model3.glb
```

Simply add `.glb` files and rebuild. They'll appear in the model picker automatically.

---

## 📖 How It Works

### The AR Anchor System

Unlike naive AR implementations that place models at fixed coordinates, **AR Test uses ARCore Anchors**:

```kotlin
// Models are attached to anchors, not raw positions
val anchorNode = AnchorNode(anchor).apply {
    addChild(ModelNode(...).apply { model = ... })
}
```

**Why this matters:**
- ARCore's real-time mapping continuously improves
- Each frame, the system re-corrects anchor positions based on the improved map
- Result: Objects stay planted to the floor, even as you walk around

### Plane Detection

The app listens for floor plane detection:

```kotlin
arSceneView.onFrame { frame ->
    val planes = frame.getUpdatedTrackables(Plane::class.java)
    // Place models on detected planes
}
```

### Interactive Placement

Tap anywhere on a detected plane to place your selected model:

```kotlin
arSceneView.onTapAr { hitResult: HitResult ->
    placeModel(hitResult)
}
```

---

## 🎯 Project Structure

```
ar_test/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/artest/
│   │   │   └── MainActivity.kt          # AR logic, plane detection, model placement
│   │   ├── res/
│   │   │   ├── layout/                  # UI layouts (bottom sheets, buttons)
│   │   │   ├── values/                  # Strings, colors, styles
│   │   │   └── drawable/                # Icons and assets
│   │   └── assets/models/               # Drop your .glb files here
│   └── build.gradle.kts                 # Dependencies and build config
├── gradle/                              # Gradle wrapper
└── README.md                            # This file
```

---

## 🎓 Learning Resources

- **[ARCore Documentation](https://developers.google.com/ar/develop)** – Official ARCore guide
- **[Filament Rendering Engine](https://google.github.io/filament/manual/index.html)** – Real-time rendering
- **[ARSceneView GitHub](https://github.com/SceneView/sceneview-android)** – Kotlin wrapper for ARCore
- **[Material Design 3](https://m3.material.io/)** – Modern Android UI patterns

---

## 📋 Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **ARCore Support**: Required (Play Store enforces this)
- **Camera Permission**: Required for AR functionality
- **OpenGL ES**: 3.0 or higher

---

## 🔧 Configuration

### Enabling ARCore as Required

The app is configured to require ARCore. This is set in `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.ar.core"
    android:value="required"
    tools:replace="android:value" />
```

The Play Store will only serve this app to devices with ARCore support.

### GLB Asset Optimization

GLB files are NOT compressed in the APK to allow Filament to memory-map them:

```kotlin
// In build.gradle.kts
androidResources {
    noCompress += "glb"
}
```

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| **"Tracking not available"** | Ensure good lighting and visible features (not blank walls) |
| **Models drift away** | Models are anchored correctly – this is usually poor lighting or reflective surfaces |
| **App crashes on startup** | Ensure camera permission is granted in Settings |
| **Models don't appear** | Check that `.glb` files are in `assets/models/` and rebuild |
| **Low FPS** | Reduce model complexity or check device hardware specs |

---

## 📄 Version & Status

- **Version**: 0.1-poc (Proof of Concept)
- **Status**: Active Development
- **Stability**: Production-ready for basic use cases

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-ar-feature`)
3. Commit your changes (`git commit -m 'Add amazing AR feature'`)
4. Push to your branch (`git push origin feature/amazing-ar-feature`)
5. Open a Pull Request

---

## 📝 License

This project is licensed under the MIT License – see the LICENSE file for details.

---

## 📞 Support

For issues, questions, or suggestions:
- **GitHub Issues**: [Create an issue](https://github.com/yourusername/ar_test/issues)
- **Discussions**: [Join the conversation](https://github.com/yourusername/ar_test/discussions)

---

**Built with ❤️ using ARCore, Filament, and Kotlin** | [View on GitHub](https://github.com/yourusername/ar_test)
