package com.example.demo

import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.redis.spring.ReactiveRedisLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.get
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.*

@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@EnableAsync
class DemoApplication @Autowired constructor(
    private val context: ApplicationContext,
    private val rabbitTemplate: RabbitTemplate,
) : CommandLineRunner {
    private val appName get() = context.environment["spring.application.name"]

    override fun run(vararg args: String?) {
//        val isRunner = context.environment["runner"]?.toBoolean() ?: true
//        if (isRunner) {
//            runBlocking { send() }
//        }

        println("Booted...")
    }

    suspend fun send() {
        run {
//            while (true) {
//            TimeUnit.SECONDS.sleep(5)
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                message = "Hello World",
                source = appName,
            )
            println("Sending $notification from $appName")
            rabbitTemplate.convertAndSend(
                "notification", notification
            )
        }
//        }
    }

    @Scheduled(cron = "0/5 * * * * *")
    @SchedulerLock(name = "sendScheduled", lockAtMostFor = "4s", lockAtLeastFor = "4s")
    fun sendScheduled() {
        // runBlocking { send() }
        println("Sending scheduled from $appName at ${Date()}")
    }

    @RabbitListener(queues = ["notification"])
    fun receive(notification: Notification) {
        println("RabbitMQ Received: $notification")

        try {
            val client = WebClient.create("http://localhost:8080/api/hello").post()
                .body(Mono.just(notification), Notification::class.java).retrieve().bodyToMono(String::class.java)
                //.retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
                .block()

            println("RabbitMQ_Request Response: $client")
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@RestController
@RequestMapping("/api")
class ApiController(
    private val applicationContext: ApplicationContext,
) {
    @PostMapping("/hello")
    fun hello(@RequestBody notification: Notification): Any {
        println("API Received from: $notification")
        return mapOf(
            "message" to "Hello World",
            "source" to applicationContext.environment["spring.application.name"],
            "timestamp" to Date(),
        )
    }
}

//@RestController
//@RequestMapping("/api")
//class ApiController(
//    eventPublisher: MessageCreatedEventPublisher,
//    private val applicationEventPublisher: ApplicationEventPublisher,
//) {
//    private val events: Flux<MessageCreatedEvent>
//    private val objectMapper = jacksonObjectMapper()
//
//    init {
//        events = Flux.create(eventPublisher).share()
//    }
//
//
//    @RequestMapping("/publish")
//    fun publish(@RequestParam(value = "message", defaultValue = "Hello World") message: String): Map<String, Any> {
//        applicationEventPublisher.publishEvent(MessageCreatedEvent(message))
//        return mapOf(
//            "status" to "Message was published!",
//            "message" to message,
//        )
//    }
//
//    @GetMapping("/stream/chat", produces = ["text/event-stream"])
//    @CrossOrigin(value = ["*"])
//    fun streamChat(): Flux<String> {
//        return this.events.map { event ->
//            try {
//                objectMapper.writeValueAsString(event) + "\n\n"
//            } catch (e: JsonProcessingException) {
//                throw RuntimeException(e)
//            }
//        }
//    }
//
//    private val counter = AtomicInteger(0)
//
//    @RequestMapping("/hello", method = [RequestMethod.GET, RequestMethod.POST])
//    fun hello(@RequestBody body: Any): Mono<String> {
//        println("Body Received: $body")
//
//        if (counter.get() < 2) {
//            counter.incrementAndGet()
//            return Mono.error {
//                RuntimeException("Internal Server Error")
//            }
//        }
//
//        return Mono.just("Hello World: Counter is: ${counter.get()}!")
//    }
//
//    @GetMapping("/stream", produces = ["text/event-stream"])
//    fun stream(): Flux<String> {
//        return Flux
//            .interval(Duration.ofSeconds(1))
//            .map { "Hello World: $it" }
//    }
//}

@Configuration
class Config {
    @Bean
    fun notificationQueue(): Queue {
        return Queue("notification", false)
    }

    @Bean
    fun lockProvider(reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory): LockProvider {
        return ReactiveRedisLockProvider(reactiveRedisConnectionFactory)
    }
}

data class Notification(
    val id: String,
    val message: String,
    val source: String? = null,
    val timestamp: Date = Date(),
) : java.io.Serializable