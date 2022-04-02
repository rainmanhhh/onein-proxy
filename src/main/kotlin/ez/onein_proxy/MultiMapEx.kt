package ez.onein_proxy

import io.vertx.core.MultiMap
import io.vertx.core.json.JsonObject

fun MultiMap.toJson() = JsonObject().also {
  for (entry in entries()) {
    it.put(entry.key, entry.value)
  }
}
