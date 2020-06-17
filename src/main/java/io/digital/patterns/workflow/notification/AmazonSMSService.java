package io.digital.patterns.workflow.notification;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
public class AmazonSMSService {

    public static String SMS_FAILURE = "SMS_FAILURE";

    private final AmazonSNSClient amazonSNSClient;

    public AmazonSMSService(AmazonSNSClient amazonSNSClient) {
        this.amazonSNSClient = amazonSNSClient;
    }

    public String sendSMS(String phoneNumber, String message) {
        log.info("Sending SMS message to {}", phoneNumber);
        try {
            Assert.isTrue(StringUtils.isNotBlank(phoneNumber), "Phone number cannot be null or empty");
            Assert.isTrue(StringUtils.isNotBlank(message), "Message cannot be null or empty");

            PublishResult result = amazonSNSClient.publish(new PublishRequest()
                    .withMessage(message)
                    .withPhoneNumber(phoneNumber)
            );
            log.info("SMS result '{}' for sending message to '{}'", result.getMessageId(), phoneNumber);
            return result.getMessageId();
        } catch (Exception e) {
            log.error("Failed to send SMS for '{}'", phoneNumber, e);
            throw new BpmnError(SMS_FAILURE, e.getMessage(), e);
        }
    }
}
