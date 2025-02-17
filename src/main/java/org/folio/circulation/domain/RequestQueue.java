package org.folio.circulation.domain;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

public class RequestQueue {
  private List<Request> requests;
  private final List<UpdatedRequestPair> updatedRequests;

  public static RequestQueue requestQueueOf(Request... requests) {
    return new RequestQueue(asList(requests));
  }

  public RequestQueue(Collection<Request> requests) {
    this.requests = new ArrayList<>(requests);
    updatedRequests = new ArrayList<>();

    // Ordering requests by position, so we can add and remove them
    // without sorting and just re-sequence from 1 to n
    this.requests.sort(Comparator
      .comparingInt(request -> Optional.ofNullable(request.getPosition())
        .orElse(0)
      ));
  }

  public RequestQueue filter(Predicate<Request> predicate) {
    return new RequestQueue(requests.stream()
      .filter(predicate)
      .collect(toList()));
  }

  ItemStatus checkedInItemStatus(Item item) {
    return hasOutstandingRequestsFulfillableByItem(item)
      ? getHighestPriorityRequestFulfillableByItem(item).checkedInItemStatus()
      : AVAILABLE;
  }

  boolean hasOutstandingFulfillableRequests() {
    return !fulfillableRequests().isEmpty();
  }

  boolean hasOutstandingRequestsFulfillableByItem(Item item) {
    return  fulfillableRequests().stream()
      .anyMatch(request -> requestIsFulfillableByItem(request, item));
  }

  Request getHighestPriorityFulfillableRequest() {
    return fulfillableRequests().get(0);
  }

  Request getHighestPriorityRequestFulfillableByItem(Item item) {
    return fulfillableRequests().stream()
      .filter(request -> requestIsFulfillableByItem(request, item))
      .findFirst()
      .orElse(null);
  }

  private boolean requestIsFulfillableByItem(Request request, Item item) {
    if (request.getRequestLevel() == RequestLevel.TITLE) {
      String itemInstanceId = item.getInstanceId();
      String requestInstanceId = request.getInstanceId();
      if(request.isRecall() && request.isNotYetFilled()) {
        return requestInstanceId.equals(itemInstanceId);
      }

      String requestItemId = request.getItemId();
      String itemId = item.getItemId();
      return requestInstanceId.equals(itemInstanceId) && (requestItemId == null ^
        (item.isFound() && itemId.equals(requestItemId)));
    }
    else if (request.getRequestLevel() == RequestLevel.ITEM) {
      String itemId = item.getItemId();
      String requestItemId = request.getItemId();

      return itemId != null && itemId.equals(requestItemId);
    }

    return false;
  }

  boolean containsRequestOfTypeForItem(RequestType type, Item item) {
    return requests.stream().anyMatch(request -> request.getRequestType() == type && request.isFor(item));
  }

  public boolean hasOpenRecalls() {
    return requests.stream()
        .anyMatch(request -> request.getRequestType() == RequestType.RECALL && request.isNotYetFilled());
  }

  public List<String> getRecalledLoansIds() {
    return requests.stream()
      .filter(Request::isRecall)
      .map(Request::getLoan)
      .filter(Objects::nonNull)
      .map(Loan::getId)
      .collect(toList());
  }

  public Loan getTheLeastRecalledLoan() {
    return requests.stream()
      .filter(Request::isRecall)
      //Counting the amount of recalls for each loan
      .collect(collectingAndThen(groupingBy(Request::getLoan, counting()), m -> m.entrySet()
        .stream()
        .min(Comparator.comparingLong(Map.Entry<Loan, Long>::getValue)
          .thenComparing(o -> o.getKey().getDueDate()))
        .map(Map.Entry::getKey)
        .orElse(null)));
  }

  public boolean isRequestedByAnotherPatron(User requestingUser, Item item) {
    Request request = getHighestPriorityRequestFulfillableByItem(item);

    return !(request == null || request.isFor(requestingUser));
  }

  private List<Request> fulfillableRequests() {
    return requests
      .stream()
      .filter(Request::isFulfillable)
      .collect(toList());
  }

  public void add(Request newRequest) {
    requests = new ArrayList<>(requests);
    requests.add(newRequest);
    reSequenceRequests();
  }

  public void update(Request original, Request updated) {
    updatedRequests.add(new UpdatedRequestPair(original, updated));
  }

  public void remove(Request request) {
    requests = requests.stream()
      .filter(r -> !r.getId().equals(request.getId()))
      .collect(toList());
    request.removePosition();
    reSequenceRequests();
  }

  private void reSequenceRequests() {
    final AtomicInteger position = new AtomicInteger(1);
    requests.forEach(req -> req.changePosition(position.getAndIncrement()));
  }

  public Integer size() {
    return requests.size();
  }

  public Boolean contains(Request request) {
      return requests.stream()
        .anyMatch(r -> r.getId().equals(request.getId()));
  }

  public Collection<Request> getRequestsWithChangedPosition() {
    return requests.stream()
      .filter(Request::hasChangedPosition)
      // order by position descending
      .sorted((req1, req2) -> req2.getPosition().compareTo(req1.getPosition()))
      .collect(toList());
  }

  //TODO: Encapsulate this better
  public Collection<Request> getRequests() {
    return requests;
  }

  public List<UpdatedRequestPair> getUpdatedRequests() {
    return updatedRequests;
  }

  boolean isEmpty() {
    return getRequests().isEmpty();
  }

  // puts request on top of all requests in status "Open - Not yet filled"
  public void updateRequestPositionOnCheckIn(String requestId) {
    int newIndex = -1;

    for (int i = 0; i < requests.size(); i++) {
      var currentRequest = requests.get(i);
      boolean isSameRequest = StringUtils.equals(requestId, currentRequest.getId());

      if (newIndex == -1) {
        if (!isSameRequest && currentRequest.isNotYetFilled()) {
          newIndex = i;
        }
      } else if (isSameRequest) {
        requests.add(newIndex, requests.remove(i));
        reSequenceRequests();
        return;
      }
    }
  }


}
