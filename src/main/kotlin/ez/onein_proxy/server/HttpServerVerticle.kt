package ez.onein_proxy.server

import ez.onein_proxy.Config
import ez.onein_proxy.HttpException
import ez.onein_proxy.UnwrappedOneinReq
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class HttpServerVerticle : CoroutineVerticle() {
  companion object {
    private val logger = LoggerFactory.getLogger(HttpServerVerticle::class.java)!!
    private val validHttpMethods = HttpMethod.values().associateBy { it.name() }
  }

  lateinit var cfg: Config
  lateinit var httpServer: HttpServer
  lateinit var router: Router
  lateinit var webClient: WebClient

  override suspend fun start() {
    cfg = Config.instance
    createBeans()
    startHttpServer()
  }

  private fun createBeans() {
    logger.info("creating beans...")
    httpServer = vertx.createHttpServer(cfg.httpServer)
    router = Router.router(vertx)
    webClient = WebClient.create(vertx)
    logger.info("beans created")
  }

  /**
   * 注册所有的web handler，并启动http服务
   */
  private suspend fun startHttpServer() {
    logger.info("starting httpServer...")
    router.route().handler(TimeoutHandler.create(cfg.timeout))
    router.route().handler(LoggerHandler.create())
    router.route().handler(BodyHandler.create())
    router.get("/favicon.ico").handler(FaviconHandler.create(vertx))
    router.route().handler(this::handleReq).failureHandler(this::handleError)
    val server = httpServer.requestHandler(router).listen().await()
    logger.info("httpServer started at {}", server.actualPort())
  }

  private fun handleReq(ctx: RoutingContext) = launch {
    try {
      val unwrappedOneinReq = unwrapOneinReq(ctx)

      val targetReq = webClient.requestAbs(
        unwrappedOneinReq.method,
        cfg.gatewayUrl + unwrappedOneinReq.path
      )
      unwrappedOneinReq.headers?.let { targetReq.putHeaders(it) }
      unwrappedOneinReq.query?.let { targetReq.queryParams().addAll(it) }
      val serviceRes =
        if (unwrappedOneinReq.body == null) targetReq.send().await()
        else targetReq.sendBuffer(unwrappedOneinReq.body).await()

      val resStatus = serviceRes.statusCode()
      val originResBodyBuffer = serviceRes.bodyAsBuffer()
      val resBodyBuffer =
        if (resStatus < 200 || resStatus >= 400) originResBodyBuffer
        else wrapServiceResBody(originResBodyBuffer, resStatus).toBuffer()
      if (logger.isDebugEnabled) {
        logger.debug(
          "origin serviceRes code: {}, body: {}",
          resStatus,
          originResBodyBuffer.toString()
        )
      }

      ctx.response().putHeader(
        HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8"
      ).end(
        resBodyBuffer ?: Buffer.buffer("null")
      ).await()
    } catch (e: Throwable) {
      logger.error("handleReq error! req: {}", ctx.normalizedPath(), e)
      if (!ctx.response().ended()) {
        if (e is HttpException) ctx.fail(e.code, e)
        else ctx.fail(e)
      }
    }
  }

  private fun unwrapOneinReq(ctx: RoutingContext): UnwrappedOneinReq {
    val originPath = ctx.normalizedPath()
    val methodPos = originPath.lastIndexOf('/')
    if (methodPos < 0 || methodPos == originPath.length - 1)
      throw HttpException.badRequest("no method in path")
    var path = originPath.substring(0, methodPos)
    val methodStr = originPath.substring(methodPos + 1)
    val method = validHttpMethods[methodStr.uppercase()]
      ?: throw HttpException(HttpResponseStatus.METHOD_NOT_ALLOWED.code(), methodStr)
    val headers = ctx.request().headers()
    val oneinBody = ctx.body().asJsonObject()
    logger.debug("originPath: {}, oneinBody: {}", originPath, oneinBody)
    if (oneinBody == null)
      return UnwrappedOneinReq(path, method, headers, null, null)
    val paramPrefixSeparator = Config.paramPrefixSeparator
    val query = MultiMap.caseInsensitiveMultiMap()
    val newJsonBody = JsonObject()
    var newNonJsonBody: Buffer? = null
    for ((k, v) in oneinBody.map) {
      if (k == Config.instance.reqBodyKey) {
        // origin request body is non-jsonObject
        newNonJsonBody = Json.encodeToBuffer(v)
      } else if (v != null) {
        if (k.firstOrNull() == paramPrefixSeparator) {
          // _paramType_paramName => ["", paramType, paramName]
          val strValue = v.toString()
          val parts = k.split(paramPrefixSeparator, ignoreCase = false, limit = 3)
          if (parts.size == 3) {
            val paramType = parts[1]
            val paramName = parts[2]
            when (paramType) {
              "path" -> path = path.replace("[$paramName]", strValue)
              "header" -> headers.add(paramName, strValue)
              "query" -> query.add(paramName, strValue)
              else -> logger.warn("invalid param: [{}={}]", k, v)
            }
          } else logger.warn("invalid param: [{}={}]", k, v)
        } else {
          // normal json body param
          newJsonBody.put(k, v)
        }
      }
    }
    return UnwrappedOneinReq(
      path,
      method,
      if (headers.isEmpty) null else headers,
      if (query.isEmpty) null else query,
      newNonJsonBody ?: newJsonBody.toBuffer()
    )
  }

  private fun wrapServiceResBody(buffer: Buffer?, resStatus: Int): JsonObject {
    val decodeValue = if (buffer == null) null else Json.decodeValue(buffer)
    val map = mutableMapOf(
      cfg.resCodeKey to resStatus,
      cfg.resBodyKey to decodeValue
    )
    return JsonObject(wrapPrimitiveArrays(map))
  }

  private fun wrapPrimitiveArrays(root: MutableMap<String, Any?>?): MutableMap<String, Any?>? {
    if (root == null) return null
    for (entry in root) {
      val value = entry.value
      if (value is ArrayList<*>) {
        val first = value.firstOrNull()
        if (!(first == null || first is Map<*, *>)) {
          entry.setValue(value.map { mapOf(cfg.primitiveArrayKey to it) })
        }
      } else if (value is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        wrapPrimitiveArrays(value as MutableMap<String, Any?>)
      }
      // else: not list or map, ignore
    }
    return root
  }

  private fun handleError(ctx: RoutingContext) = launch {
    var statusCode = ctx.statusCode()
    if (statusCode < 0) statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
    val err = ctx.failure()
    if (err != null && err is HttpException) statusCode = err.code
    val message = err?.let { it.javaClass.name + ": " + it.message }
      ?: HttpResponseStatus.valueOf(statusCode).reasonPhrase()
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain;charset=utf-8")
      .end(message)
      .await()
  }
}
