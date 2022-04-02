package ez.onein_proxy

import io.vertx.core.MultiMap
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject

class UnwrappedOneinReq(
  val path: String,
  val method: HttpMethod?,
  val headers: MultiMap?,
  val query: MultiMap?,
  val body: JsonObject?
)
