package org.folio.circulation.infrastructure.storage.loans;

import static java.util.Objects.isNull;
import static org.folio.circulation.domain.policy.LoanPolicy.unknown;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.policy.FixedDueDateSchedules;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.NoFixedDueDateSchedules;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LoanPolicyRepository extends CirculationPolicyRepository<LoanPolicy> {
  private final GetManyRecordsClient fixedDueDateSchedulesStorageClient;

  public LoanPolicyRepository(Clients clients) {
    super(clients.loanPoliciesStorage(), clients);
    this.fixedDueDateSchedulesStorageClient = clients.fixedDueDateSchedules();
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> lookupLoanPolicy(
    LoanAndRelatedRecords relatedRecords) {
    if (relatedRecords.getLoan() == null || relatedRecords.getLoan().getUser() == null) {
      return ofAsync(() -> relatedRecords);
    }
    return Result.of(relatedRecords::getLoan)
      .combineAfter(this::lookupPolicy, Loan::withLoanPolicy)
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  public CompletableFuture<Result<RenewalContext>> lookupLoanPolicy(
    RenewalContext renewalContext) {

    return Result.of(renewalContext::getLoan)
      .combineAfter(this::lookupPolicy, Loan::withLoanPolicy)
      .thenApply(mapResult(renewalContext::withLoan));
  }

  public CompletableFuture<Result<Loan>> findPolicyForLoan(Result<Loan> loanResult) {
    return loanResult.after(loan ->
      getLoanPolicyById(loan.getLoanPolicyId())
      .thenApply(result -> result.map(loan::withLoanPolicy)));
  }

  public CompletableFuture<Result<Loan>> findPolicyForLoan(Loan loan) {
    return getLoanPolicyById(loan.getLoanPolicyId())
        .thenApply(result -> result.map(loan::withLoanPolicy));
  }

  private CompletableFuture<Result<LoanPolicy>> getLoanPolicyById(String loanPolicyId) {
    if (isNull(loanPolicyId)) {
      return ofAsync(() -> unknown(null));
    }

    return FetchSingleRecord.<LoanPolicy>forRecord("loan policy")
      .using(policyStorageClient)
      .mapTo(LoanPolicy::from)
      .whenNotFound(succeeded(unknown(loanPolicyId)))
      .fetch(loanPolicyId);
  }

  public CompletableFuture<Result<MultipleRecords<Loan>>> findLoanPoliciesForLoans(MultipleRecords<Loan> multipleLoans) {
    Collection<Loan> loans = multipleLoans.getRecords();

    return getLoanPolicies(loans)
      .thenApply(r -> r.map(loanPolicies -> multipleLoans.mapRecords(
        loan -> loan.withLoanPolicy(loanPolicies.getOrDefault(
          loan.getLoanPolicyId(), unknown(loan.getLoanPolicyId())))))
    );
  }

  private CompletableFuture<Result<Map<String, LoanPolicy>>> getLoanPolicies(Collection<Loan> loans) {
    final Collection<String> loansToFetch = loans.stream()
            .map(Loan::getLoanPolicyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    final FindWithMultipleCqlIndexValues<LoanPolicy> fetcher = createLoanPoliciesFetcher();

    return fetcher.findByIds(loansToFetch)
      .thenApply(mapResult(r -> r.toMap(LoanPolicy::getId)));
  }

  private FindWithMultipleCqlIndexValues<LoanPolicy> createLoanPoliciesFetcher() {
    return findWithMultipleCqlIndexValues(policyStorageClient, "loanPolicies",
      LoanPolicy::from);
  }

  @Override
  public CompletableFuture<Result<LoanPolicy>> lookupPolicy(Loan loan) {
    return super.lookupPolicy(loan)
      .thenComposeAsync(r -> r.after(this::lookupSchedules));
  }

  private CompletableFuture<Result<LoanPolicy>> lookupSchedules(LoanPolicy loanPolicy) {
    List<String> scheduleIds = new ArrayList<>();

    final String loanScheduleId = loanPolicy.getLoansFixedDueDateScheduleId();
    final String alternateRenewalsSchedulesId = loanPolicy.getAlternateRenewalsFixedDueDateScheduleId();

    if (loanScheduleId != null) {
      scheduleIds.add(loanScheduleId);
    }

    if (alternateRenewalsSchedulesId != null) {
      scheduleIds.add(alternateRenewalsSchedulesId);
    }

    if (scheduleIds.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(loanPolicy));
    }

    return getSchedules(scheduleIds)
      .thenApply(r -> r.next(schedules -> {
        final FixedDueDateSchedules loanSchedule = schedules.getOrDefault(
          loanScheduleId, new NoFixedDueDateSchedules());

        final FixedDueDateSchedules renewalSchedule = schedules.getOrDefault(
          alternateRenewalsSchedulesId, new NoFixedDueDateSchedules());

        return succeeded(loanPolicy
          .withDueDateSchedules(loanSchedule)
          .withAlternateRenewalSchedules(renewalSchedule));
      }));
  }

  private CompletableFuture<Result<Map<String, FixedDueDateSchedules>>> getSchedules(
    Collection<String> schedulesIds) {

    final FindWithMultipleCqlIndexValues<FixedDueDateSchedules> fetcher
      = findWithMultipleCqlIndexValues(fixedDueDateSchedulesStorageClient,
        "fixedDueDateSchedules", FixedDueDateSchedules::from);

    return fetcher.findByIds(schedulesIds)
      .thenApply(mapResult(schedules -> schedules.toMap(FixedDueDateSchedules::getId)));
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Loan policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<LoanPolicy> toPolicy(JsonObject representation, AppliedRuleConditions ruleConditionsEntity) {
    return succeeded(new LoanPolicy(representation,
      new NoFixedDueDateSchedules(), new NoFixedDueDateSchedules(), ruleConditionsEntity));
  }

  @Override
  protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters) {

    return circulationRulesProcessor.getLoanPolicyAndMatch(rulesExecutionParameters);
  }
}
