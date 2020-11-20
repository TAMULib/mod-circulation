package api.loans;

import static api.support.fixtures.CalendarExamples.CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_ONE_DAY_IS_OPEN_NEXT_TWO_DAYS_CLOSED;
import static api.support.fixtures.CalendarExamples.FIRST_DAY_OPEN;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutToExpiringPatronTests extends APITests {
  @Test
  public void dueDateShouldBeTruncatedToTheEndOfLastWorkingDayBeforePatronExpiration() {
    mockClockManagerToReturnFixedDateTime(new DateTime(2020, 10, 27, 10, 0, UTC));

    useExampleFixedPolicyCirculationRules();

    IndividualResource item = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve(user -> user.expires(
      DateTime.now().plusDays(3)));

    JsonObject response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(CASE_ONE_DAY_IS_OPEN_NEXT_TWO_DAYS_CLOSED)).getJson();

    mockClockManagerToReturnDefaultDateTime();
    assertThat(DateTime.parse(response.getString("dueDate")).toLocalDate(), is(FIRST_DAY_OPEN));
  }

  @Test
  public void dueDateTruncationForPatronExpirationFailsWhenNoCalendarIsDefinedForServicePoint() {
    mockClockManagerToReturnFixedDateTime(new DateTime(2020, 10, 27, 10, 0, UTC));

    useExampleFixedPolicyCirculationRules();

    IndividualResource item = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve(user -> user.expires(
      DateTime.now().plusDays(3)));

    final var response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID));

    mockClockManagerToReturnDefaultDateTime();

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Calendar timetable is absent for requested date"))));

    final var incorrectlyUpdateItem = itemsClient.get(item);

    // As the truncation of the due date happens after the item has been updated
    // the item is checked out in error
    assertThat(incorrectlyUpdateItem, hasItemStatus("Checked out"));
  }
}
