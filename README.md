# Brother HL-6180DW Print Service for Android

A modern, open-source Android Print Service plugin designed for the **Brother HL-6180DW** laser printer. Built with Kotlin, Jetpack Compose, and Material 3, supporting direct printing over **IPP (Internet Printing Protocol)**.

> **Disclaimer:** This is an unofficial, third-party open-source project and is **not** affiliated with, authorized, maintained, or endorsed by Brother Industries, Ltd.

---

## Features

- **Native Android Print Service:** Integrated directly into the system-level Android print menu.
- **Hidden Launcher Icon:** Runs as a clean background service plugin without cluttering your home screen or app drawer.
- **IPP Protocol Support:** Sends PDF documents directly to the printer over IPP (Port 80 / 631).
- **Subnet Auto-Discovery:** Automatically scans your local `/24` Wi-Fi subnet to discover connected printers.
- **Modern Material 3 UI:** Clean dark theme UI with Jetpack Compose cards and dropdowns.
- **Advanced IPP Attributes:** Supports Duplex, Paper Trays, Resolution (300/600/1200 DPI), and Toner Density/Darkness (Eco/Normal/Dark).
- **Wi-Fi Network Binding:** Ensures local network connections bypass system proxies or mobile data routing.
- **Localization:** Full support for German (`de`) and English (`en`).
- **Built-in Diagnostics:**
  - Connection test (Get-Printer-Attributes).
  - One-click Test Print page.
  - Shortcut to open the printer's web management interface.

---

## Technical Specifications

| Feature | Details |
| :--- | :--- |
| **Minimum SDK** | Android 10 (API 29) |
| **Target SDK** | Android 14 (API 34) / Compiled against Android 15 (API 37) |
| **Protocol** | IPP 1.1 / HTTP |
| **Supported Ports** | Port 80 (HTTP/IPP), Port 631 (IPP) |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Language** | Kotlin 2.x |

---

## Getting Started

1. Download and install the APK on your Android device.
2. In Android System Settings, navigate to **Connected devices > Connection preferences > Printing** (or search for *"Printing"* in Android Settings).
3. Tap **Brother HL-6180DW** and turn the service **On**.
4. Tap **Printer Settings** (or the gear icon) to configure:
   - Printer IP address (e.g. `192.168.10.160`) and Port (`80` or `631`).
   - Default Duplex mode, Paper Tray, Resolution, and Toner Density.
   - Test Connection or run a **Drucktest**.
5. Print any document or image from any Android app (Chrome, Gallery, PDF Viewer) via the standard system **Print** action.

---

## Troubleshooting & Port Configuration

Most Brother HL-6180DW printers have Port 9100 (RAW TCP) closed by default in network configurations, but expose IPP on **Port 80** or **Port 631**.

- **Port 80 (HTTP / IPP):** Standard web management and IPP path `/ipp`.
- **Port 631 (IPP):** Standard IPP printing port.
- If you encounter connection timeouts, ensure:
  - Your phone is connected to the same Wi-Fi network as the printer.
  - Nearby Devices / Location permissions are granted to allow local network access on Android 13+.

---

## License

Distributed under the [MIT License](LICENSE).
