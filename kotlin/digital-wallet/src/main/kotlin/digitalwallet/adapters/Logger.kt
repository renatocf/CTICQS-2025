package digitalwallet.adapters

class Logger {
    fun error(message: String, arguments: Map<String, String>? = null) {
        println(message)
        arguments?.let {
            println("Arguments:")
            it.forEach { (key, value) ->
                println("$key: $value")
            }
        }
    }
}