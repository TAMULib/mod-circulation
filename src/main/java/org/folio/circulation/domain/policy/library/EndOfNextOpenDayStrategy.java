package org.folio.circulation.domain.policy.library;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.failureForAbsentTimetable;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.support.results.Result;

public class EndOfNextOpenDayStrategy implements ClosedLibraryStrategy {

  private final ZoneId zone;

  public EndOfNextOpenDayStrategy(ZoneId zone) {
    this.zone = zone;
  }

  @Override
  public Result<ZonedDateTime> calculateDueDate(ZonedDateTime requestedDate, AdjacentOpeningDays openingDays) {
    Objects.requireNonNull(openingDays);
    if (openingDays.getRequestedDay().getOpen()) {
      return succeeded(atEndOfDay(requestedDate, zone));
    }
    OpeningDay nextDay = openingDays.getNextDay();
    if (!nextDay.getOpen()) {
      return failed(failureForAbsentTimetable());
    }

    return succeeded(atEndOfDay(nextDay.getDate(), zone));
  }
}
