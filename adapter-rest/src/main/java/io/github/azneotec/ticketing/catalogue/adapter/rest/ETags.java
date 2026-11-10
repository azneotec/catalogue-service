package io.github.azneotec.ticketing.catalogue.adapter.rest;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * The event's version is its entity tag: {@code ETag: "3"}. A client sends it back in {@code
 * If-Match} to say "change it only if it is still the version I saw" (optimistic locking over
 * HTTP).
 */
final class ETags {

  /** One strong ETag holding a version number. Weak tags ({@code W/"3"}) never match If-Match. */
  private static final Pattern STRONG_VERSION = Pattern.compile("\"(\\d{1,18})\"");

  private ETags() {}

  static String of(long version) {
    return "\"" + version + "\"";
  }

  /**
   * The version named by an {@code If-Match} header.
   *
   * @throws org.springframework.web.ErrorResponseException 428 if the header is missing or {@code
   *     *} (which would match any version and defeat the check), 400 if it isn't one strong ETag
   */
  static long requiredVersion(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
      throw Problems.exception(
          HttpStatus.PRECONDITION_REQUIRED,
          "if-match-required",
          "If-Match required",
          "Send If-Match with the ETag you last read, e.g. If-Match: \"3\"");
    }
    var matcher = STRONG_VERSION.matcher(ifMatch.strip());
    if (!matcher.matches()) {
      throw Problems.exception(
          HttpStatus.BAD_REQUEST,
          "invalid-if-match",
          "Invalid If-Match",
          "If-Match must be one strong ETag as returned by this API, e.g. \"3\"");
    }
    return Long.parseLong(matcher.group(1));
  }
}
