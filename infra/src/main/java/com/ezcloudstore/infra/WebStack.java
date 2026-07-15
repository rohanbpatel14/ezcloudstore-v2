package com.ezcloudstore.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ErrorResponse;
import software.amazon.awscdk.services.cloudfront.GeoRestriction;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

import java.util.List;

/**
 * React SPA on a private S3 bucket behind CloudFront (OAC). SPA routing:
 * 403/404 rewrite to /index.html. The CloudFront domain is the free-tier
 * public URL; a custom domain can be layered on later without changes here.
 */
public class WebStack extends Stack {

    private final Distribution distribution;

    public WebStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        Bucket spaBucket = Bucket.Builder.create(this, "SpaBucket")
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        distribution = Distribution.Builder.create(this, "Distribution")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(S3BucketOrigin.withOriginAccessControl(spaBucket))
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .build())
                .defaultRootObject("index.html")
                // OFAC-sanctioned countries — CloudFront geo-restriction is free.
                // Supersedes the original's Cuba-only block with the full embargo list.
                .geoRestriction(GeoRestriction.denylist("CU", "IR", "KP", "SY"))
                .errorResponses(List.of(
                        ErrorResponse.builder().httpStatus(403)
                                .responseHttpStatus(200).responsePagePath("/index.html").build(),
                        ErrorResponse.builder().httpStatus(404)
                                .responseHttpStatus(200).responsePagePath("/index.html").build()))
                .build();

        BucketDeployment.Builder.create(this, "DeploySpa")
                .sources(List.of(Source.asset("../frontend/dist")))
                .destinationBucket(spaBucket)
                .distribution(distribution)
                .distributionPaths(List.of("/*"))
                .build();

        CfnOutput.Builder.create(this, "WebUrl")
                .value("https://" + distribution.getDistributionDomainName())
                .build();
    }

    public Distribution distribution() {
        return distribution;
    }

    public String url() {
        return "https://" + distribution.getDistributionDomainName();
    }
}
