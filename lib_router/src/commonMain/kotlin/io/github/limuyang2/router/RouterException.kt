package io.github.limuyang2.router

/**
 * 路由库抛出的通用运行时异常。
 *
 * 当 URL 格式错误、缺少必要参数、找不到目标页面工厂或拦截器状态异常时抛出。
 *
 * General runtime exception thrown by the routing library.
 *
 * Thrown on invalid URL formats, missing required parameters, unmatched page factories, or interceptor state errors.
 *
 * @param message 异常描述信息 / Exception detail message.
 */
class RouterException(message: String) : RuntimeException(message)
