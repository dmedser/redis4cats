package object app {
  val redisHost: String = "localhost"
  val redisPort: Int    = 6380
  val redisUri: String  = s"redis://$redisHost:$redisPort"
}
