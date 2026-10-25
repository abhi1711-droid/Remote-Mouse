# Remote Mouse

Remote Mouse runs a Spring Boot server on your computer and provides a browser-based touchpad, keyboard, and media remote for a phone on the same local network. The phone communicates with the server over a WebSocket; the server uses Java AWT `Robot` to send mouse and keyboard input to the computer.

## Features

- Touchpad gestures for cursor movement, clicking, dragging, and scrolling
- On-screen keyboard with modifier and navigation keys
- Media controls for playback and volume
- Automatic reconnect when the WebSocket connection drops
- LAN address discovery and a QR code for connecting a phone

## Requirements

- Java 17 JDK
- A graphical desktop session (the server uses AWT `Robot`)
- Phone and computer connected to the same Wi-Fi/LAN

The Gradle wrapper downloads Gradle and dependencies on the first build, so internet access is needed then.

### Java configuration

This repository's `gradle.properties` currently contains a Java home path from a specific macOS development machine. Before building on another computer, change `org.gradle.java.home` there to your local Java 17 JDK path, or remove that line and set `JAVA_HOME` to Java 17.

For example, on Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Use the actual JDK install directory on your machine.

## Build

From the project directory:

```powershell
.\gradlew.bat build
```

On macOS or Linux, use `./gradlew build`.

## Run

Start the Spring Boot server:

```powershell
.\gradlew.bat bootRun
```

On macOS or Linux:

```bash
./gradlew bootRun
```

The server prints its LAN URL at startup. The default port is `8080`. Stop the server with `Ctrl+C`.

## Connect a phone

1. Keep the server running on the computer.
2. Open the printed LAN URL (for example, `http://192.168.1.20:8080`) on the phone. The phone and computer must be on the same local network.
3. Allow the Java process through the computer's firewall for private networks if prompted.

Only one browser client can control the computer at a time. Close the remote page on other devices (including a desktop browser tab) before connecting the phone; otherwise the new connection will be rejected and retry.

On macOS, grant Accessibility access to the terminal or IDE running Java under **System Settings → Privacy & Security → Accessibility** so `Robot` can control mouse and keyboard input.

## HTTP and WebSocket endpoints

- `GET /` — remote-control web page
- `GET /api/connection-info` — server LAN address, port, URL, and screen dimensions
- `GET /api/qr-code` — QR code PNG for the connection URL
- `WS /ws` — real-time control connection

## Project layout

```text
src/main/java/com/remotemouse/       Spring Boot server and input handling
src/main/resources/static/           Browser client (HTML, CSS, and JavaScript)
```
