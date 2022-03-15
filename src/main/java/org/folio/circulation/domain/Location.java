package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import lombok.NonNull;

public class Location {
  private final JsonObject representation;
  private final JsonObject libraryRepresentation;
  private final JsonObject campusRepresentation;
  private final @NonNull Institution institution;

  private final ServicePoint primaryServicePoint;

  public Location(JsonObject representation,
    JsonObject libraryRepresentation, JsonObject campusRepresentation,
    Institution institution, ServicePoint primaryServicePoint) {

    this.representation = representation;
    this.libraryRepresentation = libraryRepresentation;
    this.campusRepresentation = campusRepresentation;
    this.institution = institution;
    this.primaryServicePoint = primaryServicePoint;
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  private List<String> getServicePointIds() {
    return getArrayProperty(representation, "servicePointIds")
      .stream()
      .map(String.class::cast)
      .collect(Collectors.toList());
  }

  public UUID getPrimaryServicePointId() {
    return Optional.ofNullable(getProperty(representation, "primaryServicePoint"))
      .map(UUID::fromString)
      .orElse(null);
  }

  public boolean homeLocationIsServedBy(UUID servicePointId) {
    //Defensive check just in case primary isn't part of serving set
    return matchesPrimaryServicePoint(servicePointId) ||
      matchesAnyServingServicePoint(servicePointId);
  }

  public String getName() {
    return getProperty(representation, "name");
  }

  public String getLibraryId() {
    return getProperty(representation, "libraryId");
  }

  public String getCampusId() {
    return getProperty(representation, "campusId");
  }

  public String getInstitutionId() {
    return institution.getId();
  }

  public String getCode() {
    return getProperty(representation, "code");
  }

  public String getLibraryName() {
    return getProperty(libraryRepresentation, "name");
  }

  public String getCampusName() {
    return getProperty(campusRepresentation, "name");
  }

  public String getInstitutionName() {
    return institution.getName();
  }

  public ServicePoint getPrimaryServicePoint() {
    return primaryServicePoint;
  }

  public Location withLibraryRepresentation(JsonObject libraryRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation,
      institution, primaryServicePoint);
  }

  public Location withCampusRepresentation(JsonObject campusRepresentation) {
    return new Location(representation, libraryRepresentation, campusRepresentation,
      institution, primaryServicePoint);
  }

  public Location withPrimaryServicePoint(ServicePoint servicePoint) {
    return new Location(representation, libraryRepresentation, campusRepresentation,
      institution, servicePoint);
  }

  public Location withInstitution(Institution institution) {
    return new Location(representation, libraryRepresentation, campusRepresentation,
      institution, primaryServicePoint);
  }

  private boolean matchesPrimaryServicePoint(UUID servicePointId) {
    return Objects.equals(getPrimaryServicePointId(), servicePointId);
  }

  private boolean matchesAnyServingServicePoint(UUID servicePointId) {
    return getServicePointIds().stream()
      .map(UUID::fromString)
      .anyMatch(id -> Objects.equals(servicePointId, id));
  }

  @Override
  public String toString() {
    return String.format("Institution: `%s`, Campus: `%s`, Library: `%s` Location: `%s`",
      getInstitutionId(), getCampusId(), getLibraryId(), getId());
  }
}
