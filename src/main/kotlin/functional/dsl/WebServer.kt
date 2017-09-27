package functional.dsl

import org.apache.catalina.connector.Connector
import org.apache.catalina.core.StandardContext
import org.apache.catalina.loader.WebappClassLoader
import org.apache.catalina.loader.WebappLoader
import org.apache.catalina.startup.Tomcat
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.http.server.reactive.TomcatHttpHandlerAdapter
import org.springframework.util.ClassUtils
import reactor.ipc.netty.http.server.HttpServer
import reactor.ipc.netty.tcp.BlockingNettyContext


/**
 * List of supporte server
 */
enum class Server {
    NETTY, TOMCAT
}

/**
 * Common server implementation
 */
interface WebServer {

    var port: Int
    fun run(httpHandler: HttpHandler, await: Boolean = true)
    fun stop()
}

/**
 * Netty
 */
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

/**
 * Tomcat
 */
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