package functional

import com.samskivert.mustache.Mustache
import functional.WebfluxApplicationDsl.Server.NETTY
import functional.WebfluxApplicationDsl.Server.TOMCAT
import functional.web.view.MustacheResourceTemplateLoader
import functional.web.view.MustacheViewResolver
import org.apache.catalina.connector.Connector
import org.apache.catalina.core.StandardContext
import org.apache.catalina.loader.WebappClassLoader
import org.apache.catalina.loader.WebappLoader
import org.apache.catalina.startup.Tomcat
import org.springframework.context.support.BeanDefinitionDsl
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.http.server.reactive.TomcatHttpHandlerAdapter
import org.springframework.util.ClassUtils
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
    private var server: Server = NETTY
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
            NETTY -> webServer = NettyWebServer()
            TOMCAT -> webServer = TomcatWebServer()
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


    enum class Server {
        NETTY, TOMCAT
    }


    interface WebServer {

        var port: Int
        fun run(httpHandler: HttpHandler, await: Boolean = true)
        fun stop()
    }

    class NettyWebServer : WebServer {

        override var port: Int = 8080
        private val server: HttpServer by lazy { HttpServer.create(port) }
        private lateinit var nettyContext: BlockingNettyContext

        override fun run(httpHandler: HttpHandler, await: Boolean) {
            if (await)
                server.startAndAwait(ReactorHttpHandlerAdapter(httpHandler), { nettyContext = it })
            else
                nettyContext = server.start(ReactorHttpHandlerAdapter(httpHandler))
        }

        override fun stop() {
            nettyContext.shutdown()
        }
    }

    class TomcatWebServer : WebServer {

        override var port: Int = 8080
        val tomcat = Tomcat()

        override fun run(httpHandler: HttpHandler, await: Boolean) {

            val servlet = TomcatHttpHandlerAdapter(httpHandler)

            val docBase = createTempDir("tomcat-docbase")
            val context = StandardContext()
            context.path = ""
            context.docBase = docBase.absolutePath
            context.addLifecycleListener(Tomcat.FixContextListener())
            context.parentClassLoader = ClassUtils.getDefaultClassLoader()
            val loader = WebappLoader(context.parentClassLoader)
            loader.loaderClass = WebappClassLoader::class.java.name
            loader.delegate = true
            context.loader = loader

            Tomcat.addServlet(context, "httpHandlerServlet", servlet)
            context.addServletMappingDecoded("/", "httpHandlerServlet")
            tomcat.host.addChild(context)

            val baseDir = createTempDir("tomcat")
            tomcat.setBaseDir(baseDir.absolutePath)
            val connector = Connector("org.apache.coyote.http11.Http11NioProtocol")
            tomcat.service.addConnector(connector)
            connector.setProperty("bindOnInit", "false")
            connector.port = port
            tomcat.connector = connector
            tomcat.host.autoDeploy = false

            tomcat.server.start()
            if (await)
                tomcat.server.await()
        }

        override fun stop() {
            tomcat.stop()
        }
    }
}

fun webfluxApplication(server: WebfluxApplicationDsl.Server, f: WebfluxApplicationDsl.() -> Unit) = WebfluxApplicationDsl().apply(f)