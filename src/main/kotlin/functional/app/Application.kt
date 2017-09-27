package functional.app

import functional.app.web.UserHandler
import functional.app.web.routerApi
import functional.app.web.routerHtml
import functional.app.web.routerStatic
import functional.dsl.webfluxApplication

val application = webfluxApplication(TOMCAT) {
    // group routers
    routes {
        router { routerApi(ref()) }
        router(routerStatic())
    }
    router { routerHtml(ref(), ref()) }

    // group beans
    beans {
        bean<UserHandler>()
        bean<Baz>()  // default constructor injection
    }
    bean<Bar>()

    mustacheTemplate()

    profile("foo") {
        bean<Foo>()
    }
}


fun main(args: Array<String>) {
    application.run()
}

// Only for profile:"foo"
class Foo

class Bar
class Baz(val bar: Bar)
