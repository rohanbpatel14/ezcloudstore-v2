package com.ezcloudstore.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.budgets.CfnBudget;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.CreateAlarmOptions;
import software.amazon.awscdk.services.cloudwatch.Dashboard;
import software.amazon.awscdk.services.cloudwatch.GraphWidget;
import software.amazon.awscdk.services.cloudwatch.IMetric;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.MetricOptions;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription;
import software.constructs.Construct;

import java.util.List;

/**
 * Free-tier observability (ADR-0007): an SNS alert topic (email), CloudWatch
 * alarms on Lambda errors/throttles and API 5xx, an ops dashboard, and an AWS
 * Budgets guard that emails if projected monthly spend exceeds the threshold —
 * the tripwire that keeps this "$0/month" honest. Echoes the original project's
 * documented CloudWatch/SNS story, actually built this time.
 */
public class ObservabilityStack extends Stack {

    public ObservabilityStack(Construct scope, String id, StackProps props,
                              Function handler, HttpApi httpApi,
                              String alertEmail, double monthlyBudgetUsd) {
        super(scope, id, props);

        Topic alerts = Topic.Builder.create(this, "Alerts")
                .topicName("ezcloudstore-alerts")
                .displayName("EzCloudStore alerts")
                .build();
        alerts.addSubscription(new EmailSubscription(alertEmail));
        SnsAction notify = new SnsAction(alerts);

        MetricOptions sum5m = MetricOptions.builder()
                .period(Duration.minutes(5))
                .statistic("Sum")
                .build();

        Alarm lambdaErrors = handler.metricErrors(sum5m).createAlarm(this, "LambdaErrors",
                CreateAlarmOptions.builder()
                        .threshold(1)
                        .evaluationPeriods(1)
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .treatMissingData(TreatMissingData.NOT_BREACHING)
                        .alarmDescription("EzCloudStore API Lambda reported errors")
                        .build());
        lambdaErrors.addAlarmAction(notify);

        Alarm lambdaThrottles = handler.metricThrottles(sum5m).createAlarm(this, "LambdaThrottles",
                CreateAlarmOptions.builder()
                        .threshold(1)
                        .evaluationPeriods(1)
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .treatMissingData(TreatMissingData.NOT_BREACHING)
                        .alarmDescription("EzCloudStore API Lambda is being throttled")
                        .build());
        lambdaThrottles.addAlarmAction(notify);

        Alarm api5xx = httpApi.metricServerError(sum5m).createAlarm(this, "Api5xx",
                CreateAlarmOptions.builder()
                        .threshold(1)
                        .evaluationPeriods(1)
                        .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .treatMissingData(TreatMissingData.NOT_BREACHING)
                        .alarmDescription("EzCloudStore API returned 5xx responses")
                        .build());
        api5xx.addAlarmAction(notify);

        Dashboard dashboard = Dashboard.Builder.create(this, "Dashboard")
                .dashboardName("EzCloudStore")
                .build();
        dashboard.addWidgets(
                graph("Lambda invocations & errors",
                        handler.metricInvocations(), handler.metricErrors(), handler.metricThrottles()),
                graph("Lambda duration (p95)", handler.metricDuration(
                        MetricOptions.builder().statistic("p95").build())),
                graph("API requests & errors",
                        httpApi.metricCount(), httpApi.metricClientError(), httpApi.metricServerError()),
                graph("API latency (p95)", httpApi.metricLatency(
                        MetricOptions.builder().statistic("p95").build())));

        // AWS Budgets: first two budgets are free. Alerts at 80% and 100% of the
        // monthly cap so a runaway resource is caught before it costs real money.
        CfnBudget.Builder.create(this, "MonthlyBudget")
                .budget(CfnBudget.BudgetDataProperty.builder()
                        .budgetName("ezcloudstore-monthly")
                        .budgetType("COST")
                        .timeUnit("MONTHLY")
                        .budgetLimit(CfnBudget.SpendProperty.builder()
                                .amount(monthlyBudgetUsd)
                                .unit("USD")
                                .build())
                        .build())
                .notificationsWithSubscribers(List.of(
                        budgetAlert(80, alertEmail),
                        budgetAlert(100, alertEmail)))
                .build();
    }

    private GraphWidget graph(String title, IMetric... metrics) {
        return GraphWidget.Builder.create()
                .title(title)
                .left(List.of(metrics))
                .width(12)
                .build();
    }

    private static CfnBudget.NotificationWithSubscribersProperty budgetAlert(double pct, String email) {
        return CfnBudget.NotificationWithSubscribersProperty.builder()
                .notification(CfnBudget.NotificationProperty.builder()
                        .notificationType("ACTUAL")
                        .comparisonOperator("GREATER_THAN")
                        .threshold(pct)
                        .thresholdType("PERCENTAGE")
                        .build())
                .subscribers(List.of(CfnBudget.SubscriberProperty.builder()
                        .subscriptionType("EMAIL")
                        .address(email)
                        .build()))
                .build();
    }
}
