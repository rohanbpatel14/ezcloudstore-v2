package com.ezcloudstore.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.List;

/**
 * Stack wiring. Account/region resolve from the CLI environment
 * (CDK_DEFAULT_ACCOUNT / CDK_DEFAULT_REGION) at synth time.
 *
 * Circular-dependency note: WebStack's CloudFront URL is needed by AuthStack
 * (OAuth callbacks) and ApiStack (CORS), but the SPA needs the API URL only
 * at build time (Vite env), so stacks deploy in the order
 * Stateful → Web → Auth → Api.
 */
public final class CdkApp {

    public static void main(String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();
        StackProps props = StackProps.builder().env(env).build();

        StatefulStack stateful = new StatefulStack(app, "EzCloudStoreStateful", props);
        WebStack web = new WebStack(app, "EzCloudStoreWeb", props);

        String spaOrigin = web.url();
        AuthStack auth = new AuthStack(app, "EzCloudStoreAuth", props,
                List.of(spaOrigin + "/", "http://localhost:5173/"));

        new ApiStack(app, "EzCloudStoreApi", props,
                stateful.table(), stateful.filesBucket(),
                auth.userPool(), auth.spaClient(), spaOrigin);

        app.synth();
    }

    private CdkApp() {
    }
}
