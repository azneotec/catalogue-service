package io.github.azneotec.ticketing.catalogue.adapter.grpc;

import io.github.azneotec.ticketing.catalogue.adapter.grpc.GrpcErrors.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque page tokens (Google API design guide): the client only passes them back, so the server can
 * later switch from page numbers to keyset cursors without changing the contract.
 */
final class PageTokens {

  private static final String PREFIX = "page:";

  private PageTokens() {}

  static String of(int page) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((PREFIX + page).getBytes(StandardCharsets.UTF_8));
  }

  /** Page number in a token; an empty token is the first page. */
  static int page(String token) {
    if (token.isEmpty()) {
      return 0;
    }
    try {
      var decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      if (decoded.startsWith(PREFIX)) {
        var page = Integer.parseInt(decoded.substring(PREFIX.length()));
        if (page >= 0) {
          return page;
        }
      }
    } catch (IllegalArgumentException malformed) {
      // NumberFormatException is an IllegalArgumentException too; both fall through.
    }
    throw new InvalidRequestException("page_token is not a token returned by this service");
  }
}
