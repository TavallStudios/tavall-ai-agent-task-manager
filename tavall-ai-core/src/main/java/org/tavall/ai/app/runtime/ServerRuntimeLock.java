package org.tavall.ai.app.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ServerRuntimeLock implements AutoCloseable {

  private final FileChannel channel;
  private final FileLock lock;
  private final Path lockPath;

  private ServerRuntimeLock(FileChannel channel, FileLock lock, Path lockPath) {
    this.channel = channel;
    this.lock = lock;
    this.lockPath = lockPath;
  }

  public static ServerRuntimeLock acquire(String label) {
    Path baseDir = Path.of(System.getProperty("user.home"), ".tavall-ai");
    try {
      Files.createDirectories(baseDir);
    } catch (IOException exception) {
      return null;
    }

    Path lockPath = baseDir.resolve("runtime.lock");
    try {
      FileChannel channel = FileChannel.open(
          lockPath,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE
      );
      FileLock lock = tryLock(channel);
      if (lock == null) {
        channel.close();
        return null;
      }
      writeLockInfo(channel, label == null ? "tavall-ai" : label);
      return new ServerRuntimeLock(channel, lock, lockPath);
    } catch (IOException exception) {
      return null;
    }
  }

  public Path lockPath() {
    return lockPath;
  }

  @Override
  public void close() {
    try {
      lock.release();
    } catch (IOException ignored) {
    }
    try {
      channel.close();
    } catch (IOException ignored) {
    }
  }

  private static FileLock tryLock(FileChannel channel) {
    try {
      return channel.tryLock();
    } catch (OverlappingFileLockException ignored) {
      return null;
    } catch (IOException exception) {
      return null;
    }
  }

  private static void writeLockInfo(FileChannel channel, String label) throws IOException {
    String payload = label + " pid=" + ProcessHandle.current().pid();
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    channel.truncate(0);
    channel.position(0);
    channel.write(ByteBuffer.wrap(bytes));
    channel.force(true);
  }
}
