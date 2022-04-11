package ez.onein_proxy

import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServerOptions

class Config {
  /**
   * app启动选项
   */
  var vertx = VertxOptions()

  /**
   * http服务初始化选项，例如端口等
   */
  var httpServer = HttpServerOptions()

  /**
   * 目标微服务集群网关地址，完整的url路径，不能以`/`结尾
   */
  var gatewayUrl = ""

  /**
   * 每次收到web请求后，响应的超时时间，单位：毫秒
   */
  var timeout = 10000L

  /**
   * 当请求报文含有此字段时，表示原始请求报文不是jsonObject（可能为数组或primitive type等）
   */
  var reqBodyKey = "_v"

  /**
   * 当后端服务返回非jsonObject报文体时，用此key进行包装（{key: body}）
   */
  var resBodyKey = "_jsonBody"

  /**
   * 当后端服务返回报文中有非object数组时，用此key进行包装（[{key: value1}, {key: value2}, ...]）
   */
  var primitiveArrayKey = "_v"

  fun validate() = apply {
    if (gatewayUrl.isBlank()) throw IllegalArgumentException("gatewayUrl is empty")
    if (resBodyKey.isBlank()) throw IllegalArgumentException("resBodyKey is empty")
    if (primitiveArrayKey.isBlank()) throw IllegalArgumentException("primitiveArrayKey is empty")
  }

  companion object {
    @JvmStatic
    var instance = Config()

    @JvmStatic
    val paramPrefixSeparator = '_'
  }
}
