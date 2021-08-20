package api.support.matchers;

import api.support.http.IndividualResource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.core.Is;

public class ItemStatusCodeMatcher extends TypeSafeDiagnosingMatcher<IndividualResource> {
  public static ItemStatusCodeMatcher hasItemStatus(String expectedStatus) {
    return new ItemStatusCodeMatcher(expectedStatus);
  }

  private final String expectedStatus;

  public ItemStatusCodeMatcher(String expectedStatus) {
    this.expectedStatus = expectedStatus;
  }

  @Override
  protected boolean matchesSafely(IndividualResource item, Description description) {

    Matcher<String> matcher = Is.is(expectedStatus);
    String value = item.getJson().getJsonObject("status").getString("name");

    if (matcher.matches(value))
      return true;
    else {
      description.appendText("an item with a status of ").appendValue(value);
      return false;
    }
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("an item with a status of ").appendValue(expectedStatus);
  }
}
