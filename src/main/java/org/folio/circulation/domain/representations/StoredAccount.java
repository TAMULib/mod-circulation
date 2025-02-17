package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.representations.ContributorsToNamesMapper.mapContributorNamesToJson;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.services.support.CreateAccountCommand;

import io.vertx.core.json.JsonObject;

public class StoredAccount extends JsonObject {

  public StoredAccount(CreateAccountCommand command) {
    this(
      command.getLoan(),
      command.getItem(),
      command.getFeeFineOwner(),
      command.getFeeFine(),
      command.getAmount());
  }

  public StoredAccount(Loan loan, Item item, FeeFineOwner feeFineOwner,
    FeeFine feeFine, FeeAmount amount) {
    super();

    this.put("id", UUID.randomUUID().toString());
    this.put("ownerId", feeFineOwner.getId());
    this.put("feeFineId", feeFine.getId());
    this.put("amount", amount.toDouble());
    this.put("remaining", amount.toDouble());
    this.put("feeFineType", feeFine.getFeeFineType());
    this.put("feeFineOwner", feeFineOwner.getOwner());
    this.put("title", item.getTitle());
    this.put("barcode", item.getBarcode());
    this.put("callNumber", item.getCallNumber());
    this.put("location", item.getLocation().getName());
    this.put("materialType", item.getMaterialTypeName());
    this.put("materialTypeId", item.getMaterialTypeId());
    this.put("loanId", loan.getId());
    this.put("userId", loan.getUserId());
    this.put("itemId", item.getItemId());
    write(this, "dueDate", loan.getDueDate());
    write(this, "returnedDate", loan.getReturnDate());

    this.put("paymentStatus", createNamedObject("Outstanding"));
    this.put("status", createNamedObject("Open"));

    this.put("contributors", mapContributorNamesToJson(item));
    this.put("loanPolicyId", loan.getLoanPolicyId());
    this.put("overdueFinePolicyId", loan.getOverdueFinePolicyId());
    this.put("lostItemFeePolicyId", loan.getLostItemPolicyId());
  }

  private StoredAccount(JsonObject json) {
    super(json.getMap());
  }

  public String getId() {
    return getString("id");
  }

  public static StoredAccount fromAccount(Account account) {
    return new StoredAccount(account.toJson());
  }

  private JsonObject createNamedObject(String status) {
    return new JsonObject().put("name", status);
  }
}
