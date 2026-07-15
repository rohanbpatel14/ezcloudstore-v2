package com.ezcloudstore.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.List;

/**
 * Stack wiring. Account/region resolve from the CLI environment
 * (CDK_DEFAULT_ACCOUNT / CDK_DEFAULT_REGION) at synth time.
 *
 * Optional context values:
 *   -c alertEmail=you@example.com   (default below) — SNS + budget notifications
 *   -c googleClientId=...           enables Google federation (secret from SSM)
 *   -c monthlyBudgetUsd=1           budget tripwire threshold
 *
 * Deploy order: Stateful → Web → Auth → Api → Observability.
 */
public final class CdkApp {

    private static final String DEFAULT_ALERT_EMAIL = "rohanbpatel14@gmail.com";

    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();
        StackProps props = StackProps.builder().env(env).build();

        String alertEmail = context(app, "alertEmail", DEFAULT_ALERT_EMAIL);
        String googleClientId = context(app, "googleClientId", null);
        double monthlyBudgetUsd = Double.parseDouble(context(app, "monthlyBudgetUsd", "1"));

        StatefulStack stateful = new StatefulStack(app, "EzCloudStoreStateful", props);
        WebStack web = new WebStack(app, "EzCloudStoreWeb", props);

        String spaOrigin = web.url();
        AuthStack auth = new AuthStack(app, "EzCloudStoreAuth", props,
                List.of(spaOrigin + "/", "http://localhost:5173/"), googleClientId);

        ApiStack api = new ApiStack(app, "EzCloudStoreApi", props,
                stateful.table(), stateful.filesBucket(),
                auth.userPool(), auth.spaClient(), spaOrigin);

        new ObservabilityStack(app, "EzCloudStoreObservability", props,
                api.handler(), api.httpApi(), alertEmail, monthlyBudgetUsd);

        app.synth();
    }

    private static String context(App app, String key, String fallback) {
        Object value = app.getNode().tryGetContext(key);
        return value != null ? value.toString() : fallback;
    }

    private CdkApp() {
    }
}
