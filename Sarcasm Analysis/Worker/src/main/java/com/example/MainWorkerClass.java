package com.example;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainWorkerClass {

    private String sqsWorkerQueueInput = "Sqs-Manager-Workers-Input.fifo";
    private String sqsWorkerQueueOutput = "Sqs-workers-Manager-Output.fifo";
    private SqsClient sqsClient;
    public static Region region1 = Region.US_WEST_2;
    private SentimentAnalysisHandler sentimentAnalysisHandler;
    private NamedEntityRecognitionHandler namedEntityRecognitionHandler;
    private boolean terminate;

    MainWorkerClass() {
        sqsClient = SqsClient.builder().region(region1).build();
        sentimentAnalysisHandler = new SentimentAnalysisHandler();
        namedEntityRecognitionHandler = new NamedEntityRecognitionHandler();
        terminate = false;
    }

    public static void main(String[] args) {

        MainWorkerClass worker = new MainWorkerClass();
        while (!worker.getTerminate()) {
            worker.recieveMessageFromManager();
        }
    }

    boolean getTerminate(){
        return this.terminate;
    }
    
    public void recieveMessageFromManager() { // per 1 message

        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(sqsWorkerQueueInput)
                    .build())
                    .queueUrl();

            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .visibilityTimeout(60) // Visibility timeout in seconds
                    .waitTimeSeconds(5)
                    .messageAttributeNames("All") // Maximum number of messages to retrieve
                    .build();

            ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(receiveRequest);
            if (receiveResponse.messages().isEmpty()) {
                // No messages were received, the loop will start over and check again.
                System.out.println("Queue is empty, checking again...");
            } else {
                for (Message message : receiveResponse.messages()) {

                    // Delete the received message
                    DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                            .queueUrl(sqsWorkerQueueInput)
                            .receiptHandle(message.receiptHandle())
                            .build();

                    sqsClient.deleteMessage(deleteRequest);

                    processMessageReceived(message);
                }
            }
        } catch (Exception e) {
            System.err.println("[Worker] Error receiving messages: " + e.getMessage());
        }
    }

    void processMessageReceived(Message message) {

        String review = message.body();
        String sentimentScore = sentimentAnalysisHandler.analyzeSentiment(review);
        String namedEntities = namedEntityRecognitionHandler.printEntities(review);

        // Extract attributes
        String responseQueueUrl = messageAttribute(message, "Response-Sqs-Url");
        terminate = messageAttribute(message, "terminate").equals("terminate");
        if (terminate) return;
        String stars = messageAttribute(message, "Stars");
        String reviewLink = messageAttribute(message, "ReviewUrl");
        String keyFile = messageAttribute(message, "KeyFile");

        // Create message attributes
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        addAttribute(messageAttributes, "SentimentScore", sentimentScore);
        if (!namedEntities.isEmpty()) {
            addAttribute(messageAttributes, "NamedEntities", namedEntities);
        }
        addAttribute(messageAttributes, "Response-Sqs-Url", responseQueueUrl);
        addAttribute(messageAttributes, "ReviewUrl", reviewLink);
        addAttribute(messageAttributes, "Stars", stars);
        addAttribute(messageAttributes, "KeyFile", keyFile);

        // Send message with attributes to sqsWorkerQueue
        sendMessageToManager(messageAttributes);
    }

    void sendMessageToManager(Map<String, MessageAttributeValue> messageAttributes) {

        try {
            String messageDeduplicationId = "dedup-" + UUID.randomUUID().toString();

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(sqsWorkerQueueOutput)
                    .messageBody("answer from worker")
                    .messageGroupId("MyMessageGroup")
                    .messageDeduplicationId(messageDeduplicationId)
                    .messageAttributes(messageAttributes)
                    .build();

            sqsClient.sendMessage(sendMessageRequest);
        } catch (Exception e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    void addAttribute(Map<String, MessageAttributeValue> messageAttributes, String key, String value) {

        messageAttributes.put(key, MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(value)
                .build());
    }

    String messageAttribute(Message msg, String attribute) {

        // Check if the message has the "KeyFi;e" attribute
        if (msg.messageAttributes().containsKey(attribute)) {
            return msg.messageAttributes().get(attribute).stringValue();
        }
        return "";
    }
}
