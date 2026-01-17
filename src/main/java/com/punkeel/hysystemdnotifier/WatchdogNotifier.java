package com.punkeel.hysystemdnotifier;

import com.hypixel.hytale.common.util.java.ManifestUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;

final class WatchdogNotifier {
  private final HytaleLogger logger;
  private final SDNotify sdNotify;
  private boolean sentReady;

  WatchdogNotifier(HytaleLogger logger, SDNotify sdNotify) {
    this.logger = logger;
    this.sdNotify = sdNotify;
  }

  public void tick() {
    var status = buildStatus();
    if (!sentReady) {
      sdNotify.ready(status);
      sentReady = true;
    } else {
      sdNotify.watchdog(status);
    }
  }

  /** Handle server shutdown notification. */
  public void onShutdown() {
    logger.atInfo().log("Detected stop - notifying service manager");
    sdNotify.stopping(null);
  }

  /**
   * Obtain a status message describing the current state of the server.
   *
   * @return status message or empty if status cannot be determined
   */
  public String buildStatus() {
    try {
      var universe = Universe.get();
      var server = HytaleServer.get();

      int onlinePlayers = universe != null ? universe.getPlayerCount() : 0;
      int maxPlayers = server.getConfig().getMaxPlayers();

      String version = "HytaleServer " + ManifestUtil.getImplementationVersion();

      return "Running %s with %d/%d players".formatted(version, onlinePlayers, maxPlayers);
    } catch (RuntimeException e) {
      // Failsafe in case status gathering misbehaves (which would make us not send notifications!)
      logger.atSevere().withCause(e).log("Status gathering threw an exception, ignoring");
      return null;
    }
  }
}
