package ez.onein_proxy

import com.fasterxml.jackson.databind.DeserializationFeature
import ez.onein_proxy.server.HttpServerVerticle
import io.vertx.config.ConfigRetriever
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

@Suppress("unused")
class MainVerticle : CoroutineVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(MainVerticle::class.java)!!

    init {
      DatabindCodec.mapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      DatabindCodec.prettyMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
  }

  override suspend fun start() {
    readConfig()
    val cfg = Config.instance
    val newVertx = Vertx.vertx(cfg.vertx)
    newVertx.deployVerticle(
      HttpServerVerticle::class.java,
      DeploymentOptions()
        .setInstances(cfg.vertx.eventLoopPoolSize)
    ).await()
    vertx.close {
      logger.info("launcher vertx instance closed")
    }
    logger.info("MainVerticle started")
  }

  private suspend fun readConfig() {
    logger.info("reading config...")
    val configJson = ConfigRetriever.create(vertx).config.await()
    Config.instance = configJson.mapTo(Config::class.java).validate()
  }
}
