package io.digital.patterns.workflow.aws;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.retry.RetryUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
public class AwsConfiguration {


    private final AwsProperties awsProperties;

    public AwsConfiguration(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }


    @Bean
    public AWSStaticCredentialsProvider credentials(){
        BasicAWSCredentials basicAWSCredentials =
                new BasicAWSCredentials(awsProperties.getCredentials().getAccessKey()
                , awsProperties.getCredentials().getSecretKey());
       return  new AWSStaticCredentialsProvider(basicAWSCredentials);

    }

    @Bean
    @Primary
    public AmazonS3 awsS3Client() {
        RetryPolicy retryPolicy = new RetryPolicy(
                (originalRequest, exception, retriesAttempted) -> {
                    if (exception.getCause() instanceof IOException) {
                        return true;
                    }
                    if (exception instanceof AmazonServiceException) {
                        AmazonServiceException ase = (AmazonServiceException)exception;
                        if (RetryUtils.isRetryableServiceException(ase)) return true;
                        if (RetryUtils.isThrottlingException(ase)) return true;
                        if (ase.getStatusCode() == 404 || ase.getErrorCode().contains("NoSuchKey")) {
                            return true;
                        }
                        return RetryUtils.isClockSkewError(ase);
                    }

                    return false;
                },
                new PredefinedBackoffStrategies.ExponentialBackoffStrategy(
                        Math.toIntExact(TimeUnit.SECONDS.toMillis(5)),
                        Math.toIntExact(TimeUnit.SECONDS.toMillis(5))
                ), 3, true);

        return AmazonS3ClientBuilder
                .standard()
                .withClientConfiguration(new ClientConfiguration()
                .withRetryPolicy(retryPolicy))
                .withRegion(Regions.fromName(awsProperties.getRegion()))
                .withCredentials(credentials()).build();
    }

    @Bean
    public AmazonSimpleEmailService amazonSimpleEmailService() {
       return AmazonSimpleEmailServiceClientBuilder.standard()
               .withRegion(Regions.fromName(awsProperties.getRegion()))
               .withCredentials(credentials()).build();
    }

    @Bean
    public AmazonSNSClient amazonSNSClient() {
       return (AmazonSNSClient) AmazonSNSClient.builder()
                .withRegion(Regions.fromName(awsProperties.getSnsRegion()))
                .withCredentials(credentials()).build();

    }

}
