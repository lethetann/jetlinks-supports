package org.jetlinks.supports.event;

import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.event.TopicPayload;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class BrokerEventBusTest {


    @Test
    public void test() {
        BrokerEventBus eventBus = new BrokerEventBus();

        eventBus.subscribe(Subscription.of("test", new String[]{"/test/1/2/3"}), String.class)
                .doOnSubscribe(sub -> {
                    Mono.delay(Duration.ofSeconds(1))
                            .flatMap(i -> eventBus.publish("/test/1/2/3", "hello"))
                            .subscribe();
                })
                .take(Duration.ofSeconds(3))
                .as(StepVerifier::create)
                .expectNext("hello")
                .verifyComplete();
    }

    @Test
    public void testQueue() {
        BrokerEventBus eventBus = new BrokerEventBus();

        Flux.merge(
                eventBus.subscribe(Subscription.of("test", new String[]{"/test/1/2/3"}, Subscription.Feature.local, Subscription.Feature.shared), String.class),
                eventBus.subscribe(Subscription.of("test", new String[]{"/test/1/2/3"}, Subscription.Feature.local, Subscription.Feature.shared), String.class)
        )
                .doOnSubscribe(sub -> {
                    Mono.delay(Duration.ofSeconds(1))
                            .flatMap(i -> eventBus.publish("/test/1/2/3", "hello"))
                            .subscribe();
                })
                .take(Duration.ofSeconds(3))
                .as(StepVerifier::create)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    public void testTopic() {
        BrokerEventBus eventBus = new BrokerEventBus();

        Flux
                .merge(
                        eventBus.subscribe(Subscription.of("test", new String[]{"/test/1/2/3"},Subscription.Feature.local), String.class),
                        eventBus.subscribe(Subscription.of("test", new String[]{"/test/1/2/3"},Subscription.Feature.local), String.class)
                )
                .doOnSubscribe(sub -> {
                    Mono.delay(Duration.ofSeconds(1))
                            .flatMap(i -> eventBus.publish("/test/1/2/3", "hello"))
                            .subscribe();
                })
                .take(Duration.ofSeconds(3))
                .as(StepVerifier::create)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    public void testBroker() {
        BrokerEventBus eventBus = new BrokerEventBus();
        TestBroker broker = new TestBroker();
        eventBus.addBroker(broker);

        eventBus.publish("/test/123", "123")
                .then(Mono.fromSupplier(broker.counter::get))
                .as(StepVerifier::create)
                .expectNext(1L)
                .verifyComplete();
    }

    class TestBroker implements EventBroker {

        private AtomicLong counter = new AtomicLong();

        @Override
        public String getId() {
            return "test";
        }

        @Override
        public Flux<EventConnection> accept() {
            return Flux.just(new TestEventConnection());
        }

        class TestEventConnection implements EventConnection, EventConsumer {

            EmitterProcessor<TopicPayload> processor = EmitterProcessor.create();

            TestEventConnection() {
                processor.doOnNext(i -> counter.incrementAndGet())
                        .subscribe();
            }

            @Override
            public String getId() {
                return "127.0.0.1";
            }

            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public void doOnDispose(Disposable disposable) {

            }

            @Override
            public EventBroker getBroker() {
                return TestBroker.this;
            }

            @Override
            public Flux<Subscription> handleSubscribe() {
                return Flux.just(Subscription.of("admin", new String[]{"/test/**"}));
            }

            @Override
            public Flux<Subscription> handleUnSubscribe() {
                return Flux.empty();
            }

            @Override
            public FluxSink<TopicPayload> sink() {
                return processor.sink(FluxSink.OverflowStrategy.BUFFER);
            }

            @Override
            public void dispose() {

            }
        }
    }

}