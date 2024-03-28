# Sarcasm-Analysis

A distributed application designed to process Amazon reviews, perform sentiment analysis, and detect sarcasm, showcasing results on a web page.
This project utilizes AWS cloud resources, including EC2 instances for processing and S3 for storage, to analyze input text files of reviews in JSON format.

-----------------------

### More Details

The application is composed of a local application, and non-local instances running on Amazon
cloud.

The application gets input text files containing lists of reviews (JSON format).
Then, instances are launched in AWS (workers & a manager) to apply sentiment analysis on the
reviews and detect whether it is sarcasm or not. The results are displayed on a webpage.
The main use-case:

  1. The user starts the application and supplies as input files with reviews, an integer n stating
  how many reviews/messages per worker (your choice), and an optional argument terminate,
  indicating that the local application will send a terminate message to the Manager at the
  end of its task.
  2. The user gets an html file containing the reviews with sentiment analysis and sarcasm
  detection, containing the url of the original review (at amazon).


----------------------------------------------------------------------------------------------------------------------------------------------------------
### Instructions - how to run the project:

-Extract a jar file of the local application, main program is App.

-In the command line:

	>java -jar Local.jar inputFileName1... inputFileNameN outputFileName1... outputFileNameN n [terminate]
 
		• inputFileNameI is the name of the input file I.
  
 		• outputFileName is the name of the output file. 
   
		• n is the workers’ files ratio (reviews per worker).
  
 		• terminate indicates that the application should terminate the manager at the end.
   

Type of instances used in the program: 

	•	ami: "ami-00e95a9222311e8ed”.
 
	•	type: m4.large.
 

### High-level explanation of program flow:

1. Local Application uploads the file with the list of reviews URLs to S3.
2. Local Application sends a message (queue) stating the location of the input file on S3.
3. Local Application does one of the following:
   - Starts the manager.
   - Checks if a manager is active and if not, starts it.
4. Manager downloads a list of reviews.
5. Manager distributes sentiment analysis and entity extraction jobs on the workers.
6. Manager deploys nodes to process messages.
7. Worker gets a message from an SQS queue.
8. Worker performs the requested job/s on the review.
9. Worker puts a message in an SQS queue indicating the original review together with the output of the operation performed (sentiment/extracted entities).
10. Manager reads all Workers' messages from SQS and creates one summary file.
11. Manager uploads the summary file to S3.
12. Manager posts an SQS message about the summary file.
13. Local Application reads SQS message.
14. Local Application downloads the summary file from S3.
15. Local Application creates HTML output file.
16. Manager terminates all workers instances and itself when all missions are done.


[![Screenshot-2024-02-15-at-16-12-30-2.png](https://i.postimg.cc/0NVBMpRb/Screenshot-2024-02-15-at-16-12-30-2.png)](https://postimg.cc/GBsQ6TpC)

#### Local Application

The application resides on a local (non-cloud) machine. Once started, it reads the input file from the user, and:

 	o Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node. 
  
	o Uploads the file to S3, doing it per input file.
 
	o Sends a message to an SQS fifo queue, stating the location of the file on S3.
 
	o Incase of terminate argument from command line, attaches it to the message above as attribute for manager to know to terminate after this input file.
 
	o Checks an SQS fifo queue for a message indicating the process is done and the response (the summary file) is available on S3.
 
	o Create an html file representing the results. 
 
IMPORTANT: There might be more than one than one local application running at the same time.


#### The Manager

 The manager process resides on an EC2 node. It checks a special SQS fifo queue for messages from local applications. Once it receives a message it:
 
 • For the case of new task message:
 
 	o Distribute the operations to be performed on the reviews to the workers using SQS queue/s. 
  
	o Check the SQS message count and starts Worker processes (nodes) accordingly. 
 
		▪ The manager should create a worker for every n messages (as defined by the command-line argument), if there are no running workers. 
  
		▪ If there are k active workers, and the new job requires m workers, then the manager should create m-k new workers, if possible. For any case don’t run more than 9 instances – AWS uses to block students who try this. 
  
		▪ Note that while the manager creates a node for every n messages, it does not delegate messages to specific nodes. All of the worker nodes take their messages from the same SQS queue;
		  so it might be the case that with 2n messages, hence two worker nodes, one node processed n+(n/2) messages, while the other processed only n/2.
    
	o In case the manger receives response messages from the workers (regarding input file), it: 
 
		▪ Creates a summary output file accordingly, if not existed yet for the specific input file number.
  
		▪ Writes the response to the above file.
  
		▪ Once all reviews was process, uploads the file to S3.
  
		▪ Sends a message to the application with the location of the file in S3.
  
• In case of a termination message, the manager: 

	o Should not accept any more input files from local applications. However, it does serve the local application that sent the termination message. 
 
	o Waits for all the workers to finish their job, and then terminates them. 
 
	o Creates response messages for the jobs, if needed. 
 
	o Terminates. 
 
IMPORTANT: the manager must process requests from local applications simultaneously; meaning, it must not handle each request at a time, but rather work on all requests in parallel.


The manager has multi-threads working:

	✓	one thread for receiving messages from local application, then disturbing reviews jobs to working-local threads.
 
	✓	working-local threads: 5 threads for processing reviews arriving from local application, then adding those as mission to workers for analysis.
 
	✓	one thread for receiving messages from workers, then disturbing answers to working-workers threads.
 
	✓	working-workers threads: 5 threads for processing answers arriving from workers, then writing these answers to a file existed to be sent to local app.
 

#### The Workers

A worker process resides on an EC2 node. His life cycle:

Repeatedly:

	• Get a message from an SQS queue. 
 
	• Perform the requested job, and return the result. 
 
	• Remove the processed message from the SQS queue. 
 

#### Queues and Messages 

As described above, queues are used for: 

	• Communication between the local application and the manager:
 
		- One fifo queue for sending the input file needed to be worked on ( the url of the location file of the input file in S3). 
  
		- One fifo queue for response message (mission is done - the url of the location file in S3).
  
	• Communication between the manager and the workers.
 
		- One fifo queue for sending the reviews needed to be worked on. 
  
		- One fifo queue for response message (mission is done by worker).
  

