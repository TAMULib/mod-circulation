package api.support.fixtures;

import org.folio.circulation.support.json.JsonPropertyFetcher;
import api.support.http.IndividualResource;

import api.support.http.ResourceClient;

public class PatronGroupsFixture {
  private final RecordCreator patronGroupRecordCreator;

  public PatronGroupsFixture(ResourceClient patronGroupsClient) {
    patronGroupRecordCreator = new RecordCreator(patronGroupsClient,
      group -> JsonPropertyFetcher.getProperty(group, "group"));
  }

  public void cleanUp() {

    patronGroupRecordCreator.cleanUp();
  }

  public IndividualResource regular() {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.regular());
  }

  public IndividualResource alternative() {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.alternative());
  }

  public IndividualResource staff() {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.staff());
  }

  public IndividualResource faculty() {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.faculty());
  }

  public IndividualResource undergrad() {

    return patronGroupRecordCreator.createIfAbsent(PatronGroupExamples.undergrad());
  }
}
