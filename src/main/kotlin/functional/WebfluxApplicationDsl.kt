package functional

import com.samskivert.mustache.Mustache
import functional.web.view.MustacheResourceTemplateLoader
import functional.web.view.MustacheViewResolver
import org.springframework.context.support.BeanDefinitionDsl
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.context.support.beans
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.ipc.netty.http.server.HttpServer
import reactor.ipc.netty.tcp.BlockingNettyContext


class WebfluxApplicationDsl : BeanDefinitionDsl() {

    // Configuration
    private var port = 8080
    private var hasViewResolver: Boolean = false

    // Netty Server
    private val context: GenericApplicationContext by lazy {
        GenericApplicationContext().apply {
            initialize(this)
            webHandler.initialize(this)
            messageSource.initialize(this)
            routes.initialize(this)
            refresh()
        }
    }
    private val httpHandler: HttpHandler by lazy { WebHttpHandlerBuilder.applicationContext(context).build() }
    private val server: HttpServer by lazy { HttpServer.create(port) }
    private lateinit var nettyContext: BlockingNettyContext

    // Beans
    private lateinit var routesDsl: RoutesDsl
    private val routes = beans {
        bean {
            routesDsl.merge(this)
        }
    }
    private val webHandler = beans {
        bean("webHandler") {
            RouterFunctions.toWebHandler(ref(), HandlerStrategies.builder().apply {
                if (hasViewResolver) viewResolver(ref())
            }.build())
        }
    }
    private val messageSource = beans {
        bean("messageSource") {
            ReloadableResourceBundleMessageSource().apply {
                setBasename("messages")
                setDefaultEncoding("UTF-8")
            }
        }
    }

    fun run(await: Boolean = true, port: Int = this.port) {
        if (await)
            server.startAndAwait(ReactorHttpHandlerAdapter(httpHandler), { nettyContext = it })
        else
            nettyContext = server.start(ReactorHttpHandlerAdapter(httpHandler))
    }

    fun stop() {
        nettyContext.shutdown()
    }

    fun routes(f: RoutesDsl.() -> Unit) {
        routesDsl = RoutesDsl().apply(f)
    }

    fun mustacheTemplate(prefix: String = "classpath:/templates/",
                         suffix: String = ".mustache",
                         f: MustacheViewResolver.() -> Unit = {}) {
        bean {
            hasViewResolver = true
            MustacheResourceTemplateLoader(prefix, suffix).let {
                MustacheViewResolver(Mustache.compiler().withLoader(it)).apply {
                    setPrefix(prefix)
                    setSuffix(suffix)
                    f()
                }
            }
        }
    }

    class RoutesDsl {
        private val routes = mutableListOf<BeanDefinitionDsl.BeanDefinitionContext.() -> RouterFunction<ServerResponse>>()

        fun router(router: RouterFunction<ServerResponse>) {
            routes.add({ router })
        }

        fun router(f: BeanDefinitionDsl.BeanDefinitionContext.() -> RouterFunction<ServerResponse>) {
            routes.add(f)
        }

        fun merge(f: BeanDefinitionDsl.BeanDefinitionContext): RouterFunction<ServerResponse> =
                routes.map { it.invoke(f) }.reduce(RouterFunction<ServerResponse>::and)
    }
}

fun webfluxApplication(f: WebfluxApplicationDsl.() -> Unit) = WebfluxApplicationDsl().apply(f)