package io.opentracing.contrib.jaxrs2.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.CastUtils;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

/**
 * @author Pavol Loffay
 */
public class SpanClientRequestFilter implements ClientRequestFilter {

    private static final Logger log = Logger.getLogger(SpanClientRequestFilter.class.getName());

    protected static final String SPAN_PROP_ID = "activeClientSpan";

    private Tracer tracer;
    private List<ClientSpanDecorator> spanDecorators;

    public SpanClientRequestFilter(Tracer tracer, List<ClientSpanDecorator> spanDecorators) {
        this.tracer = tracer;
        this.spanDecorators = new ArrayList<>(spanDecorators);
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        Boolean tracingDisabled = CastUtils.cast(requestContext.getProperty(TracingProperties.TRACING_DISABLED), Boolean.class);

        if (tracingDisabled != null && tracingDisabled) {
            log.finest("Client tracing disabled");
            return;
        }

        // in case filter is registered twice
        if (requestContext.getProperty(SPAN_PROP_ID) != null) {
            return;
        }

        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(requestContext.getMethod())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

        SpanContext parentSpanContext = CastUtils.cast(requestContext.getProperty(TracingProperties.CHILD_OF),
                SpanContext.class);
        if (parentSpanContext != null) {
            spanBuilder.asChildOf(parentSpanContext);
        }

        Span span = spanBuilder.start();

        if (spanDecorators != null) {
            for (ClientSpanDecorator decorator: spanDecorators) {
                decorator.decorateRequest(requestContext, span);
            }
        }

        if (log.isLoggable(Level.FINEST)) {
            log.finest("Starting client span");
        }

        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new ClientHeadersInjectTextMap(requestContext.getHeaders()));
        requestContext.setProperty(SPAN_PROP_ID, new SpanWrapper(span));
    }
}