package object app {
  val redisHost: String = "localhost"
  val redisPort: Int    = 6379
  val redisUri: String  = s"redis://$redisHost:$redisPort"

  def redisUri(host: String, port: Int): String = s"redis://$host:$port"
}
