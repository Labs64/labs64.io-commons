package io.labs64.authcontext.cedar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class LoggingDecisionListenerTest {

    private final LoggingDecisionListener listener = new LoggingDecisionListener();
    private ListAppender<ILoggingEvent> summaryAppender;
    private ListAppender<ILoggingEvent> detailAppender;
    private Logger summaryLogger;
    private Logger detailLogger;

    @BeforeEach
    void attach() {
        summaryLogger = (Logger) LoggerFactory.getLogger(LoggingDecisionListener.class);
        detailLogger = (Logger) LoggerFactory.getLogger("io.labs64.authcontext.cedar.detail");
        summaryAppender = new ListAppender<>();
        detailAppender = new ListAppender<>();
        summaryAppender.start();
        detailAppender.start();
        summaryLogger.addAppender(summaryAppender);
        detailLogger.addAppender(detailAppender);
        detailLogger.setLevel(Level.DEBUG); // testing-phase: detail enabled
    }

    @AfterEach
    void detach() {
        summaryLogger.detachAppender(summaryAppender);
        detailLogger.detachAppender(detailAppender);
    }

    private AuthorizationDecision decision(final boolean allowed, final boolean enforced, final String error) {
        return new AuthorizationDecision("payPayment", "Payment", "pay_1", allowed, enforced,
                List.of("policy0"), error, "alice", "t_100", "r-1");
    }

    @Test
    void summaryOmitsSensitiveFieldsAndCarriesOutcome() {
        listener.onDecision(decision(true, true, null));
        assertThat(summaryAppender.list).hasSize(1);
        ILoggingEvent e = summaryAppender.list.get(0);
        String msg = e.getFormattedMessage();
        assertThat(e.getLevel()).isEqualTo(Level.INFO);
        assertThat(msg).contains("outcome=enforced-allow", "decision=allow",
                "action=payPayment", "resourceType=Payment", "reasons=policy0", "requestId=r-1");
        // sensitive fields MUST NOT be in the summary
        assertThat(msg).doesNotContain("alice", "t_100", "pay_1");
    }

    @Test
    void detailCarriesSensitiveFieldsAtDebug() {
        listener.onDecision(decision(true, true, null));
        assertThat(detailAppender.list).hasSize(1);
        ILoggingEvent e = detailAppender.list.get(0);
        assertThat(e.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(e.getFormattedMessage()).contains("requestId=r-1", "user=alice",
                "tenant=t_100", "resource=Payment::pay_1");
    }

    @Test
    void shadowDenyIsWarn() {
        listener.onDecision(decision(false, false, null));
        assertThat(summaryAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(summaryAppender.list.get(0).getFormattedMessage()).contains("outcome=shadow-deny");
    }

    @Test
    void errorIsWarnAndRawTextStaysInDetailOnly() {
        listener.onDecision(decision(false, true, "engine exploded: secret-ish detail"));
        ILoggingEvent summary = summaryAppender.list.get(0);
        assertThat(summary.getLevel()).isEqualTo(Level.WARN);
        assertThat(summary.getFormattedMessage()).contains("decision=error", "outcome=enforced-deny");
        assertThat(summary.getFormattedMessage()).doesNotContain("secret-ish detail");
        assertThat(detailAppender.list.get(0).getFormattedMessage()).contains("error=engine exploded: secret-ish detail");
    }

    @Test
    void detailSuppressedWhenLoggerNotAtDebug() {
        detailLogger.setLevel(Level.INFO); // detail off (default posture)
        listener.onDecision(decision(true, true, null));
        assertThat(detailAppender.list).isEmpty();
        assertThat(summaryAppender.list).hasSize(1);
    }
}
