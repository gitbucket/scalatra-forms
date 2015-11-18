package io.github.gitbucket.scalatra.forms

import org.scalatra._

/**
 * Provides JavaScript file which contains some functions for client side validation.
 *
 * You can map this servlet to the path which you want as following:
 *
 * {{{
 * class ScalatraBootstrap extends LifeCycle {
 *   override def init(context: ServletContext) {
 *     context.mount(new ValidationJavaScriptProvider, "/commons/js")
 *     ...
 *   }
 * }
 * }}}
 *
 * In this case, you can refer validation.js in HTML as following:
 *
 * {{{
 * &lt;link href="/common/js/validation.js" rel="stylesheet"&gt;
 * }}}
 */
class ValidationJavaScriptProvider extends ScalatraServlet {

  var source: String = null
  val in = Thread.currentThread.getContextClassLoader.getResourceAsStream("validation.js")
  try {
    val bytes = new Array[Byte](in.available)
    in.read(bytes)
    source = new String(bytes, "UTF-8")
  } finally {
    in.close
  }

  get("/validation.js"){
    contentType = "application/x-javascript;charset=UTF-8"
    source
  }

}
