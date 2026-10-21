package net.spidiboost.muhashist.updates;

import java.io.*;
import java.net.*;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

public final class GitHubDownload {
  private static final Set<String> HOSTS =
      Set.of(
          "github.com",
          "release-assets.githubusercontent.com",
          "objects.githubusercontent.com",
          "github-releases.githubusercontent.com");
  private final Connections connections;
  private final int connectTimeout, readTimeout, totalTimeout;

  @FunctionalInterface
  interface Connections {
    HttpsURLConnection open(URI address) throws IOException;
  }

  public GitHubDownload() {
    this(address -> (HttpsURLConnection) address.toURL().openConnection(), 10000, 10000, 45000);
  }

  GitHubDownload(Connections connections, int connectTimeout, int readTimeout, int totalTimeout) {
    this.connections = connections;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.totalTimeout = totalTimeout;
  }

  public byte[] get(URI address, int limit) throws IOException {
    if (limit < 1) throw new IllegalArgumentException("Invalid download limit");
    long deadline = System.nanoTime() + totalTimeout * 1_000_000L;
    for (int redirects = 0; redirects <= 5; redirects++) {
      if (!trusted(address)) throw new IOException("Unexpected release download host");
      checkInterrupted();
      var connection = connections.open(address);
      try {
        connection.setConnectTimeout(remaining(deadline, connectTimeout));
        connection.setReadTimeout(remaining(deadline, readTimeout));
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "MuhaShist-Updater");
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Accept-Encoding", "identity");
        int status = connection.getResponseCode();
        remaining(deadline, readTimeout);
        if (Set.of(301, 302, 303, 307, 308).contains(status)) {
          String location = connection.getHeaderField("Location");
          if (location == null || location.isBlank()) throw new IOException("Missing redirect");
          try {
            address = address.resolve(location);
          } catch (IllegalArgumentException e) {
            throw new IOException("Invalid release redirect", e);
          }
          continue;
        }
        if (status != 200) throw new IOException("GitHub release HTTP " + status);
        long length = connection.getContentLengthLong();
        if (length > limit) throw new IOException("Release download exceeds size limit");
        try (var input = connection.getInputStream();
            var output = new ByteArrayOutputStream((int) Math.max(0, Math.min(length, 65536)))) {
          byte[] buffer = new byte[Math.min(limit, 16384)];
          while (true) {
            checkInterrupted();
            connection.setReadTimeout(remaining(deadline, readTimeout));
            int count = input.read(buffer);
            if (count < 0) break;
            if (count > limit - output.size())
              throw new IOException("Release download exceeds size limit");
            output.write(buffer, 0, count);
          }
          remaining(deadline, readTimeout);
          if (length >= 0 && output.size() != length) throw new EOFException("Incomplete release");
          return output.toByteArray();
        }
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("Too many release redirects");
  }

  private static void checkInterrupted() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted())
      throw new InterruptedIOException("Download stopped");
  }

  private static int remaining(long deadline, int maximum) throws SocketTimeoutException {
    long nanos = deadline - System.nanoTime();
    if (nanos <= 0) throw new SocketTimeoutException("Release download timed out");
    return (int) Math.min(maximum, Math.max(1, nanos / 1_000_000));
  }

  public static boolean trusted(URI uri) {
    return "https".equalsIgnoreCase(uri.getScheme())
        && uri.getHost() != null
        && HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))
        && uri.getUserInfo() == null
        && uri.getFragment() == null
        && (uri.getPort() == -1 || uri.getPort() == 443);
  }
}
