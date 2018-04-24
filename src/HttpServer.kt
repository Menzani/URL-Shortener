package it.menzani.urlshortener

import java.io.OutputStreamWriter
import java.io.Writer
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    val executor = Executors.newCachedThreadPool()
    val server = HttpServer(executor)
    executor.execute(server)
}

class HttpServer(private val executor: Executor) : Runnable {
    private val dataSource = DataSource()
    private val socket = ServerSocket(dataSource.port())

    init {
        println("Started HTTP server, listening on port ${socket.localPort}.")
    }

    override fun run() {
        while (true) handleRequest()
    }

    private fun handleRequest() {
        val handler = RequestHandler(socket.accept(), dataSource)
        executor.execute(handler)
    }
}

class RequestHandler(private val socket: Socket, private val dataSource: DataSource) : Runnable {
    companion object {
        private const val LINE_SEPARATOR = "\r\n"
    }

    private val input = Scanner(socket.getInputStream()).useDelimiter(LINE_SEPARATOR)
    private val output: Writer = OutputStreamWriter(socket.getOutputStream())

    override fun run() {
        val response = parseRequest()
        submitResponse(response)
        socket.close()
    }

    private fun parseRequest(): Response {
        if (!input.hasNext()) {
            return Response.BadRequest
        }
        val requestLine = input.next()
        if (!requestLine.startsWith("GET /")) {
            return Response.Ok("Serving GET requests only.")
        }
        val protocolVersionPosition = requestLine.indexOf(" HTTP/", 5)
        return when {
            protocolVersionPosition < 5 -> Response.BadRequest
            protocolVersionPosition == 5 -> Response.Ok("Please supply the code.")
            protocolVersionPosition > 105 -> Response.Ok("Code must not be longer than 100 characters.")
            else -> {
                val code = requestLine.substring(5, protocolVersionPosition)
                if (code in arrayOf("index.html", "index.htm", "index.php", "favicon.ico")) {
                    return Response.NotFound
                }
                if (code == "robots.txt") {
                    return Response.Ok("User-agent: *${LINE_SEPARATOR}Disallow: /")
                }
                if ("%20" in code) {
                    return Response.Ok("Code may not contain spaces.")
                }
                var url = dataSource.lookup(code)
                println("$code -> $url")
                if (url == null) {
                    return Response.Ok("Code not found.")
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                Response.MovedPermanently(url)
            }
        }
    }

    private fun submitResponse(response: Response) {
        output.use {
            it.write("HTTP/1.1 ")
            it.write(response.status)
            it.writeln()
            when (response) {
                is Response.Ok -> {
                    it.write("Content-Type: text/plain; charset=UTF-8")
                    it.write("Content-Length: ")
                    it.write(response.message.toByteArray().size)
                    it.writelnln()
                    it.write(response.message)
                }
                is Response.MovedPermanently -> {
                    it.write("Location: ")
                    it.write(response.url)
                    it.writelnln()
                }
                is Response.BadRequest, is Response.NotFound -> {
                    it.writelnln()
                }
            }
        }
    }

    private fun Writer.writeln() {
        this.write(LINE_SEPARATOR)
    }

    private fun Writer.writelnln() {
        this.writeln()
        this.writeln()
    }
}

sealed class Response(val status: String) {
    class Ok(val message: String) : Response("200 OK")
    class MovedPermanently(val url: String) : Response("301 Moved Permanently")
    object BadRequest : Response("400 Bad Request")
    object NotFound : Response("404 Not Found")
}