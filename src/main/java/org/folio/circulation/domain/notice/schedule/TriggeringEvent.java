package org.folio.circulation.domain.notice.schedule;

import java.util.Arrays;
import java.util.Objects;

import org.folio.circulation.domain.notice.NoticeEventType;

public enum TriggeringEvent {

  HOLD_EXPIRATION("Hold expiration"),
  REQUEST_EXPIRATION("Request expiration"),
  DUE_DATE("Due date"),
  OVERDUE_FINE_RETURNED("Overdue fine returned"),
  OVERDUE_FINE_RENEWED("Overdue fine renewed"),
  AGED_TO_LOST("Aged to lost"),
  AGED_TO_LOST_FINE_CHARGED("Aged to lost - fine charged"),
  AGED_TO_LOST_RETURNED("Aged to lost & item returned - fine adjusted");

  private final String representation;

  TriggeringEvent(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static TriggeringEvent from(String value) {
    return Arrays.stream(values())
      .filter(v -> Objects.equals(v.getRepresentation(), (value)))
      .findFirst()
      .orElse(null);
  }

  public static TriggeringEvent from(NoticeEventType eventType) {
    final TriggeringEvent result = from(eventType.getRepresentation());

    if (result == null) {
      throw new IllegalArgumentException(
        "Failed to convert NoticeEventType to TriggeringEvent: " + eventType);
    }

    return result;
  }
}
