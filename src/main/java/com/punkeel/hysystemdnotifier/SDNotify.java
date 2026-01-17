package com.punkeel.hysystemdnotifier;

import com.hypixel.hytale.logger.HytaleLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.EpollDomainDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainDatagramPacket;
import io.netty.channel.unix.DomainSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class SDNotify {
  private final long pid;
  private final long watchdogUsec;
  private final DomainSocketAddress socketAddress;

  @SuppressWarnings("deprecation")
  // NIO doesn't support Abstract UDS, which is required to communicate with the notify socket
  private final EpollEventLoopGroup eventLoopGroup;

  private final Channel channel;
  private final HytaleLogger logger;

  public SDNotify(HytaleLogger logger) {
    this.logger = logger;
    pid = ProcessHandle.current().pid();

    String socketPath = System.getenv("NOTIFY_SOCKET");
    if (socketPath == null || socketPath.isEmpty()) {
      throw new IllegalStateException("NOTIFY_SOCKET environment variable not set");
    }

    if (socketPath.startsWith("@")) {
      socketPath = "\0" + socketPath.substring(1);
    }
    this.socketAddress = new DomainSocketAddress(socketPath);

    this.eventLoopGroup = new EpollEventLoopGroup();

    try {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap
          .group(eventLoopGroup)
          .channel(EpollDomainDatagramChannel.class)
          .handler(
              new ChannelInitializer<EpollDomainDatagramChannel>() {
                @Override
                protected void initChannel(EpollDomainDatagramChannel ch) {}
              });

      this.channel = bootstrap.bind(new DomainSocketAddress("")).sync().channel();
    } catch (Exception e) {
      eventLoopGroup.shutdownGracefully();
      throw new RuntimeException("Failed to create Netty channel for sd_notify", e);
    }

    long watchdogUsecValue = 0;
    String watchdogPid = System.getenv("WATCHDOG_PID");
    String watchdogUsecEnv = System.getenv("WATCHDOG_USEC");

    if (watchdogPid != null && watchdogUsecEnv != null) {
      try {
        long watchdogPidValue = Long.parseLong(watchdogPid);
        if (watchdogPidValue == pid) {
          watchdogUsecValue = Long.parseLong(watchdogUsecEnv);
        }
      } catch (NumberFormatException ignored) {
      }
    }

    this.watchdogUsec = watchdogUsecValue;
  }

  private void sendNotification(String message) {
    if (channel == null || !channel.isActive()) {
      logger.atWarning().log("Channel not active, cannot send sd_notify");
      return;
    }

    DomainDatagramPacket packet =
        new DomainDatagramPacket(
            Unpooled.copiedBuffer(message, StandardCharsets.UTF_8), socketAddress);

    channel
        .writeAndFlush(packet)
        .addListener(
            future -> {
              if (!future.isSuccess()) {
                logger.atWarning().log("Failed to send sd_notify: ", future.cause());
              }
            });
  }

  public void shutdown() {
    if (channel != null) {
      channel.close();
    }
    eventLoopGroup.shutdownGracefully(0, 100, TimeUnit.MILLISECONDS);
  }

  public static boolean isPlatformSupported() {
    String socketVar = System.getenv("NOTIFY_SOCKET");
    return socketVar != null && !socketVar.isEmpty();
  }

  public long getPid() {
    return pid;
  }

  public long getWatchdogUsec() {
    return watchdogUsec;
  }

  public void init(String status) {
    String message =
        String.format(
            "MAINPID=%d\nNOTIFYACCESS=main\nSTATUS=%s", pid, status == null ? "Loading" : status);
    sendNotification(message);
  }

  public void ready(String status) {
    String message = String.format("READY=1\nSTATUS=%s", status == null ? "Running" : status);
    sendNotification(message);
  }

  public void watchdog(String status) {
    String message = String.format("WATCHDOG=1\nSTATUS=%s", status == null ? "Running" : status);
    sendNotification(message);
  }

  public void stopping(String status) {
    String message = String.format("STOPPING=1\nSTATUS=%s", status == null ? "Stopping" : status);
    sendNotification(message);
  }
}
