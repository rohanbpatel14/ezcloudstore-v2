package com.ezcloudstore.infra;

import software.amazon.awscdk.App;

/**
 * CDK entry point. Stacks arrive in phase 4:
 * StatefulStack (DynamoDB, S3), AuthStack (Cognito),
 * ApiStack (Lambda + HTTP API), WebStack (SPA + CloudFront).
 */
public final class CdkApp {

    public static void main(String[] args) {
        App app = new App();
        app.synth();
    }

    private CdkApp() {
    }
}
