package com.ezcloudstore.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AttributeMapping;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.CfnUserPoolGroup;
import software.amazon.awscdk.services.cognito.CognitoDomainOptions;
import software.amazon.awscdk.services.cognito.OAuthFlows;
import software.amazon.awscdk.services.cognito.OAuthScope;
import software.amazon.awscdk.services.cognito.OAuthSettings;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.ProviderAttribute;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.StandardAttribute;
import software.amazon.awscdk.services.cognito.StandardAttributes;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientIdentityProvider;
import software.amazon.awscdk.services.cognito.UserPoolDomain;
import software.amazon.awscdk.services.cognito.UserPoolIdentityProviderGoogle;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;

/**
 * Cognito user pool with Hosted UI (ADR-0004). Admin capability = membership
 * in the "admin" group. Google federation (ADR-0006) is wired when a Google
 * OAuth client id is supplied via CDK context (-c googleClientId=...); its
 * secret is read from the SSM SecureString /ezcloudstore/google-client-secret,
 * so no secret ever lands in git or the CloudFormation template. Without the
 * context value the stack still deploys with email/password auth only.
 */
public class AuthStack extends Stack {

    public static final String GOOGLE_SECRET_SSM_PARAM = "/ezcloudstore/google-client-secret";

    private final UserPool userPool;
    private final UserPoolClient spaClient;
    private final UserPoolDomain domain;

    public AuthStack(Construct scope, String id, StackProps props,
                     List<String> callbackUrls, String googleClientId) {
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

        List<UserPoolClientIdentityProvider> providers = new ArrayList<>();
        providers.add(UserPoolClientIdentityProvider.COGNITO);

        UserPoolIdentityProviderGoogle googleIdp = null;
        boolean googleEnabled = googleClientId != null && !googleClientId.isBlank();
        if (googleEnabled) {
            googleIdp = UserPoolIdentityProviderGoogle.Builder.create(this, "GoogleIdp")
                    .userPool(userPool)
                    .clientId(googleClientId)
                    .clientSecretValue(SecretValue.ssmSecure(GOOGLE_SECRET_SSM_PARAM))
                    .scopes(List.of("openid", "email", "profile"))
                    .attributeMapping(AttributeMapping.builder()
                            .email(ProviderAttribute.GOOGLE_EMAIL)
                            .build())
                    .build();
            providers.add(UserPoolClientIdentityProvider.GOOGLE);
        }

        spaClient = UserPoolClient.Builder.create(this, "SpaClient")
                .userPool(userPool)
                .generateSecret(false) // public SPA client: Authorization Code + PKCE
                .authFlows(AuthFlow.builder().userSrp(true).build())
                .supportedIdentityProviders(providers)
                .oAuth(OAuthSettings.builder()
                        .flows(OAuthFlows.builder().authorizationCodeGrant(true).build())
                        .scopes(List.of(OAuthScope.OPENID, OAuthScope.EMAIL, OAuthScope.PROFILE))
                        .callbackUrls(callbackUrls)
                        .logoutUrls(callbackUrls)
                        .build())
                .build();

        // The client must be created after the Google IdP it lists as supported.
        if (googleIdp != null) {
            spaClient.getNode().addDependency(googleIdp);
        }

        domain = UserPoolDomain.Builder.create(this, "HostedUiDomain")
                .userPool(userPool)
                .cognitoDomain(CognitoDomainOptions.builder()
                        .domainPrefix("ezcloudstore")
                        .build())
                .build();

        CfnOutput.Builder.create(this, "UserPoolId").value(userPool.getUserPoolId()).build();
        CfnOutput.Builder.create(this, "SpaClientId").value(spaClient.getUserPoolClientId()).build();
        CfnOutput.Builder.create(this, "HostedUiDomainName").value(domain.getDomainName()).build();
        CfnOutput.Builder.create(this, "GoogleFederation")
                .value(googleEnabled ? "enabled" : "disabled (set -c googleClientId=... to enable)")
                .build();
    }

    public UserPool userPool() {
        return userPool;
    }

    public UserPoolClient spaClient() {
        return spaClient;
    }
}
