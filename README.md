# Xed-Editor

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="96" height="96" />
</p>

<p align="center">
  <strong>A lightweight, powerful, and extensible code editor for Android.</strong>
</p>

<p align="center">
  <a href="https://github.com/abidhasansojib/Xed-Editor/actions/workflows/android.yml">
    <img src="https://github.com/abidhasansojib/Xed-Editor/actions/workflows/android.yml/badge.svg" alt="Build Status" />
  </a>
  <a href="https://github.com/abidhasansojib/Xed-Editor/releases">
    <img src="https://img.shields.io/github/v/release/abidhasansojib/Xed-Editor?style=flat-square" alt="Latest Release" />
  </a>
  <a href="https://github.com/abidhasansojib/Xed-Editor/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/abidhasansojib/Xed-Editor?style=flat-square" alt="License" />
  </a>
</p>

---

## ✨ Features

- 📝 **Advanced Code Editor**: Fast syntax highlighting powered by TextMate grammars and code intelligence via LSP (Language Server Protocol).
- 💻 **Integrated Terminal**: Full-featured interactive terminal supporting local Android shells, remote SSH connections, and custom commands.
- 📦 **Container & Droidspaces Integration**: Seamlessly browse, edit, and manage files inside Linux containers directly from your workspace.
- 🌐 **Offline Web Preview**: Instant, 100% offline HTML/CSS/JavaScript preview optimized for mobile devices with live reload.
- 📖 **Markdown Viewer**: Live Markdown rendering with fast switching between preview and editing modes.
- 🎨 **Modern Material 3 UI**: Clean Compose interface with customizable themes, AMOLED dark mode, and responsive layout.
- ⚡ **Lightweight & Fast**: Minimal memory footprint with smooth scrolling and instant tab switching.

---

## 📥 Download & Install

Download the latest APK directly from the GitHub Releases page:

👉 **[Download Latest Release](https://github.com/abidhasansojib/Xed-Editor/releases/latest)**

---

## 🛠️ Building from Source

### Prerequisites
- JDK 21
- Android SDK (API 35+)

### Build Steps

```bash
# Clone the repository
git clone https://github.com/abidhasansojib/Xed-Editor.git
cd Xed-Editor

# Build debug APK
./gradlew assembleDebug

# Or build release APK
./gradlew assembleRelease
```

Compiled APK files will be located at `app/build/outputs/apk/`.

---

## 🙏 Credits & Acknowledgements

This project is a continuation and enhanced edition based on the original [Xed-Editor](https://github.com/Rohitkushvaha01/Xed-Editor) created by [Rohit Kushvaha](https://github.com/Rohitkushvaha01) and contributors. Full credit and gratitude go to the original author and open-source community for laying the foundational work.

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
