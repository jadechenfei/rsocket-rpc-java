/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.rsocket.ipc;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.opentracing.Tracer;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.rpc.metrics.Metrics;
import io.rsocket.rpc.tracing.Tag;
import io.rsocket.rpc.tracing.Tracing;
import io.rsocket.util.ByteBufPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
public final class Client<I, O> {

  private final String service;
  private final Marshaller<I> marshaller;
  private final Unmarshaller<O> unmarshaller;
  private final RSocket rSocket;
  private final MeterRegistry meterRegistry;
  private final Tracer tracer;

  private Client(
      final String service,
      final Marshaller marshaller,
      final Unmarshaller unmarshaller,
      final RSocket rSocket,
      final MeterRegistry meterRegistry,
      final Tracer tracer) {
    this.service = service;
    this.marshaller = marshaller;
    this.unmarshaller = unmarshaller;
    this.rSocket = rSocket;
    this.meterRegistry = meterRegistry;
    this.tracer = tracer;
  }

  public interface R {
    M rsocket(RSocket rSocket);
  }

  public interface M {
    T noMeterRegistry();

    T meterRegistry(MeterRegistry registry);
  }

  public interface T {
    P noTracer();

    P tracer(Tracer tracer);
  }

  public interface P {
    <I> U<I> marshall(Marshaller<I> marshaller);
  }

  public interface U<I> {
    <O> Client<I, O> unmarshall(Unmarshaller<O> unmarshaller);
  }

  private <O> Function<? super Publisher<O>, ? extends Publisher<O>> metrics(String route) {
    return meterRegistry == null
        ? Function.identity()
        : Metrics.timed(meterRegistry, "rsocket.client", "service", service, "method", route);
  }

  private <O>
      Function<Map<String, String>, Function<? super Publisher<O>, ? extends Publisher<O>>> tracing(
          String route) {
    return tracer == null
        ? Tracing.trace()
        : Tracing.trace(
            tracer,
            route,
            Tag.of("rsocket.service", service),
            Tag.of("rsocket.rpc.role", "client"),
            Tag.of("rsocket.rpc.version", ""));
  }

  public Functions.RequestResponse<I, O> requestResponse(String route) {
    return genericRequestResponse(route, marshaller, unmarshaller);
  }

  public <X> Functions.RequestResponse<X, O> requestResponse(
      String route, Marshaller<X> marshaller) {
    return genericRequestResponse(route, marshaller, unmarshaller);
  }

  public <Y> Functions.RequestResponse<I, Y> requestResponse(
      String route, Unmarshaller<Y> unmarshaller) {
    return genericRequestResponse(route, marshaller, unmarshaller);
  }

  public <X, Y> Functions.RequestResponse<X, Y> requestResponse(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    return genericRequestResponse(route, marshaller, unmarshaller);
  }

  <X, Y> Functions.RequestResponse<X, Y> genericRequestResponse(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    Objects.requireNonNull(route);
    Objects.requireNonNull(marshaller);
    Objects.requireNonNull(unmarshaller);
    Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics = metrics(route);
    Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>> tracing =
        tracing(route);
    return (o, byteBuf) ->
        doRequestResponse(
            service, route, rSocket, marshaller, unmarshaller, o, byteBuf, metrics, tracing);
  }

  public Functions.RequestChannel<I, O> requestChannel(String route) {
    return genericRequestChannel(route, marshaller, unmarshaller);
  }

  public <X> Functions.RequestChannel<X, O> requestChannel(String route, Marshaller<X> marshaller) {
    return genericRequestChannel(route, marshaller, unmarshaller);
  }

  public <Y> Functions.RequestChannel<I, Y> requestChannel(
      String route, Unmarshaller<Y> unmarshaller) {
    return genericRequestChannel(route, marshaller, unmarshaller);
  }

  public <X, Y> Functions.RequestChannel<X, Y> requestChannel(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    return genericRequestChannel(route, marshaller, unmarshaller);
  }

  <X, Y> Functions.RequestChannel<X, Y> genericRequestChannel(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    Objects.requireNonNull(route);
    Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics = metrics(route);
    Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>> tracing =
        tracing(route);
    return (publisher, byteBuf) ->
        doRequestChannel(
            service,
            route,
            rSocket,
            marshaller,
            unmarshaller,
            publisher,
            byteBuf,
            metrics,
            tracing);
  }

  public Functions.RequestStream<I, O> requestStream(String route) {
    return genericRequestStream(route, marshaller, unmarshaller);
  }

  public <X> Functions.RequestStream<X, O> requestStream(String route, Marshaller<X> marshaller) {
    return genericRequestStream(route, marshaller, unmarshaller);
  }

  public <Y> Functions.RequestStream<I, Y> requestStream(
      String route, Unmarshaller<Y> unmarshaller) {
    return genericRequestStream(route, marshaller, unmarshaller);
  }

  public <X, Y> Functions.RequestStream<X, Y> requestStream(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    return genericRequestStream(route, marshaller, unmarshaller);
  }

  <X, Y> Functions.RequestStream<X, Y> genericRequestStream(
      String route, Marshaller<X> marshaller, Unmarshaller<Y> unmarshaller) {
    Objects.requireNonNull(route);
    Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics = metrics(route);
    Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>> tracing =
        tracing(route);
    return (o, byteBuf) ->
        doRequestStream(
            service, route, rSocket, marshaller, unmarshaller, o, byteBuf, metrics, tracing);
  }

