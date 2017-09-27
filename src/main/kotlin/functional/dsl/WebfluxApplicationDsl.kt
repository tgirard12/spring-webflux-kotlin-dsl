package functional.dsl

import com.samskivert.mustache.Mustache
import functional.app.web.view.MustacheResourceTemplateLoader
import functional.app.web.view.MustacheViewResolver
import org.springframework.context.support.BeanDefinitionDsl
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.adapter.WebHttpHandlerBuilder


class WebfluxApplicationDsl : BeanDefinitionDsl() {

    // Configuration
    private var port = 8080
    private var server: Server = Server.NETTY
    private var hasViewResolver: Boolean = false

    // Spring App
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
    private lateinit var webServer: WebServer

    // Beans
    private val beanDsl: BeanDefinitionDsl = BeanDefinitionDsl()
    private val routesDsl: RoutesDsl = RoutesDsl()
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
        this.port = port

        when (server) {
            Server.NETTY -> webServer = NettyWebServer()
            Server.TOMCAT -> webServer = TomcatWebServer()
        }
        webServer.port = this.port
        webServer.run(httpHandler, await)
    }

    fun stop() {
        webServer.stop()
    }

    // Routes
    fun routes(f: RoutesDsl.() -> Unit) = routesDsl.apply(f)

    fun router(router: RouterFunction<ServerResponse>) = routesDsl.router(router)
    fun router(f: BeanDefinitionDsl.BeanDefinitionContext.() -> RouterFunction<ServerResponse>) = routesDsl.router(f)

    // Beans
    fun beans(f: BeanDefinitionDsl.() -> Unit) = beanDsl.apply { f() }

    // Mustache
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

fun webfluxApplication(server: Server, f: WebfluxApplicationDsl.() -> Unit) = WebfluxApplicationDsl().apply(f)