package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

class LoanMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> isOpen() {
    return hasStatus("Open");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isClosed() {
    return hasStatus("Closed");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isAnonymized() {
    return doesNotHaveUserId();
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasLoanProperty(
    String propertyName, Matcher<String> matcher) {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan should have a ")
          .appendText(propertyName).appendText(" property that is ").appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {

        final String actualValue = getProperty(representation, propertyName);

        matcher.describeMismatch(actualValue, description);

        return matcher.matches(actualValue);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasLoanProperty(
    String propertyName, String expectedValue) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan should have a ")
          .appendText(propertyName).
          appendText(" of ").appendText(expectedValue);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {

        final String actualValue = representation.getString(propertyName);
        Matcher<String> objectMatcher = is(actualValue);
        objectMatcher.describeMismatch(expectedValue, description );
        return objectMatcher.matches(expectedValue);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasLoanProperty(
    String propertyName) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan item should have a ")
          .appendText(propertyName);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        return notNullValue().matches(representation.getValue(propertyName));
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasStatus(
    String status) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan should have a status of ")
          .appendText(status);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        if (!representation.containsKey("status")) {
          description.appendText("has no status property");
          return false;
        }

        final Matcher<String> statusMatcher = is(status);

        final String statusName = representation.getJsonObject("status")
          .getString("name");
        statusMatcher.describeMismatch(statusName, description);
        return statusMatcher.matches(statusName);
      }
    };
  }

  private static TypeSafeDiagnosingMatcher<JsonObject> doesNotHaveUserId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Anonymized loan should not have a userId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        this.describeMismatch(representation.getValue("userId"), description);
        return !representation.containsKey("userId") &&
          !representation.containsKey("borrower");

      }
    };
  }

  public static Matcher<JsonObject> hasItem() {
    return hasJsonPath("item", notNullValue());
  }
}
