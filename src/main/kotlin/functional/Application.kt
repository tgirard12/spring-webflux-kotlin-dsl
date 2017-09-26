package functional

import functional.web.UserHandler
import functional.web.router
import functional.web.staticRouter
import org.springframework.context.support.beans

val application = webfluxApplication {
    routes {
        addRouter { router(ref(), ref()) }
        addRouter { staticRouter() }
    }
    beans {
        bean<UserHandler>()
    }
    mustacheTemplate()
    profile("foo") {
        bean<Foo>()
    }
}


fun main(args: Array<String>) {
    application.startAndAwait()
}


// Only for profile:"foo"
class Foo
