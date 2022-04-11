package ez.onein_proxy

import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod

class UnwrappedOneinReq(
  val path: String,
  val method: HttpMethod?,
  val headers: MultiMap?,
  val query: MultiMap?,
  val body: Buffer?
)