  public Functions.FireAndForget<I> fireAndForget(String route) {
    return genericFireAndForget(route, marshaller);
  }

  public <X> Functions.FireAndForget<X> fireAndForget(String route, Marshaller<X> marshaller) {
    return genericFireAndForget(route, marshaller);
  }

  <X> Functions.FireAndForget<X> genericFireAndForget(String route, Marshaller<X> marshaller) {
    Objects.requireNonNull(route);
    Function<? super Publisher<Void>, ? extends Publisher<Void>> metrics = metrics(route);
    Function<Map<String, String>, Function<? super Publisher<Void>, ? extends Publisher<Void>>>
        tracing = tracing(route);
    return (o, byteBuf) ->
        doFireAndForget(service, route, rSocket, marshaller, o, byteBuf, metrics, tracing);
  }

  private static class Builder implements P, U, R, M, T {
    private final String service;
    private Marshaller marshaller;
    private MeterRegistry meterRegistry;
    private Tracer tracer;
    private RSocket rSocket;

    private Builder(String service) {
      this.service = service;
    }

    @Override
    public <I> U<I> marshall(Marshaller<I> marshaller) {
      this.marshaller = Objects.requireNonNull(marshaller);
      return this;
    }

    @Override
    public Client unmarshall(Unmarshaller unmarshaller) {
      Objects.requireNonNull(unmarshaller);
      return new Client(service, marshaller, unmarshaller, rSocket, meterRegistry, tracer);
    }

    @Override
    public M rsocket(RSocket rSocket) {
      this.rSocket = Objects.requireNonNull(rSocket);
      return this;
    }

    @Override
    public T noMeterRegistry() {
      return this;
    }

    @Override
    public T meterRegistry(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
      return this;
    }

    @Override
    public P noTracer() {
      return this;
    }

    @Override
    public P tracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }
  }

  private <X> Mono<Void> doFireAndForget(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<X> marshaller,
      final X o,
      final ByteBuf metadata,
      Function<? super Publisher<Void>, ? extends Publisher<Void>> metrics,
      Function<Map<String, String>, Function<? super Publisher<Void>, ? extends Publisher<Void>>>
          tracing) {
    try {
      HashMap<String, String> map = new HashMap<>();
      ByteBuf d = marshaller.apply(o);
      ByteBuf t = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, map);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, t, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.fireAndForget(payload).transform(metrics).transform(tracing.apply(map));
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  private <X, Y> Mono<Y> doRequestResponse(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<X> marshaller,
      final Unmarshaller<Y> unmarshaller,
      final X o,
      final ByteBuf metadata,
      Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics,
      Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>>
          tracing) {
    try {
      HashMap<String, String> map = new HashMap<>();
      ByteBuf d = marshaller.apply(o);
      ByteBuf t = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, map);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, t, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.requestResponse(payload)
          .map(
              p -> {
                try {
                  return unmarshaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              })
          .transform(metrics)
          .transform(tracing.apply(map));
    } catch (Throwable t) {
      return Mono.error(t);
    }
  }

  private <X, Y> Flux<Y> doRequestStream(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<X> marshaller,
      final Unmarshaller<Y> unmarshaller,
      final X o,
      final ByteBuf metadata,
      Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics,
      Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>>
          tracing) {
    try {
      HashMap<String, String> map = new HashMap<>();
      ByteBuf d = marshaller.apply(o);
      ByteBuf t = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, map);
      ByteBuf m = Metadata.encode(ByteBufAllocator.DEFAULT, service, route, t, metadata);

      Payload payload = ByteBufPayload.create(d, m);
      return r.requestStream(payload)
          .map(
              p -> {
                try {
                  return unmarshaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              })
          .transform(metrics)
          .transform(tracing.apply(map));
    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  private <X, Y> Flux<Y> doRequestChannel(
      final String service,
      final String route,
      final RSocket r,
      final Marshaller<X> marshaller,
      final Unmarshaller<Y> unmarshaller,
      final Publisher<X> pub,
      final ByteBuf metadata,
      Function<? super Publisher<Y>, ? extends Publisher<Y>> metrics,
      Function<Map<String, String>, Function<? super Publisher<Y>, ? extends Publisher<Y>>>
          tracing) {
    try {

      HashMap<String, String> map = new HashMap<>();

      Flux<Payload> input =
          Flux.from(pub)
              .map(
                  o -> {
                    ByteBuf d = marshaller.apply(o);
                    ByteBuf t = Tracing.mapToByteBuf(ByteBufAllocator.DEFAULT, map);
                    ByteBuf m =
                        Metadata.encode(ByteBufAllocator.DEFAULT, service, route, t, metadata);

                    return ByteBufPayload.create(d, m);
                  });

      return r.requestChannel(input)
          .map(
              p -> {
                try {
                  return unmarshaller.apply(p.sliceData());
                } finally {
                  p.release();
                }
              })
          .transform(metrics)
          .transform(tracing.apply(map));

    } catch (Throwable t) {
      return Flux.error(t);
    }
  }

  public static R service(String service) {
    return new Builder(Objects.requireNonNull(service));
  }
}