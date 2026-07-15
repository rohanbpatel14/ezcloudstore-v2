package com.ezcloudstore.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;

import java.util.Map;

/**
 * Verifies the observability stack synthesizes the alerting resources.
 * Uses an inline dummy Lambda + bare HTTP API so the test needs no build
 * artifacts (keeps it runnable in the backend CI job).
 */
class ObservabilityStackTest {

    private Template template;

    @BeforeEach
    void synth() {
        App app = new App();
        Stack fixture = new Stack(app, "Fixture");
        Function handler = Function.Builder.create(fixture, "Dummy")
                .runtime(Runtime.PYTHON_3_12)
                .handler("index.handler")
                .code(Code.fromInline("def handler(event, context):\n    return {}"))
                .build();
        HttpApi httpApi = HttpApi.Builder.create(fixture, "DummyApi").build();

        ObservabilityStack stack = new ObservabilityStack(app, "Obs", null,
                handler, httpApi, "alerts@example.com", 1.0);
        template = Template.fromStack(stack);
    }

    @Test
    void createsAnSnsTopicWithEmailSubscription() {
        template.resourceCountIs("AWS::SNS::Topic", 1);
        template.hasResourceProperties("AWS::SNS::Subscription", Match.objectLike(Map.of(
                "Protocol", "email",
                "Endpoint", "alerts@example.com")));
    }

    @Test
    void createsThreeAlarmsWiredToTheTopic() {
        template.resourceCountIs("AWS::CloudWatch::Alarm", 3);
        // every alarm notifies via an alarm action (the SNS topic)
        template.hasResourceProperties("AWS::CloudWatch::Alarm", Match.objectLike(Map.of(
                "AlarmActions", Match.anyValue())));
    }

    @Test
    void createsAnOpsDashboard() {
        template.resourceCountIs("AWS::CloudWatch::Dashboard", 1);
    }

    @Test
    void createsAMonthlyBudgetWithTwoNotifications() {
        template.resourceCountIs("AWS::Budgets::Budget", 1);
        template.hasResourceProperties("AWS::Budgets::Budget", Match.objectLike(Map.of(
                "Budget", Match.objectLike(Map.of(
                        "BudgetType", "COST",
                        "TimeUnit", "MONTHLY")))));
    }
}
