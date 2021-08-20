package api.support.fixtures;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import api.support.http.IndividualResource;

import api.support.builders.NoticePolicyBuilder;
import api.support.http.ResourceClient;

public class NoticePoliciesFixture {
  private final RecordCreator noticePolicyRecordCreator;

  public NoticePoliciesFixture(ResourceClient noticePoliciesClient) {
    noticePolicyRecordCreator = new RecordCreator(noticePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource inactiveNotice() {

    final NoticePolicyBuilder inactiveNoticePolicy = new NoticePolicyBuilder();

    return noticePolicyRecordCreator.createIfAbsent(inactiveNoticePolicy);
  }

  public IndividualResource activeNotice() {

    final NoticePolicyBuilder activeNoticePolicy = new NoticePolicyBuilder()
      .active();

    return noticePolicyRecordCreator.createIfAbsent(activeNoticePolicy);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy) {
    return noticePolicyRecordCreator.createIfAbsent(noticePolicy);
  }

  public void cleanUp() {
    noticePolicyRecordCreator.cleanUp();
  }

  public void delete(IndividualResource record) {
    noticePolicyRecordCreator.delete(record);
  }
}
