package functional

import functional.web.Routes
import functional.web.UserHandler

val application = springNettyApp {
    bean { Routes(ref(), ref()) }
    bean<UserHandler>()
    routes {
        ref<Routes>().router()
    }
    mustacheTemplate {
    }
    profile("foo") {
        bean<Foo>()
    }
}


fun main(args: Array<String>) {
    application.startAndAwait()
}


// Only for profile:"foo"
class Foo
