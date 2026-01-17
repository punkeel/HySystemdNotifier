# HySystemdNotifier

A lightweight Hytale server plugin that integrates with systemd for proper service management and watchdog functionality.

## What it does

- Notifies systemd when the server is fully started (`Type=notify`)
- Sends periodic watchdog signals to prevent systemd from killing a healthy server
- Reports server status to systemd for monitoring

## Installation

1. Download `HySystemdNotifier-*.jar` from releases
2. Place the JAR in your Hytale server's `mods/` directory
3. Restart the server

## Systemd Configuration

Update your Hytale systemd service file:

```
[Unit]
Description=Hytale Server
After=network.target

[Service]
User=hytale
WorkingDirectory=/path/to/hytale
ExecStart=java -jar ...
Restart=always

# Add this:
Type=notify
WatchdogSec=10
TimeoutStartSec=300
TimeoutStopSec=120

[Install]
WantedBy=multi-user.target
```

## Key Settings

- `Type=notify`: Server sends READY notification when started
- `TimeoutStartSec=300`: Kill server if startup takes longer than 5 minutes
- `WatchdogSec=10`: Kill server if no watchdog signal for 10 seconds
- `TimeoutStopSec=120`: Kill server if shutdown takes longer than 2 minutes

## Building

Requires Java 25+, like Hytale. Use the provided Gradle wrapper:

```
./gradlew build
```

## Credits

This project is heavily inspired by [MCSDNotifier](https://github.com/AgentOak/MCSDNotifier), which provides similar systemd integration for Minecraft servers.

## License

LGPL-3.0-or-later
