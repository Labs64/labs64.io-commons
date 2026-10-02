package io.labs64.authcontext.cerbos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import dev.cerbos.sdk.CerbosBlockingClient;

import io.labs64.authcontext.authorization.AuthorizationProperties;

class CerbosAutoConfigurationTest {

    private final CerbosAutoConfiguration configuration = new CerbosAutoConfiguration();

    @Test
    void pdpDeadlineDefaultsAboveTheSdkDefault() {
        // The Cerbos SDK default is 500 ms; the first call on a cold JVM also opens the channel.
        assertThat(new AuthorizationProperties().getTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(new AuthorizationProperties().isWarmUp()).isTrue();
    }

    @Test
    void warmUpCallsThePdpOnce() throws Exception {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        ApplicationRunner runner = configuration.cerbosWarmUp(client);

        runner.run(mock(ApplicationArguments.class));

        verify(client).check(any(dev.cerbos.sdk.builders.Principal.class),
                any(dev.cerbos.sdk.builders.Resource.class), any(String[].class));
    }

    @Test
    void warmUpFailureDoesNotStopTheApplication() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        when(client.check(any(dev.cerbos.sdk.builders.Principal.class),
                any(dev.cerbos.sdk.builders.Resource.class), any(String[].class)))
                .thenThrow(new RuntimeException("pdp down"));
        ApplicationRunner runner = configuration.cerbosWarmUp(client);

        org.assertj.core.api.Assertions.assertThatCode(() -> runner.run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
    }
}
