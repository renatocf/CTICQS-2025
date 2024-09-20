package adapters

class Logger {
  def error(message: String, arguments: Option[Map[String, String]] = None): Unit = {
    println(message)
    arguments.foreach { args =>
      println("Arguments:")
      args.foreach { case (key, value) =>
        println(s"$key: $value")
      }
    }
  }
}
