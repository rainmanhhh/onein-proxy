package ez.onein_proxy

import io.netty.handler.codec.http.HttpResponseStatus

class HttpException(val code: Int, message: String): Exception(message) {
  companion object {
    fun badRequest(message: String) = HttpException(HttpResponseStatus.BAD_REQUEST.code(), message)
    fun internalErr(message: String) = HttpException(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), message)
  }
}

fun HttpResponseStatus.err(message: String) = HttpException(code(), message)
