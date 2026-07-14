package com.ezcloudstore.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.CfnUserPoolGroup;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolDomain;
import software.constructs.Construct;

import java.util.List;

/**
 * Cognito user pool with Hosted UI (ADR-0004). Google federation is added
 * post-deploy once OAuth client credentials exist (needs a Google Cloud
 * console step that can't be automated here); email/password works day one.
 * Admin capability = membership in the "admin" group.
 */
public class AuthStack extends Stack {

    private final UserPool userPool;
    private final UserPoolClient spaClient;
    private final UserPoolDomain domain;

    public AuthStack(Construct scope, String id, StackProps props, List<String> callbackUrls) {
        super(scope, id, props);

        userPool = UserPool.Builder.create(this, "UserPool")
                .userPoolName("ezcloudstore-users")
                .selfSignUpEnabled(true)
                .signInAliases(SignInAliases.builder().email(true).build())
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder().required(true).mutable(false).build())
                        .build())
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(12)
                        .requireLowercase(true)
                        .requireUppercase(true)
                        .requireDigits(true)
                        .build())
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        CfnUserPoolGroup.Builder.create(this, "AdminGroup")
                .userPoolId(userPool.getUserPoolId())
                .groupName("admin")
                .description("Members may call /admin/** endpoints")
                .build();

        spaClient = UserPoolClient.Builder.create(this, "SpaClient")
                .userPool(userPool)
                .generateSecret(false) // public SPA client: Authorization Code + PKCE
                .authFlows(AuthFlow.builder().userSrp(true).build())
                .oAuth(OAuthSettings.builder()
                        .flows(OAuthFlows.builder().authorizationCodeGrant(true).build())
                        .scopes(List.of(OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE))
                        .callbackUrls(callbackUrls)
                        .logoutUrls(callbackUrls)
                        .build())
                .build();

        domain = UserPoolDomain.Builder.create(this, "HostedUiDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix("ezcloudstore")
                        .build())
                .build();

        CfnOutput.Builder.create(this, "UserPoolId").value(userPool.getUserPoolId()).build();
        CfnOutput.Builder.create(this, "SpaClientId").value(spaClient.getUserPoolClientId()).build();
        CfnOutput.Builder.create(this, "HostedUiDomainName").value(domain.getDomainName()).build();
    }

    public UserPool userPool() {
        return userPool;
    }

    public UserPoolClient spaClient() {
        return spaClient;
    }
}
