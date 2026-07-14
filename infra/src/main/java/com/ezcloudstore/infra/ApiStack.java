package com.ezcloudstore.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpJwtAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.lambda.Version;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Spring Boot 3 on Lambda with SnapStart (ADR-0001) behind an HTTP API.
 * JWT authorization happens at the gateway; /public/* routes skip it.
 * Least-privilege grants: table RW + files-bucket object RW only.
 */
public class ApiStack extends Stack {

    private final HttpApi httpApi;

    public ApiStack(Construct scope, String id, StackProps props,
                    Table table, Bucket filesBucket,
                    UserPool userPool, UserPoolClient spaClient,
                    String spaOrigin) {
        super(scope, id, props);

        String issuer = "https://cognito-idp." + getRegion() + ".amazonaws.com/" + userPool.getUserPoolId();

        Function handler = Function.Builder.create(this, "ApiHandler")
                .runtime(Runtime.JAVA_21)
                .handler("com.ezcloudstore.lambda.StreamLambdaHandler::handleRequest")
                .code(Code.fromAsset("../backend/target/ezcloudstore-backend-2.0.0-SNAPSHOT-lambda.zip"))
                .memorySize(1536)
                .timeout(Duration.seconds(30))
                .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                .environment(Map.of(
                        "EZCLOUDSTORE_TABLE", table.getTableName(),
                        "EZCLOUDSTORE_FILES_BUCKET", filesBucket.getBucketName(),
                        "COGNITO_ISSUER_URI", issuer,
                        "SPRING_MAIN_LAZY_INITIALIZATION", "false"))
                .build();

        table.grantReadWriteData(handler);
        filesBucket.grantReadWrite(handler);
        filesBucket.grantDelete(handler);

        // SnapStart requires invoking a published version, not $LATEST
        Version liveVersion = handler.getCurrentVersion();

        httpApi = HttpApi.Builder.create(this, "HttpApi")
                .apiName("ezcloudstore-api")
                .corsPreflight(CorsPreflightOptions.builder()
                        .allowOrigins(List.of(spaOrigin))
                        .allowMethods(List.of(CorsHttpMethod.GET, CorsHttpMethod.POST,
                                CorsHttpMethod.PATCH, CorsHttpMethod.DELETE, CorsHttpMethod.OPTIONS))
                        .allowHeaders(List.of("Authorization", "Content-Type"))
                        .maxAge(Duration.hours(1))
                        .build())
                .build();

        HttpLambdaIntegration integration = new HttpLambdaIntegration("ApiIntegration", liveVersion);

        HttpJwtAuthorizer authorizer = HttpJwtAuthorizer.Builder.create("CognitoJwt", issuer)
                .jwtAudience(List.of(spaClient.getUserPoolClientId()))
                .build();

        // Public: share resolution + health, no JWT at the gateway
        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/public/{proxy+}")
                .methods(List.of(HttpMethod.GET))
                .integration(integration)
                .build());
        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/actuator/health")
                .methods(List.of(HttpMethod.GET))
                .integration(integration)
                .build());

        // Everything else requires a valid Cognito JWT before Lambda is invoked
        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/{proxy+}")
                .methods(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE))
                .integration(integration)
                .authorizer(authorizer)
                .build());

        CfnOutput.Builder.create(this, "ApiUrl").value(httpApi.getApiEndpoint()).build();
    }

    public HttpApi httpApi() {
        return httpApi;
    }
}
