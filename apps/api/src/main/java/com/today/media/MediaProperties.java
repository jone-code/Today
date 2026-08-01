package com.today.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "today.media")
public class MediaProperties {

  /** Absolute or relative directory for uploaded media files. */
  private String root = "data/media";

  /** Max upload size in bytes (default 10 MiB). */
  private long maxBytes = 10L * 1024 * 1024;

  public String getRoot() {
    return root;
  }

  public void setRoot(String root) {
    this.root = root;
  }

  public long getMaxBytes() {
    return maxBytes;
  }

  public void setMaxBytes(long maxBytes) {
    this.maxBytes = maxBytes;
  }
}
