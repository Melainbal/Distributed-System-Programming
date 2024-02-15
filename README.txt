{\rtf1\ansi\ansicpg1252\cocoartf2709
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica-Bold;\f1\fswiss\fcharset0 Helvetica;\f2\fswiss\fcharset0 ArialMT;
\f3\fswiss\fcharset0 Arial-BoldMT;\f4\fnil\fcharset0 Menlo-Regular;}
{\colortbl;\red255\green255\blue255;\red0\green0\blue0;\red255\green255\blue255;\red194\green126\blue101;
\red193\green193\blue193;}
{\*\expandedcolortbl;;\cssrgb\c0\c0\c0;\cssrgb\c100000\c100000\c100000;\cssrgb\c80784\c56863\c47059;
\cssrgb\c80000\c80000\c80000;}
{\*\listtable{\list\listtemplateid1\listhybrid{\listlevel\levelnfc23\levelnfcn23\leveljc0\leveljcn0\levelfollow0\levelstartat1\levelspace360\levelindent0{\*\levelmarker \{disc\}}{\leveltext\leveltemplateid1\'01\uc0\u8226 ;}{\levelnumbers;}\fi-360\li720\lin720 }{\listname ;}\listid1}
{\list\listtemplateid2\listhybrid{\listlevel\levelnfc23\levelnfcn23\leveljc0\leveljcn0\levelfollow0\levelstartat1\levelspace360\levelindent0{\*\levelmarker \{disc\}}{\leveltext\leveltemplateid101\'01\uc0\u8226 ;}{\levelnumbers;}\fi-360\li720\lin720 }{\listname ;}\listid2}
{\list\listtemplateid3\listhybrid{\listlevel\levelnfc23\levelnfcn23\leveljc0\leveljcn0\levelfollow0\levelstartat1\levelspace360\levelindent0{\*\levelmarker \{disc\}}{\leveltext\leveltemplateid201\'01\uc0\u8226 ;}{\levelnumbers;}\fi-360\li720\lin720 }{\listname ;}\listid3}
{\list\listtemplateid4\listhybrid{\listlevel\levelnfc23\levelnfcn23\leveljc0\leveljcn0\levelfollow0\levelstartat1\levelspace360\levelindent0{\*\levelmarker \{check\}}{\leveltext\leveltemplateid301\'01\uc0\u10003 ;}{\levelnumbers;}\fi-360\li720\lin720 }{\listname ;}\listid4}}
{\*\listoverridetable{\listoverride\listid1\listoverridecount0\ls1}{\listoverride\listid2\listoverridecount0\ls2}{\listoverride\listid3\listoverridecount0\ls3}{\listoverride\listid4\listoverridecount0\ls4}}
\paperw11900\paperh16840\margl1440\margr1440\vieww38200\viewh21600\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\b\fs36 \cf0 \ul \ulc0 Instructions - how to run the project:\

\f1\b0\fs28 \ulnone -Extract a jar file of the local application, main program is App.\
-In the command line:\
\pard\pardeftab720\partightenfactor0

\f2 \cf0 \expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec2 	>java -jar Local.jar inputFileName1... inputFileNameN outputFileName1... outputFileNameN n [terminate]\
		\'95 inputFileNameI is the name of the input file I.\
 		\'95 outputFileName is the name of the output file. \
		\'95 n is the workers\'92 files ratio (reviews per worker).\
 		\'95 terminate indicates that the application should terminate the manager at the end.\
\

\f3\b\fs36 \ul Type of instances used in the program: 
\f2\b0 \ulnone \
\pard\tx220\tx720\pardeftab720\li720\fi-720\partightenfactor0
\ls1\ilvl0
\fs28 \cf0 \kerning1\expnd0\expndtw0 \outl0\strokewidth0 {\listtext	\uc0\u8226 	}ami:\cf2 \cb3  \expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec4 "ami-00e95a9222311e8ed\'94.
\f4\fs24 \cf5 \cb1 \strokec5 \
\ls1\ilvl0
\f2\fs28 \cf0 \kerning1\expnd0\expndtw0 \outl0\strokewidth0 {\listtext	\uc0\u8226 	}type: m4.large.\
\pard\tx720\pardeftab720\partightenfactor0
\cf0 \expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec2 \
Program took 6:20 minutes to be finished-\
\pard\tx220\tx720\pardeftab720\li720\fi-720\partightenfactor0
\ls2\ilvl0\cf0 \kerning1\expnd0\expndtw0 \outl0\strokewidth0 {\listtext	\uc0\u8226 	}1 local application\expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec2 \
\pard\tx220\tx720\pardeftab720\li720\fi-720\partightenfactor0
\ls3\ilvl0\cf0 \kerning1\expnd0\expndtw0 \outl0\strokewidth0 {\listtext	\uc0\u8226 	}1 worker per 50 reviews\
{\listtext	\uc0\u8226 	}inputs: input1.txt and input2.txt, total of 980 reviews\
{\listtext	\uc0\u8226 	}outputs: output1.html and output2.html with example outputs written inside.\
\pard\tx720\pardeftab720\partightenfactor0
\cf0 \expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec2 \

\f3\b\fs36 \ul High-level explanation of program flow:\

\f2\b0\fs28 \ulnone \
\pard\pardeftab720\partightenfactor0
\cf0 1.Local Application uploads the file with the list of reviews urls to S3.\
2. Local Application sends a message (queue) stating the location of the input file on S3. \
3. Local Application does one of the following:\
	 o Starts the manager.\
	 o Checks if a manager is active and if not, starts it. \
4. Manager downloads a list of reviews. \
5. Manager distributes sentiment analysis and entity extraction jobs on the workers. \
6. Manager deploy nodes to process messages. \
7. Worker gets a message from an SQS queue. \
8. Worker performs the requested job/s on the review. \
9. Worker puts a message in an SQS queue indicating the original review together with the output of the operation performed (sentiment/extracted entities). \
10. Manager reads all Workers' messages from SQS and creates one summary file. \
11. Manager uploads the summary file to S3. \
12. Manager posts an SQS message about the summary file. \
13. Local Application reads SQS message. \
14. Local Application downloads the summary file from S3. \
15. Local Application creates html output file.\
16.Manager terminates all workers instances and itself when all missions are done.\
\

\f3\b\fs36 \ul Local Application\

\f2\b0\fs28 \ulnone The application resides on a local (non-cloud) machine. Once started, it reads the input file from the user, and:\
 	o Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node. \
	o Uploads the file to S3, doing it per input file.\
	o Sends a message to an SQS fifo queue, stating the location of the file on S3.\
\pard\pardeftab720\partightenfactor0
\cf0 \outl0\strokewidth0 	o Incase of terminate argument from command line, attaches it to the message above as attribute for manager to know to terminate after this input file.\outl0\strokewidth0 \strokec2 \
\pard\pardeftab720\partightenfactor0
\cf0 	o Checks an SQS fifo queue for a message indicating the process is done and the response (the summary file) is available on S3.\
	o Create an html file representing the results. \

\f3\b IMPORTANT:
\f2\b0  There might be more than one than one local application running at the same time.\
\

\f3\b\fs36 \ul The Manager\

\f2\b0\fs28 \ulnone  The manager process resides on an EC2 node. It checks a special SQS fifo queue for messages from local applications. Once it receives a message it:\
 \'95 For the case of new task message:\
 	o Distribute the operations to be performed on the reviews to the workers using SQS queue/s. \
	o Check the SQS message count and starts Worker processes (nodes) accordingly. \
		\uc0\u9642  The manager should create a worker for every n messages (as defined by the command-line argument), if there are no running workers. \
		\uc0\u9642  If there are k active workers, and the new job requires m workers, then the manager should create m-k new workers, if possible. For any case don\'92t run more than 9 instances \'96 AWS uses to block students who try this. \
		\uc0\u9642  Note that while the manager creates a node for every n messages, it does not delegate messages to specific nodes. All of the worker nodes take their messages from the same SQS queue;\
		  so it might be the case that with 2n messages, hence two worker nodes, one node processed n+(n/2) messages, while the other processed only n/2.\
	o In case the manger receives response messages from the workers (regarding input file), it: \
		\uc0\u9642  Creates a summary output file accordingly, if not existed yet for the specific input file number.\
		\uc0\u9642  Writes the response to the above file.\
\pard\pardeftab720\partightenfactor0
\cf0 \outl0\strokewidth0 		\uc0\u9642  Once all reviews was process, uploads the file to S3.\outl0\strokewidth0 \strokec2 \
\pard\pardeftab720\partightenfactor0
\cf0 		\uc0\u9642  Sends a message to the application with the location of the file in S3.\
\'95 In case of a termination message, the manager: \
	o Should not accept any more input files from local applications. However, it does serve the local application that sent the termination message. \
	o Waits for all the workers to finish their job, and then terminates them. \
	o Creates response messages for the jobs, if needed. \
	o Terminates. \

\f3\b IMPORTANT:
\f2\b0  the manager must process requests from local applications simultaneously; meaning, it must not handle each request at a time, but rather work on all requests in parallel.\
\
The manager has multi-threads working:\
\pard\tx220\tx720\pardeftab720\li720\fi-720\partightenfactor0
\ls4\ilvl0\cf0 \kerning1\expnd0\expndtw0 \outl0\strokewidth0 {\listtext	\uc0\u10003 	}one thread for receiving messages from local application, then disturbing reviews jobs to working-local threads.\
{\listtext	\uc0\u10003 	}working-local threads: 5 threads for processing reviews arriving from local application, then adding those as mission to workers for analysis.\
{\listtext	\uc0\u10003 	}one thread for receiving messages from workers, then disturbing answers to working-workers threads.\
\ls4\ilvl0{\listtext	\uc0\u10003 	}working-workers threads: 5 threads for processing answers arriving from workers, then writing these answers to a file existed to be sent to local app.\
\pard\tx720\pardeftab720\partightenfactor0
\cf0 \
\pard\pardeftab720\partightenfactor0

\f3\b\fs36 \cf0 \expnd0\expndtw0\kerning0
\ul \ulc0 \outl0\strokewidth0 \strokec2 The Workers\

\f2\b0\fs28 \ulnone A worker process resides on an EC2 node. His life cycle:\
Repeatedly:\
	\'95 Get a message from an SQS queue. \
	\'95 Perform the requested job, and return the result. \
	\'95 Remove the processed message from the SQS queue. \
\

\f3\b\fs36 \ul Queues and Messages \

\f2\b0\fs28 \ulnone As described above, queues are used for: \
	\'95 Communication between the local application and the manager:\
\pard\pardeftab720\partightenfactor0
\cf0 \outl0\strokewidth0 		- One fifo queue for sending the input file needed to be worked on ( the url of the location file of the input file in S3). \outl0\strokewidth0 \strokec2 \
\pard\pardeftab720\partightenfactor0
\cf0 		- One fifo queue for response message (mission is done - the url of the location file in S3).\
	\'95 Communication between the manager and the workers.\
\pard\pardeftab720\partightenfactor0
\cf0 \outl0\strokewidth0 		- One fifo queue for sending the reviews needed to be worked on. \
		- One fifo queue for response message (mission is done by worker).\kerning1\expnd0\expndtw0 \
\pard\tx720\pardeftab720\partightenfactor0
\cf0 \expnd0\expndtw0\kerning0
\outl0\strokewidth0 \strokec2 \
}
