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
    var port = 8080
    var routes = mutableListOf<RouterFunction<ServerResponse>>()

    private val webHandler = beans {
        bean("webHandler") {
            RouterFunctions.toWebHandler(
                    routes.reduce(RouterFunction<ServerResponse>::and),
                    HandlerStrategies.builder().viewResolver(ref()).build())
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

    private lateinit var nettyContext: BlockingNettyContext
    private lateinit var httpHandler: HttpHandler
    private lateinit var server: HttpServer

    private fun init(port: Int = this.port) {
        val context = GenericApplicationContext().apply {
            initialize(this)
            messageSource.initialize(this)
            webHandler.initialize(this)
            refresh()
        }
        server = HttpServer.create(port)
        httpHandler = WebHttpHandlerBuilder.applicationContext(context).build()
    }

    fun start(port: Int = this.port) {
        init(port)
        nettyContext = server.start(ReactorHttpHandlerAdapter(httpHandler))
    }

    fun startAndAwait(port: Int = this.port) {
        init(port)
        server.startAndAwait(ReactorHttpHandlerAdapter(httpHandler), { nettyContext = it })
    }

    fun stop() {
        nettyContext.shutdown()
    }

    //

    fun routes(f: MutableList<RouterFunction<ServerResponse>>.() -> Unit) = routes.apply(f)

    fun MutableList<RouterFunction<ServerResponse>>.addRouter(router: RouterFunction<ServerResponse>) {
        this.add(router)
    }

    fun MutableList<RouterFunction<ServerResponse>>.addRouter(
            f: BeanDefinitionDsl.BeanDefinitionContext.() -> RouterFunction<ServerResponse>) {
        this.add(f())
    }


    fun mustacheTemplate(prefix: String = "classpath:/templates/",
                         suffix: String = ".mustache",
                         f: MustacheViewResolver.() -> Unit = {}) {
        bean {
            MustacheResourceTemplateLoader(prefix, suffix).let {
                MustacheViewResolver(Mustache.compiler().withLoader(it)).apply {
                    setPrefix(prefix)
                    setSuffix(suffix)
                    f()
                }
            }
        }
    }
}

fun webfluxApplication(f: WebfluxApplicationDsl.() -> Unit) = WebfluxApplicationDsl().apply(f)