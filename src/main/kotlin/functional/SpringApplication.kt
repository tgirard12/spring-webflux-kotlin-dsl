package functional

import com.samskivert.mustache.Mustache
import functional.web.view.MustacheResourceTemplateLoader
import functional.web.view.MustacheViewResolver
import org.springframework.context.support.BeanDefinitionDsl
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.ipc.netty.http.server.HttpServer
import reactor.ipc.netty.tcp.BlockingNettyContext


class SpringApplication {
    var port = 8080
    var beans: BeanDefinitionDsl? = null

    private val messageSource = BeanDefinitionDsl().apply {
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

    private fun initServer(port: Int = this.port) {

        val context = GenericApplicationContext().apply {
            beans?.initialize(this)
            messageSource.initialize(this)
            refresh()
        }
        server = HttpServer.create(port)
        httpHandler = WebHttpHandlerBuilder.applicationContext(context).build()
    }

    fun start(port: Int = this.port) {
        initServer(port)
        nettyContext = server.start(ReactorHttpHandlerAdapter(httpHandler))
    }

    fun startAndAwait(port: Int = this.port) {
        initServer(port)
        server.startAndAwait(ReactorHttpHandlerAdapter(httpHandler), { nettyContext = it })
    }

    fun stop() {
        nettyContext.shutdown()
    }

}

fun springNettyApp(f: BeanDefinitionDsl.() -> Unit): SpringApplication =
        SpringApplication().apply {
            this.beans = BeanDefinitionDsl().apply(f)
        }

fun BeanDefinitionDsl.routes(f: BeanDefinitionDsl.BeanDefinitionContext.() -> RouterFunction<ServerResponse>) {
    bean("webHandler") {
        RouterFunctions.toWebHandler(f(), HandlerStrategies.builder().viewResolver(ref()).build())
    }
}

fun BeanDefinitionDsl.mustacheTemplate(prefix: String = "classpath:/templates/",
                                       suffix: String = ".mustache",
                                       f: MustacheViewResolver.() -> Unit) {
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