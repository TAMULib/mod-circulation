package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.representations.CheckInByBarcodeRequest.CLAIMED_RETURNED_RESOLUTION;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.support.results.Result;

public final class CheckInValidators {
  public Result<CheckInContext> refuseWhenClaimedReturnedIsNotResolved(
    Result<CheckInContext> contextResult) {

    return contextResult.failWhen(
      processRecords -> succeeded(isClaimedReturnedNotResolved(processRecords)),
      processRecords -> singleValidationError(
        "Item is claimed returned, a resolution is required to check in",
        CLAIMED_RETURNED_RESOLUTION, null));
  }

  private boolean isClaimedReturnedNotResolved(CheckInContext context) {
    return context.getItem().isClaimedReturned()
      && context.getCheckInRequest().getClaimedReturnedResolution() == null;
  }
}
