package com.example;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.AbortedException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkInterruptedException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class ManagerApp {

    private S3Client s3Client;
    private SqsClient sqsClient;
    private Ec2Client ec2Client;
    public String sqsLocalManagerName = "Sqs-Local-Manager.fifo";
    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, Integer>> LocalToListOfFilesInput;
    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, Path>> LocalToListOfFilesOutput;
    public ConcurrentHashMap<String, ConcurrentHashMap<Integer, Boolean>> LocalToListOfFilesTerminate;
    private volatile static boolean terminate = false;
    private volatile static boolean finish = false;
    private AtomicInteger workersPerRevivews;
    private AtomicInteger totalMissions;
    private AtomicInteger totalRecived;
    final static AWS aws = AWS.getInstance();
    private String sqsUrlManagerWorkerInput;
    private String sqsUrlManagerWorkerOutput;
    private String bucketName = "naor-inbal1";
    private List<Thread> threads = new ArrayList<>();
    private final BlockingQueue<Message> messageQueueWorker = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> missionQueueThreads = new LinkedBlockingQueue<>();
    private static String ec2Script =
     "#!/bin/bash\n" +
    "sudo yum update -y \n" +
    "curl -s \"https://get.sdkman.io\" | bash\n" +
    "source \"$HOME/.sdkman/bin/sdkman-init.sh\"\n" +
    "sdk install java 17.0.1-open\n" +
    "sdk use java 17.0.1-open\n" +
    "aws s3 cp s3://script-bucket-inbal-naor/Worker.jar /home/Worker.jar\n" +
    "JAVA_HOME=\"$HOME/.sdkman/candidates/java/17.0.1-open\"\n" +
    "echo \"export JAVA_HOME=$JAVA_HOME\" >> ~/.bashrc\n" +
    "echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc\n" +
    "export JAVA_HOME\n" +
    "export PATH=$JAVA_HOME/bin:$PATH\n" +
    "java -cp /home/Worker.jar com.example.MainWorkerClass\n"+
    "shutdown -h now";

    ManagerApp() {
        s3Client = aws.getS3();
        sqsClient = aws.getSqsClient();
        ec2Client = aws.getEC2Client();
        workersPerRevivews = new AtomicInteger(-1);
        totalMissions = new AtomicInteger(0);
        sqsUrlManagerWorkerInput = "";
        LocalToListOfFilesInput = new ConcurrentHashMap<>();
        LocalToListOfFilesOutput = new ConcurrentHashMap<>();
        LocalToListOfFilesTerminate = new ConcurrentHashMap<>();
        totalRecived = new AtomicInteger(0);
    }

    public static void main(String[] args) throws InterruptedException {

        // Create an instance of ManagerApp with the specific SQS URL and region
        ManagerApp app = new ManagerApp();

        app.setSqsUrlManagerWorkerInput(app.createSqsManagerToWorkers("Sqs-Manager-Workers-Input"));
        app.setSqsUrlManagerWorkerOutput(app.createSqsManagerToWorkers("Sqs-workers-Manager-Output"));

        app.startMessageReceiverThread();
        app.startMessageProcessingThreads(5);
        app.startWorkerMessageReceiverThread();
        app.startWorkerProcessingThreads(5);

        while (!terminate) {
            try {
                // Sleep for a specified period before checking the condition again
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                // Handle the InterruptedException
                Thread.currentThread().interrupt();
                System.err.println("Thread was interrupted, possibly shutting down.");
                break;
            }
        }
        while (!terminate || !app.getMissionQueueThreads().isEmpty() || !finish 
        || app.getTotalMissions().get()!=0 || !app.getMessageQueueWorker().isEmpty()) {
            Thread.sleep(20000);
            // Wait for mission to be done before ending program
        }

        // Interrupt all threads
        System.out.println("[Manager] Interrupting manually threads");
        for (Thread thread : app.threads) {
            thread.interrupt();
        }

        // Optionally, wait for all threads to finish
        for (Thread thread : app.threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // Handle the interruption of the join call here, if necessary
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        }

        int counterWorkers = app.getRunningWorkerCount();
        while (counterWorkers > 0) {
            app.sendTerminateToWorkers("Done");
            counterWorkers--;
        }
    }

    AtomicInteger getTotalMissions(){
        return this.totalMissions;
    }

    void setSqsUrlManagerWorkerInput(String url){
        this.sqsUrlManagerWorkerInput = url;
    }

    void setSqsUrlManagerWorkerOutput(String url){
        this.sqsUrlManagerWorkerOutput = url;
    }

    BlockingQueue<String> getMissionQueueThreads(){
        return this.missionQueueThreads;
    }

    BlockingQueue<Message> getMessageQueueWorker(){
        return this.messageQueueWorker;
    }


    public void startMessageReceiverThread() throws InterruptedException {
        Thread receiverThread = new Thread(() -> {
            String sqsUrlLocalManager = null;
            try {
                // Retrieve the queue URL once outside the loop to avoid unnecessary calls
                sqsUrlLocalManager = aws.getSqsClient().getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(sqsLocalManagerName)
                        .build())
                        .queueUrl();
            } catch (SqsException e) {
                // Handle AWS SQS exception
                System.err.println("Failed to get SQS queue URL: " + e.getMessage());
                return; // Exit thread if we cannot proceed without the queue URL
            }

            while (!Thread.currentThread().isInterrupted() && !terminate) {
                try {
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(sqsUrlLocalManager)
                            .maxNumberOfMessages(1) // Maximum number of messages to retrieve
                            .visibilityTimeout(60) // Visibility timeout in seconds
                            .waitTimeSeconds(20) // Increase if needed to reduce API calls
                            .messageAttributeNames("All") // Request all message attributes
                            .build();

                    ReceiveMessageResponse receiveResponse = aws.getSqsClient().receiveMessage(receiveRequest);

                    for (Message message : receiveResponse.messages()) {
                        processRecievedMessageFromLocalApp(message);
                        // Delete the received message
                        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                                .queueUrl(sqsUrlLocalManager)
                                .receiptHandle(message.receiptHandle())
                                .build();

                        aws.getSqsClient().deleteMessage(deleteRequest);
                    }
                } catch (SqsException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Handle the interruption gracefully
                        System.err.println("Receiver thread was interrupted during AWS SDK call.");
                        return; // Optionally, terminate the loop and thus the thread if interrupted
                    } else {
                        // Log or handle other SQS exceptions that are not due to thread interruption
                        System.err.println("Error receiving messages from SQS: " + e.getMessage());
                    }
                }
            }
        });
        threads.add(receiverThread);
        receiverThread.start();
    }

    public void startMessageProcessingThreads(int numberOfThreads) throws InterruptedException {
        for (int i = 0; i < numberOfThreads; i++) {
            Thread thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        processLineFromMessageQueueThreads(); 
                    } catch (Exception e) { // Catch general exceptions
                        System.err.println("Exception in processing thread: " + e.getMessage());
                    }
                }
            });
            threads.add(thread);
            thread.setName("Thread-" + i);
            thread.start();
        }
    }

    public void startWorkerMessageReceiverThread() {
        Thread receiverThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(sqsUrlManagerWorkerOutput)
                            .maxNumberOfMessages(1) // Maximum number of messages to retrieve
                            .visibilityTimeout(60) // Visibility timeout in seconds
                            .waitTimeSeconds(20) // Use long polling (wait up to 20 seconds for messages to arrive)
                            .messageAttributeNames("All") // Request the "Response-Sqs" attribute
                            .build();
    
                    ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(receiveRequest);
                    if (receiveResponse.messages().isEmpty()) {
                        // No messages were received, the loop will start over and check again.
                        System.out.println("Queue is empty, checking again...");
                    } else {
                        for (Message message : receiveResponse.messages()) {
                            // Process the message
                            messageQueueWorker.put(message);
                            deleteMsgFromSqs(sqsUrlManagerWorkerOutput, message);
                        }
                    }
                } catch (AbortedException | SdkInterruptedException e ) {
                    if (Thread.currentThread().isInterrupted()) {
                        // Handle the case where the interruption caused the AbortedException
                        System.err.println("Thread was interrupted during AWS SDK operation.");
                        break; // Exit the loop, optionally perform cleanup if needed
                    } else {
                        // Handle other cases of AbortedException not caused by an interrupt
                        System.err.println("AWS SDK operation was aborted for a reason other than an interrupt.");
                    }
                } catch (InterruptedException e) {
                    // Handle any other unexpected exceptions here
                    System.err.println("An unexpected error occurred: " + e.getMessage());
                }
            }
        });
        threads.add(receiverThread);
        receiverThread.start();
    }
    
    public void startWorkerProcessingThreads(int numOfThreads) {
        for (int i = 0; i < numOfThreads; i++) {
            Thread thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Message message = messageQueueWorker.take(); // Blocks until a message is available
                        processMessageThreadsRecivedFromWorkers(message); // Implement this to handle message processing
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Worker processor thread was interrupted");
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
    }

    public void processRecievedMessageFromLocalApp(Message message) {

        String dataUrl = "";
        String responseQueueUrl = "";
        int fileKey = 0;

        dataUrl = message.body();

        // Check if the message has the "Response-Sqs" attribute
        if (message.messageAttributes().containsKey("Response-Sqs-Url")) {
            responseQueueUrl = message.messageAttributes().get("Response-Sqs-Url").stringValue();
        }

        // Check if the message has the "FileKey" attribute
        if (message.messageAttributes().containsKey("FileKey")) {
            fileKey = Integer.parseInt(message.messageAttributes().get("FileKey").stringValue());
            // Ensure the map for the responseQueueUrl exists and add it if not
            LocalToListOfFilesInput.computeIfAbsent(responseQueueUrl, k -> new ConcurrentHashMap<>());
            LocalToListOfFilesInput.get(responseQueueUrl).put(fileKey, 0);
            LocalToListOfFilesTerminate.computeIfAbsent(responseQueueUrl, k -> new ConcurrentHashMap<>());
        }

        // Check the terminate tag - (volatile)
        if (message.messageAttributes().containsKey("terminate")) {
            terminate = message.messageAttributes().get("terminate").stringValue().equals("true");
            LocalToListOfFilesTerminate.get(responseQueueUrl).put(fileKey, terminate);
        }

        // function poll workers per mission value
        if (workersPerRevivews.get() == -1 && message.messageAttributes().containsKey("TaskCount")) {
            workersPerRevivews.set(Integer.parseInt(message.messageAttributes().get("TaskCount").stringValue()));
        }

        downloadAndProcess(dataUrl, responseQueueUrl, fileKey);
    }

    public void downloadAndProcess(String s3FileLocation, String responeSqs, int fileKey) {
        try {
            System.out.println("[Manager] download and process");
            // Parse bucket name and object key from the S3 URL
            String[] urlParts = s3FileLocation.split("/", 4);
            String[] bucketNameParts = urlParts[2].split("\\.");
            String bucketName = bucketNameParts[0];
            String objectKey = urlParts[3];

            // Download the object from S3
            ResponseInputStream responseInputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());

            // Save the object to a temporary file
            Path tempFilePath = Files.createTempFile("temp-" + UUID.randomUUID(), ".tmp");
            Files.copy(responseInputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);

            // Process the downloaded file
            DistributeLines(tempFilePath, responeSqs, fileKey);

            System.out.println("[Manager] Object downloaded and processed successfully!");
        } catch (S3Exception | IOException e) {
            e.printStackTrace();
        }
    }

    public void DistributeLines(Path filePath, String responseSqs, int fileKey) {

        int counter = 0;
        try {
            for (String line : Files.readAllLines(filePath)) {
                // Format: line|responseSqs|fileKey
                String queueEntry = line + "|" + responseSqs + "|" + fileKey;
                missionQueueThreads.put(queueEntry);
                counter++;
            }
            System.out.println("[Manager - Manager Thread] Amount of reviews to process: " + counter * 10);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void processLineFromMessageQueueThreads() {
        try {
            String queueEntry = missionQueueThreads.take();
            String[] parts = queueEntry.split("\\|");
            if (parts.length == 3) {
                String line = parts[0];
                String responseSqs = parts[1];
                int fileKey = Integer.parseInt(parts[2]);

                // Process each line as a separate JSON object
                JSONObject jsonObject = new JSONObject(line);
                JSONArray reviews = jsonObject.getJSONArray("reviews");
                for (int i = 0; i < reviews.length(); i++) {
                    JSONObject review = reviews.getJSONObject(i);
                    String reviewUrl = review.getString("link");
                    int stars = review.getInt("rating");
                    String text = review.getString("text");
                    synchronized (this) {
                        int previousCount = LocalToListOfFilesInput.get(responseSqs).get(fileKey);
                        LocalToListOfFilesInput.get(responseSqs).put(fileKey, previousCount + 1);
                        addMissionToWorkersSqs(text, responseSqs, reviewUrl, stars, fileKey);
                    }
                }

                synchronized (this) {
                    totalMissions.set(totalMissions.get() + reviews.length());
                }
                // Check if the current thread's name is "0" before deploying worker nodes
                if (Thread.currentThread().getName().equals("Thread-0")) {
                    deployWorkerNodesIfPossible();
                }
            }
        } catch (InterruptedException e) {
            // InterruptedException caught when the thread is interrupted while waiting
            Thread.currentThread().interrupt(); // Re-interrupt the thread to preserve the interrupted status
            System.err.println("Thread was interrupted while waiting on the blocking queue.");
        }
    }

    synchronized public int getRunningWorkerCount() {

        int runningWorkerCount = 0;

        // Create a filter for instances with a tag "Role" set to "Worker"
        // and for instances that are in the "running" state
        Filter tagFilter = Filter.builder()
                .name("tag:Name")
                .values("Worker")
                .build();

        Filter stateFilter = Filter.builder()
                .name("instance-state-name")
                .values("running", "pending")
                .build();

        // Describe instances with the filters
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(tagFilter, stateFilter)
                .build();

        DescribeInstancesResponse response = ec2Client.describeInstances(request);

        // Count the number of running instances that match the filters
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                runningWorkerCount++;
            }
        }

        return runningWorkerCount;
    }

    synchronized void deployWorkerNodesIfPossible() {

        int counterWorkers = getRunningWorkerCount();
        if (counterWorkers >= 8
                || (counterWorkers > 0 && totalMissions.get() < workersPerRevivews.get() * counterWorkers))
            return;

        // Deploy worker
        if (counterWorkers == 0) {
            aws.createEC2(ec2Script, "Worker", 1);
            return;
        }
        int workers = totalMissions.get() / (workersPerRevivews.get() * counterWorkers);
        if (workers + counterWorkers > 8) {
            workers = 8 - counterWorkers;
        }
        System.out.println("[Manager] Number of workers to deploy: " + workers);
        aws.createEC2(ec2Script, "Worker", workers);
    }

    String createSqsManagerToWorkers(String queueName) {
        // Create sqs
        aws.createSqsQueue(queueName);
        return aws.getSqsClient().getQueueUrl(GetQueueUrlRequest.builder()
                .queueName(queueName + ".fifo")
                .build())
                .queueUrl();
    }

    void addMissionToWorkersSqs(String text, String responeLocalSqs, String reviewUrl, int stars, int keyfile) {

        String messageDeduplicationId = "dedup-" + UUID.randomUUID().toString();

        // Send the message to the SQS queue
        SendMessageResponse response = aws.getSqsClient().sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsUrlManagerWorkerInput)
                .messageBody(text)
                .messageGroupId("MyMessageGroup")
                .messageDeduplicationId(messageDeduplicationId)
                .messageAttributes(Map.of(
                        "Response-Sqs-Url", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(responeLocalSqs)
                                .build(),
                        "ReviewUrl", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(reviewUrl)
                                .build(),
                        "Stars", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(String.valueOf(stars))
                                .build(),
                        "KeyFile", MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(String.valueOf(keyfile))
                        .build()))
                .build());
    }

    void sendTerminateToWorkers(String text) {

        String messageDeduplicationId = "dedup-" + UUID.randomUUID().toString();

        System.out.println("[Manager] Sending terminate to workers");
        // Send the message to the SQS queue
        SendMessageResponse response = aws.getSqsClient().sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsUrlManagerWorkerInput)
                .messageBody(text)
                .messageGroupId("MyMessageGroup")
                .messageDeduplicationId(messageDeduplicationId)
                .messageAttributes(Map.of(
                        "terminate", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("terminate")
                                .build()))
                .build());
    }


    public void processMessageThreadsRecivedFromWorkers(Message message) {

        int keyFile = 0;
        int stars = 0;

        // Extract Attributes
        String responseQueueUrl = messageAttribute(message, "Response-Sqs-Url");

        if (!messageAttribute(message, "KeyFile").isEmpty()) {
            keyFile = Integer.parseInt(messageAttribute(message, "KeyFile"));
        }

        synchronized (this) {
            int previousCount = LocalToListOfFilesInput.get(responseQueueUrl).get(keyFile);
            LocalToListOfFilesInput.get(responseQueueUrl).put(keyFile, previousCount - 1);
        }

        String sentiment = messageAttribute(message, "SentimentScore");

        String namedEntities = messageAttribute(message, "NamedEntities");

        if (!messageAttribute(message, "Stars").isEmpty()) {
            stars = Integer.parseInt(messageAttribute(message, "Stars"));
        }

        String reviewUrl = messageAttribute(message, "ReviewUrl");

        processMessageRecievedFromWorker(keyFile, responseQueueUrl, reviewUrl, Integer.valueOf(stars),
                sentiment, namedEntities);

        synchronized (this) {
            if (LocalToListOfFilesInput.get(responseQueueUrl).get(keyFile) == 0) { // Done
                messageToLocalMissionDone(keyFile, responseQueueUrl);
                if(LocalToListOfFilesTerminate.get(responseQueueUrl).getOrDefault(keyFile, false)){
                    finish = true;
                }
            }
            int curr = totalMissions.decrementAndGet();
        }

    }

    void deleteMsgFromSqs(String sqs, Message msg) {

        // Delete the received message
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(sqs)
                .receiptHandle(msg.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }

    String messageAttribute(Message msg, String attribute) {

        // Check if the message has the "KeyFi;e" attribute
        if (msg.messageAttributes().containsKey(attribute)) {
            return msg.messageAttributes().get(attribute).stringValue();
        }
        return "";
    }

    void processMessageRecievedFromWorker(int keyFile, String responseSqs, String reviewUrlLink,
            int stars, String sentiemnet, String namedEntities) {

        synchronized (this) {
            // Check if the map for responseSqs exists and then check if the key exists in
            // the inner map.
            if (LocalToListOfFilesOutput.containsKey(responseSqs)
                    && LocalToListOfFilesOutput.get(responseSqs) != null) {
                if (LocalToListOfFilesOutput.get(responseSqs).get(keyFile) == null) {
                    // The keyFile does not exist, so put a new value in it.
                    LocalToListOfFilesOutput.get(responseSqs).put(keyFile, createFilePathFromUrl(responseSqs));
                }
            } else {
                // The map for responseSqs does not exist or is null, so create a new map and
                // put it.
                ConcurrentHashMap<Integer, Path> newFileMap = new ConcurrentHashMap<>();
                newFileMap.put(keyFile, createFilePathFromUrl(responseSqs));
                LocalToListOfFilesOutput.put(responseSqs, newFileMap);
            }
        }

        Path outputFile = LocalToListOfFilesOutput.get(responseSqs).get(keyFile);
        boolean sarcasm = detectSarcasm(stars, sentiemnet);
        String entities = extractNamedEntities(namedEntities);
        String color = getSentimentColor(sentiemnet);
        addReviewToFile(reviewUrlLink, color, sarcasm, entities, outputFile);
    }

    synchronized void messageToLocalMissionDone(int keyFile, String responseSqs) {

        Path outputFile = LocalToListOfFilesOutput.get(responseSqs).get(keyFile);
        String S3Url = aws.uploadFile(bucketName, "Results-input-num-" + keyFile, outputFile);
        String messageDeduplicationId = "dedup-" + "-" + System.currentTimeMillis();

        // Send the message to the responseSQS queue
        SendMessageResponse response = aws.getSqsClient().sendMessage(SendMessageRequest.builder()
                .queueUrl(responseSqs)
                .messageBody(S3Url)
                .messageGroupId("MyMessageGroup")
                .messageDeduplicationId(messageDeduplicationId)
                .messageAttributes(Map.of(
                        "keyFile", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(String.valueOf(keyFile))
                                .build()))
                .build());

        System.out.println("[Manager] Mission of inputFile number " + keyFile + " is done.");
        System.out.println("[Manager] Send a message to localApp at " + responseSqs);
    }

    public Path createFilePathFromUrl(String url) {

        // Extract the part of the URL after the last slash and add " Result" suffix
        String fileName = url.substring(url.lastIndexOf('/') + 1) + "-Result";

        // Create a Path object for the file
        Path filePath = Paths.get(fileName);

        try {
            // Ensure the file exists. If it doesn't, create it.
            if (Files.notExists(filePath)) {
                Files.createFile(filePath);
                System.out.println("File created at path: " + filePath);
            } else {
                System.out.println("File already exists at path: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("An error occurred while creating the file: " + e.getMessage());
        }

        return filePath;
    }

    public boolean detectSarcasm(int stars, String sentiment) {
        // High stars but negative sentiment
        if ((stars == 4 || stars == 5) && (sentiment.equals("Very negative") || sentiment.equals("Negative"))) {
            return true;
        }
        // Low stars but positive sentiment
        else if ((stars == 1 || stars == 2) && (sentiment.equals("Positive") || sentiment.equals("Very positive"))) {
            return true;
        }
        // No significant mismatch found
        return false;
    }

    public String extractNamedEntities(String input) {
        // Split the input string into parts based on spaces
        String[] parts = input.split(" ");

        // Transform each part and collect the results
        String result = Arrays.stream(parts)
                .filter(part -> part.contains(":")) // Optional: Ensure part matches expected format
                .map(part -> part.substring(1, part.indexOf(':'))) // Remove leading "-" and ":Type"
                .collect(Collectors.joining(", ", "[", "]")); // Join with commas, enclosed in brackets

        return result;
    }

    public String getSentimentColor(String sentiment) {
        switch (sentiment) {
            case "Very negative":
                return "darkred";
            case "Negative":
                return "red";
            case "Neutral":
                return "black";
            case "Positive":
                return "lightgreen";
            case "Very positive":
                return "darkgreen";
            default:
                return "black"; // Default color for unknown sentiment
        }
    }

    public synchronized void addReviewToFile(String reviewLink, String reviewColor, boolean sarcasm, String entities,
            Path pathFile) {

        String sarcasmText = sarcasm ? "Sarcasm is detected" : "Sarcasm is not detected";
        totalRecived.getAndIncrement();

        // Format the review data as a delimited text string
        String textContent = String.format("%s,%s,%s,%s\n", reviewLink, reviewColor, entities, sarcasmText);

        // Use Files.newBufferedWriter to open the file and append the content
        try (BufferedWriter file = Files.newBufferedWriter(pathFile, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            file.write(textContent);
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
        }
    }
}
