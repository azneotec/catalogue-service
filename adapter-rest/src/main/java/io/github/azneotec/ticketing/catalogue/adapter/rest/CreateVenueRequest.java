package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.github.azneotec.ticketing.catalogue.application.port.in.CreateVenue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;

/** Body of {@code POST /admin/venues}. {@code zone} is an IANA id such as {@code Europe/London}. */
public record CreateVenueRequest(
    @NotBlank String name, @NotBlank String city, @NotNull ZoneId zone) {

  CreateVenue.Command toCommand() {
    return new CreateVenue.Command(name, city, zone);
  }
}
