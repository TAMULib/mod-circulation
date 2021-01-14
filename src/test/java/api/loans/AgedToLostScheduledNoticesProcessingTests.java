package api.loans;

import static api.support.fixtures.TemplateContextMatchers.getFeeActionContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getFeeChargeContextMatcher;
import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getLoanContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.matchers.AccountMatchers.isAccount;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledFeeFineNotice;
import static api.support.matchers.ScheduledNoticeMatchers.hasScheduledLoanNotice;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.notice.NoticeTiming.AFTER;
import static org.folio.circulation.domain.notice.NoticeTiming.UPON_AT;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.joda.time.DateTime.now;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.AgeToLostFixture.AgeToLostResult;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class AgedToLostScheduledNoticesProcessingTests extends APITests {
  private static final UUID UPON_AT_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_ONE_TIME_TEMPLATE_ID = UUID.randomUUID();
  private static final UUID AFTER_RECURRING_TEMPLATE_ID = UUID.randomUUID();

  private static final Period TIMING_PERIOD = Period.days(1);
  private static final Period RECURRENCE_PERIOD = Period.hours(1);

  public static final String ACCOUNT_STATUS_OPEN = "Open";
  public static final String LOST_ITEM_FEE = "Lost item fee";
  public static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";
  public static final String PAYMENT_STATUS_OUTSTANDING = "Outstanding";

  public static final String ACTION_TYPE_CANCELLED = "Cancelled item returned";
  public static final String ACTION_TYPE_REFUNDED_PARTIALLY = "Refunded partially";
  public static final String ACTION_TYPE_REFUNDED_FULLY = "Refunded fully";

  public static final double LOST_ITEM_FEE_AMOUNT = 10;
  public static final double PROCESSING_FEE_AMOUNT = 5;
  public static final double LOST_ITEM_FEE_PAYMENT_AMOUNT = LOST_ITEM_FEE_AMOUNT / 2;
  public static final double PROCESSING_FEE_PAYMENT_AMOUNT = PROCESSING_FEE_AMOUNT / 2;

  @Before
  public void beforeEach() throws InterruptedException {
    super.beforeEach();

    templateFixture.createDummyNoticeTemplate(UPON_AT_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_ONE_TIME_TEMPLATE_ID);
    templateFixture.createDummyNoticeTemplate(AFTER_RECURRING_TEMPLATE_ID);

    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
  }

  @Test
  public void agedToLostLoanNoticesAreCreatedAndProcessed() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_ONE_TIME_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .create(),
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .recurring(RECURRENCE_PERIOD)
            .create()
          )));

    final DateTime runTimeOfUponAtNotice = getAgedToLostDate(agedToLostLoan);
    final DateTime runTimeOfAfterNotices = runTimeOfUponAtNotice.plus(TIMING_PERIOD.timePeriod());

    final UUID loanId = agedToLostLoan.getLoanId();

    // before first run, all three scheduled notices should exist
    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(3),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfUponAtNotice,
          UPON_AT.getRepresentation(), UPON_AT_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // first run, UPON_AT notice should be sent and deleted
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfUponAtNotice.plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(2),
      hasItems(
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_ONE_TIME_TEMPLATE_ID, null, true),
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // second run, both AFTER notices should be sent, recurring AFTER notice should be rescheduled
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(runTimeOfAfterNotices.plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(3));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItem(
        hasScheduledLoanNotice(loanId, runTimeOfAfterNotices.plus(RECURRENCE_PERIOD.timePeriod()),
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    checkSentLoanNotices(agedToLostLoan,
      List.of(UPON_AT_TEMPLATE_ID, AFTER_ONE_TIME_TEMPLATE_ID, AFTER_RECURRING_TEMPLATE_ID));

    // close the loan
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // third run, loan is now closed so recurring notice should be deleted without sending
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      runTimeOfAfterNotices.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(3));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void shouldStopSendingAgedToLostNoticesOnceLostItemFeeWasCharged() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostEvent()
            .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
            .withAfterTiming(TIMING_PERIOD)
            .recurring(RECURRENCE_PERIOD)
            .create()
        )));

    final DateTime firstRunTime = getAgedToLostDate(agedToLostLoan).plus(TIMING_PERIOD.timePeriod());

    final UUID loanId = agedToLostLoan.getLoanId();

    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItems(
        hasScheduledLoanNotice(loanId, firstRunTime,
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    // first run, notice should be sent and rescheduled
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(firstRunTime.plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(1),
      hasItems(
        hasScheduledLoanNotice(loanId, firstRunTime.plus(RECURRENCE_PERIOD.timePeriod()),
          AFTER.getRepresentation(), AFTER_RECURRING_TEMPLATE_ID, RECURRENCE_PERIOD, true)
      )));

    ageToLostFixture.chargeFees();

    // second run, notice should be deleted without sending
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      firstRunTime.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));
    assertThat(patronNoticesClient.getAll(), hasSize(1));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));
  }

  @Test
  public void shouldStopSendingAgedToLostNoticesOnceItemIsDeclaredLost() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    declareLostFixtures.declareItemLost(agedToLostLoan.getLoan().getJson());
    final DateTime firstRunTime = getAgedToLostDate(agedToLostLoan).plus(
      TIMING_PERIOD.timePeriod());
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      firstRunTime.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));

    assertThat(scheduledNoticesClient.getAll(), empty());
    assertThat(patronNoticesClient.getAll(), empty());
  }

  @Test
  public void shouldStopSendingAgedToLostNoticesOnceItemIsClaimedReturned() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    claimItemReturnedFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(agedToLostLoan.getLoanId().toString())
      .withItemClaimedReturnedDate(now()));
    final DateTime firstRunTime = getAgedToLostDate(agedToLostLoan).plus(
      TIMING_PERIOD.timePeriod());
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      firstRunTime.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));

    assertThat(scheduledNoticesClient.getAll(), empty());
    assertThat(patronNoticesClient.getAll(), empty());
  }

  @Test
  public void shouldStopSendingAgedToLostNoticesOnceItemIsRenewed() {
    AgeToLostResult agedToLostLoan = createRecurringAgedToLostNotice();

    loansFixture.overrideRenewalByBarcode(agedToLostLoan.getItem(), agedToLostLoan.getUser(),
      "Test overriding", agedToLostLoan.getLoan().getJson().getString("dueDate"));
    loansFixture.renewLoan(agedToLostLoan.getItem(), agedToLostLoan.getUser());
    final DateTime firstRunTime = getAgedToLostDate(agedToLostLoan).plus(
      TIMING_PERIOD.timePeriod());
    scheduledNoticeProcessingClient.runLoanNoticesProcessing(
      firstRunTime.plus(RECURRENCE_PERIOD.timePeriod()).plusMinutes(1));

    assertThat(scheduledNoticesClient.getAll(), empty());
    assertThat(patronNoticesClient.getAll(), empty());
  }

  private AgeToLostResult createRecurringAgedToLostNotice() {
    val agedToLostLoan = ageToLostFixture.createAgedToLostLoan(
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withLoanNotices(Collections.singletonList(new NoticeConfigurationBuilder()
          .withAgedToLostEvent()
          .withTemplateId(AFTER_RECURRING_TEMPLATE_ID)
          .withAfterTiming(TIMING_PERIOD)
          .recurring(RECURRENCE_PERIOD)
          .create())));

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(scheduledNoticesClient::getAll, hasSize(1));

    return agedToLostLoan;
  }

  @Test
  public void patronNoticesForForAgedToLostFineAdjustmentsAreCreatedAndProcessed() {
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(LOST_ITEM_FEE_AMOUNT)
      .withLostItemProcessingFee(PROCESSING_FEE_AMOUNT);

    AgeToLostResult agedToLostLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder,
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withFeeFineNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostReturnedEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create()
        )));

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, agedToLostLoan.getUser().getId()),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, agedToLostLoan.getUser().getId())
      )));

    // one "charge" action per account
    assertThat(feeFineActionsClient.getAll(), hasSize(2));

    // create payments for both accounts to produce "refund" actions and notices
    existingAccounts.forEach(account -> feeFineAccountFixture.pay(
      account.getString("id"),
      account.getDouble("remaining") / 2) // partial payment to produce "cancel" actions and notices
    );

    // 2 charges + 2 payments
    assertThat(feeFineActionsClient.getAll(), hasSize(4));

    // check-in should refund and cancel both "Lost item fee" and "Lost item processing fee"
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // 2 charges + 2 payments + 2 credits + 2 refunds + 2 cancellations
    assertThat(feeFineActionsClient.getAll(), hasSize(10));

    final UUID loanId = agedToLostLoan.getLoanId();
    final UUID userId = agedToLostLoan.getUser().getId();

    final JsonObject refundLostItemFeeAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_PARTIALLY, LOST_ITEM_FEE_PAYMENT_AMOUNT);
    final JsonObject refundProcessingFeeAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_PARTIALLY, PROCESSING_FEE_PAYMENT_AMOUNT);
    final JsonObject cancelLostItemFeeAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, LOST_ITEM_FEE_AMOUNT);
    final JsonObject cancelProcessingFeeAction =
      findFeeFineAction(ACTION_TYPE_CANCELLED, PROCESSING_FEE_AMOUNT);

    final UUID refundLostItemFeeActionId = getId(refundLostItemFeeAction);
    final UUID refundProcessingFeeActionId = getId(refundProcessingFeeAction);
    final UUID cancelLostItemFeeActionId = getId(cancelLostItemFeeAction);
    final UUID cancelProcessingFeeActionId = getId(cancelProcessingFeeAction);

    final DateTime refundLostItemFeeActionDate = getActionDate(refundLostItemFeeAction);
    final DateTime refundProcessingFeeActionDate = getActionDate(refundProcessingFeeAction);
    final DateTime cancelLostItemFeeActionDate = getActionDate(cancelLostItemFeeAction);
    final DateTime cancelProcessingFeeActionDate = getActionDate(cancelProcessingFeeAction);

    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(4),
      hasItems(
        hasScheduledFeeFineNotice(refundLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(refundProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundProcessingFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, cancelLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(cancelProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, cancelProcessingFeeActionDate,
          UPON_AT, null, true)
      )));

    DateTime maxActionDate = Stream.of(
      cancelLostItemFeeActionDate,
      cancelProcessingFeeActionDate,
      refundLostItemFeeActionDate,
      refundProcessingFeeActionDate)
      .max(DateTime::compareTo)
      .orElseThrow();

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(maxActionDate.plusSeconds(1));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));

    checkSentFeeFineNotices(agedToLostLoan, Map.of(
      refundLostItemFeeAction, UPON_AT_TEMPLATE_ID,
      refundProcessingFeeAction, UPON_AT_TEMPLATE_ID,
      cancelLostItemFeeAction, UPON_AT_TEMPLATE_ID,
      cancelProcessingFeeAction, UPON_AT_TEMPLATE_ID
    ));
  }

  @Test
  public void patronNoticeForAdjustmentOfFullyPaidLostItemFeeIsCreatedAndProcessed() {
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(LOST_ITEM_FEE_AMOUNT)
      .withLostItemProcessingFee(PROCESSING_FEE_AMOUNT);

    AgeToLostResult agedToLostLoan = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePolicyBuilder,
      new NoticePolicyBuilder()
        .active()
        .withName("Aged to lost notice policy")
        .withFeeFineNotices(List.of(
          new NoticeConfigurationBuilder()
            .withAgedToLostReturnedEvent()
            .withTemplateId(UPON_AT_TEMPLATE_ID)
            .withUponAtTiming()
            .create()
        )));

    final UUID loanId = agedToLostLoan.getLoanId();
    final UUID userId = agedToLostLoan.getUser().getId();

    final List<JsonObject> existingAccounts = accountsClient.getAll();

    assertThat(existingAccounts, allOf(
      iterableWithSize(2),
      hasItems(
        isAccount(LOST_ITEM_FEE_AMOUNT, LOST_ITEM_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_FEE, agedToLostLoan.getUser().getId()),
        isAccount(PROCESSING_FEE_AMOUNT, PROCESSING_FEE_AMOUNT, ACCOUNT_STATUS_OPEN,
          PAYMENT_STATUS_OUTSTANDING, LOST_ITEM_PROCESSING_FEE, agedToLostLoan.getUser().getId())
      )));

    // one "charge" action per account
    assertThat(feeFineActionsClient.getAll(), hasSize(2));

    final List<Account> accounts = existingAccounts.stream()
      .map(Account::from)
      .collect(toList());

    assertThat(accounts, hasSize(2));

    final Account firstAccount = accounts.get(0);
    final Account secondAccount = accounts.get(1);

    feeFineAccountFixture.pay(firstAccount.getId(), firstAccount.getAmount().toDouble());
    feeFineAccountFixture.transfer(secondAccount.getId(), secondAccount.getAmount().toDouble());

    // 2 charges + 1 payment + 1 transfer
    assertThat(feeFineActionsClient.getAll(), hasSize(4));

    // closing a loan-related fee/fine should close the loan
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loanId);
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // check-in should refund both fees, no cancellations since both were paid/transferred fully
    checkInFixture.checkInByBarcode(agedToLostLoan.getItem());
    assertThat(itemsFixture.getById(agedToLostLoan.getItemId()).getJson(), isAvailable());
    assertThat(loansFixture.getLoanById(agedToLostLoan.getLoanId()).getJson(), isClosed());

    // 2 charges + 1 payment + 1 transfer + 2 credits + 2 refunds
    assertThat(feeFineActionsClient.getAll(), hasSize(8));

    final JsonObject lostItemFeeRefundAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_FULLY, LOST_ITEM_FEE_AMOUNT);
    final JsonObject processingFeeRefundAction =
      findFeeFineAction(ACTION_TYPE_REFUNDED_FULLY, PROCESSING_FEE_AMOUNT);

    final UUID refundLostItemFeeActionId = getId(lostItemFeeRefundAction);
    final UUID refundProcessingFeeActionId = getId(processingFeeRefundAction);

    final DateTime refundLostItemFeeActionDate = getActionDate(lostItemFeeRefundAction);
    final DateTime refundProcessingFeeActionDate = getActionDate(processingFeeRefundAction);

    assertThat(patronNoticesClient.getAll(), hasSize(0));
    assertThat(scheduledNoticesClient.getAll(), allOf(
      iterableWithSize(2),
      hasItems(
        hasScheduledFeeFineNotice(refundLostItemFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundLostItemFeeActionDate,
          UPON_AT, null, true),
        hasScheduledFeeFineNotice(refundProcessingFeeActionId, loanId, userId,
          UPON_AT_TEMPLATE_ID, AGED_TO_LOST_RETURNED, refundProcessingFeeActionDate,
          UPON_AT, null, true)
      )));

    DateTime maxActionDate = Stream.of(refundLostItemFeeActionDate, refundProcessingFeeActionDate)
      .max(DateTime::compareTo)
      .orElseThrow();

    scheduledNoticeProcessingClient.runFeeFineNoticesProcessing(maxActionDate.plusSeconds(1));
    assertThat(scheduledNoticesClient.getAll(), hasSize(0));

    checkSentFeeFineNotices(agedToLostLoan, Map.of(
      lostItemFeeRefundAction, UPON_AT_TEMPLATE_ID,
      processingFeeRefundAction, UPON_AT_TEMPLATE_ID
    ));
  }

  private JsonObject findFeeFineAction(String actionType, double actionAmount) {
    return feeFineActionsClient.getMany(
      exactMatch("typeAction", actionType)
        .and(exactMatch("amountAction", String.valueOf(actionAmount))))
      .getFirst();
  }

  private static DateTime getActionDate(JsonObject feeFineAction) {
    return DateTime.parse(feeFineAction.getString("dateAction"));
  }

  private static UUID getId(JsonObject jsonObject) {
    return UUID.fromString(jsonObject.getString("id"));
  }

  private static DateTime getAgedToLostDate(AgeToLostResult ageToLostResult) {
    return DateTime.parse(
      ageToLostResult.getLoan()
        .getJson()
        .getJsonObject("agedToLostDelayedBilling")
        .getString("agedToLostDate")
    );
  }

  private void checkSentFeeFineNotices(AgeToLostResult agedToLostResult,
    Map<JsonObject, UUID> actionsToTemplateIds) {

    final UUID userId = agedToLostResult.getUser().getId();

    final List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(actionsToTemplateIds.size()));

    actionsToTemplateIds.keySet().stream()
      .map(feeFineAction -> hasEmailNoticeProperties(userId, actionsToTemplateIds.get(feeFineAction),
          allOf(
            getBaseNoticeContextMatcher(agedToLostResult),
            getFeeActionContextMatcher(feeFineAction),
            getFeeChargeContextMatcher(findAccountForFeeFineAction(feeFineAction)))))
      .forEach(matcher -> assertThat(sentNotices, hasItem(matcher)));
  }

  private void checkSentLoanNotices(AgeToLostResult agedToLostResult, List<UUID> templateIds) {
    final UUID userId = agedToLostResult.getUser().getId();

    final List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat(sentNotices, hasSize(templateIds.size()));

    templateIds.forEach(templateId -> assertThat(sentNotices, hasItem(
        hasEmailNoticeProperties(userId, templateId, getBaseNoticeContextMatcher(agedToLostResult)))));
  }

  private JsonObject findAccountForFeeFineAction(JsonObject feeFineAction) {
    return accountsClient.get(UUID.fromString(feeFineAction.getString("accountId"))).getJson();
  }

  private Matcher<? super String> getBaseNoticeContextMatcher(AgeToLostResult agedToLostResult) {
    final IndividualResource holdingsRecord = holdingsClient.get(
      getUUIDProperty(agedToLostResult.getItem().getJson(), "holdingsRecordId"));

    final IndividualResource instance = instancesClient.get(
      getUUIDProperty(holdingsRecord.getJson(), "instanceId"));

    final ItemResource itemResource = new ItemResource(agedToLostResult.getItem(),
      holdingsRecord, instance);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(getUserContextMatchers(agedToLostResult.getUser()));
    noticeContextMatchers.putAll(getLoanContextMatchers(agedToLostResult.getLoan()));
    noticeContextMatchers.putAll(getItemContextMatchers(itemResource, true));

    return toStringMatcher(noticeContextMatchers);
  }
}
