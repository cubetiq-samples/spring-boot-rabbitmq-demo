package com.example.demo

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.springframework.util.ReflectionUtils
import reactor.core.publisher.FluxSink
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.Consumer

//@Component
class MessageCreatedEventPublisher(private val executor: Executor) : ApplicationListener<MessageCreatedEvent>,
    Consumer<FluxSink<MessageCreatedEvent>> {
    private val queue: BlockingQueue<MessageCreatedEvent> = LinkedBlockingQueue()

    override fun onApplicationEvent(event: MessageCreatedEvent) {
        this.queue.offer(event)
    }

    override fun accept(sink: FluxSink<MessageCreatedEvent>) {
        executor.execute {
            while (true) {
                try {
                    val event = queue.take()
                    sink.next(event)
                } catch (e: InterruptedException) {
                    ReflectionUtils.rethrowException(e)
                }
            }
        }
    }
}

data class MessageCreatedEvent(val message: String) : ApplicationEvent(message)