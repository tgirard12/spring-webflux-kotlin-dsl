package functional

import functional.web.UserHandler
import functional.web.router
import functional.web.staticRouter

val application = webfluxApplication {
    routes {
        router { router(ref(), ref()) }
        router(staticRouter())
    }
    bean<UserHandler>()

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
