package org.folio.circulation.support.http.server;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class JsonHttpResponse implements HttpResponse {
  private final int statusCode;
  private final JsonObject body;
  private final String location;

  public JsonHttpResponse(int statusCode, JsonObject body, String location) {
    this.statusCode = statusCode;
    this.body = body;
    this.location = location;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    String json = Json.encodePrettily(body);
    Buffer buffer = buffer(json, "UTF-8");

    response.setStatusCode(statusCode);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    if(isNotBlank(location)) {
      response.putHeader("location", location);
    }

    response.write(buffer);
    response.end();
  }
}
