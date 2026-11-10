package io.github.azneotec.ticketing.catalogue;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Wraps a use case (an inbound port) so every call is a Micrometer {@link Observation}: one span
 * named after the use case (e.g. {@code PublishEvent}) between the HTTP/gRPC span and the SQL
 * spans, plus a timer {@code catalogue.use-case} tagged {@code use.case} and {@code error} (rate,
 * errors and duration per use case).
 *
 * <p>The application module stays free of Micrometer: the wrapping happens here, at the edge, with
 * a JDK dynamic proxy that implements the port interface and delegates to the real service.
 */
final class ObservedUseCases {

  static final String OBSERVATION_NAME = "catalogue.use-case";

  private ObservedUseCases() {}

  static <T> T observe(Class<T> port, T target, ObservationRegistry registry) {
    var useCase = port.getSimpleName();
    Object proxy =
        Proxy.newProxyInstance(
            port.getClassLoader(),
            new Class<?>[] {port},
            (self, method, args) -> {
              if (method.getDeclaringClass() == Object.class) {
                return invoke(method, target, args); // equals, hashCode, toString
              }
              return Observation.createNotStarted(OBSERVATION_NAME, registry)
                  .contextualName(useCase)
                  .lowCardinalityKeyValue("use.case", useCase)
                  .observeChecked(() -> invoke(method, target, args));
            });
    return port.cast(proxy);
  }

  /** Calls the real method and rethrows what it threw, not reflection's wrapper. */
  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException wrapped) {
      throw wrapped.getCause();
    }
  }
}
