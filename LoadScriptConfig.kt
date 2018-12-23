class LoadScriptConfig(parser: ArgParser) {

    val websiteUrl by parser.positional("WEBSITE", help = "URL of site")

    val isVerbose by parser.flagging(
        "-v", "--verbose",
        help = "enable verbose mode"
    )

    val threads by parser.storing(
        "-t", "--threads",
        help = "Number of concurrent threads"
    ) { toInt() }.default(1).addValidator {
        if (value !in 1..100) {
            throw IllegalArgumentException("Number of threads must be between 1 and 100")
        }
    }

    val count by parser.storing(
        "-c", "--count",
        help = "Number of requests per thread"
    ) { toInt() }.default(3).addValidator {
        if (value !in 1..100) {
            throw IllegalArgumentException("Request per thread must be between 1 and 100")
        }
    }

    val delay by parser.storing(
        "-d", "--delay",
        help = "Delay between requests"
    ) { toLong() }.default(300).addValidator {
        if (value !in 0..100_000) {
            throw IllegalArgumentException("Delay must be between 0 and 100 000")
        }
    }

    val httpMethod by parser.mapping(
            "--get" to HttpMethodType.GET,
            "--post" to HttpMethodType.POST,
            "--patch" to HttpMethodType.PATCH,
            "--put" to HttpMethodType.PUT,
            "--head" to HttpMethodType.HEAD,
            "--delete" to HttpMethodType.DELETE,
            help = "Which http method to use").default(HttpMethodType.GET)

    val headersString by parser.storing(
        "-e", "--headers",
        help = "A colon separated list of headers for each request"
    ).default("")

    val body by parser.storing(
        "-b", "--body",
        help = "The body to send with each request"
    ).default("")

    val headers: Map<String, String>
        get() = headersString.split(",")
            .filter { it.contains("=") }
            .map {
                val headerString = it.split("=")
                Pair(headerString[0], headerString[1])
            }
            .toMap()

    fun newRequest(): Request {
      val requestUrl = FuelManager.instance.basePath!!
      val request = when(httpMethod) {
        HttpMethodType.GET -> requestUrl.httpGet()
        HttpMethodType.POST -> requestUrl.httpPost()
        HttpMethodType.PATCH -> requestUrl.httpPatch()
        HttpMethodType.PUT -> requestUrl.httpPut()
        HttpMethodType.HEAD -> requestUrl.httpHead()
        HttpMethodType.DELETE -> requestUrl.httpDelete()
      }
      request.body(body)
      return request
    }

    suspend fun startNewJob(): MutableMap<Int, Int> {
      val resultMap = mutableMapOf<Int, Int>()
      (1..count).forEach {
          newRequest().awaitResponse<IgnoredObject, IgnoredObject>(IgnoredObject.ignored).run {
            if(isVerbose) {
              println("Response code [${second.statusCode}]")
            }          
            val newValue = (resultMap.get(second.statusCode) ?: 0) + 1
            resultMap.put(second.statusCode, newValue)
          }
          delay(delay)
      }
      return resultMap
    }

    class IgnoredObject : Deserializable<IgnoredObject> {
        override fun deserialize(response: Response) = ignored

        companion object {
            val ignored = IgnoredObject()
        }
    }
}
