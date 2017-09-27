package functional.app.web

import com.samskivert.mustache.Mustache
import functional.app.locale
import org.springframework.context.MessageSource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType.*
import org.springframework.web.reactive.function.server.RenderingResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.toMono
import java.util.*

fun routerStatic() = router {
    resources("/**", ClassPathResource("static/"))
}

fun routerApi(userHandler: UserHandler) = router {
    "/api".nest {
        accept(APPLICATION_JSON).nest {
            GET("/users", userHandler::findAll)
        }
        accept(TEXT_EVENT_STREAM).nest {
            GET("/users", userHandler::stream)
        }
    }
}

fun routerHtml(userHandler: UserHandler, messageSource: MessageSource) = router {
    accept(TEXT_HTML).nest {
        GET("/") { ok().render("index") }
        GET("/sse") { ok().render("sse") }
        GET("/users", userHandler::findAllView)
    }
    resources("/**", ClassPathResource("static/"))
}.filter { request, next ->
    next.handle(request).flatMap {
        if (it is RenderingResponse) RenderingResponse.from(it).modelAttributes(attributes(request.locale(), messageSource)).build() else it.toMono()
    }
}

private fun attributes(locale: Locale, messageSource: MessageSource) = mutableMapOf<String, Any>(
        "i18n" to Mustache.Lambda { frag, out ->
            val tokens = frag.execute().split("|")
            out.write(messageSource.getMessage(tokens[0], tokens.slice(IntRange(1, tokens.size - 1)).toTypedArray(), locale))
        })