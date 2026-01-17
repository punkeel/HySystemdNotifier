package com.punkeel.hysystemdnotifier;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public final class SystemdNotifierPlugin extends JavaPlugin {
  private static final long MSEC_PER_SEC = 1_000L;
  private static final long USEC_PER_MSEC = 1_000L;

  private static final long MAX_NOTIFY_INTERVAL = 10L * MSEC_PER_SEC;

  private SDNotify sdNotify;
  private long notifyInterval;

  private boolean sdNotifyEnabled;
  private WatchdogNotifier watchdogNotifier;

  public SystemdNotifierPlugin(@NonNullDecl JavaPluginInit init) {
    super(init);
  }

  @Override
  protected void setup() {
    getEventRegistry().registerGlobal(ShutdownEvent.class, this::onShutdown);

    if (!SDNotify.isPlatformSupported()) {
      getLogger()
          .atSevere()
          .log("Not running on an sd_notify-aware service manager. This plugin has no effect");
      return;
    }

    try {
      sdNotify = new SDNotify(getLogger().getSubLogger("SDNotify"));
    } catch (RuntimeException e) {
      getLogger()
          .atSevere()
          .withCause(e)
          .log(
              "Could not initialize sd_notify! If service manager expects notifications, the server may soon be considered unresponsive and killed!");
      return;
    }

    if (sdNotify.getWatchdogUsec() == 0) {
      getLogger()
          .atWarning()
          .log(
              "Not running through service manager or watchdog is not configured, hangs will not be detected!");
      return;
    }

    long watchdogInterval = Math.max(1, sdNotify.getWatchdogUsec() / USEC_PER_MSEC);
    notifyInterval = Math.min(MAX_NOTIFY_INTERVAL, watchdogInterval / 2);
    getLogger().atInfo().log("Watchdog timeout is " + watchdogInterval + " ms");

    sdNotifyEnabled = true;

    getLogger()
        .atInfo()
        .log(
            "We are pid %d - notifying service manager. Sending watchdog updates every %d ms",
            sdNotify.getPid(), notifyInterval);
    sdNotify.init(null);
  }

  @Override
  protected void start() {
    getLogger()
        .atInfo()
        .log("HySystemdNotifier start() called, sdNotifyEnabled=%d", sdNotifyEnabled);

    if (sdNotifyEnabled) {
      watchdogNotifier = new WatchdogNotifier(this.getLogger(), sdNotify);
      HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
          watchdogNotifier::tick, 1L, notifyInterval, TimeUnit.MILLISECONDS);
    }
  }

  private void onShutdown(ShutdownEvent event) {
    if (watchdogNotifier != null) {
      watchdogNotifier.onShutdown();
    }
  }

  @Override
  protected void shutdown() {
    if (sdNotify != null) {
      sdNotify.shutdown();
    }
  }
}
