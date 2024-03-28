# MapReduce Collocation Analysis Project

This project utilizes Hadoop and MapReduce within the Amazon Elastic MapReduce environment to analyze the Google 2-grams dataset for collocation extraction. By calculating Normalized Pointwise Mutual Information (NPMI), it identifies word pairs that occur together more frequently than random chance suggests. Designed for scalability, this approach leverages big data technologies to advance natural language processing research.

## How to run
The main entry point for the program is a Java Archive (JAR) file that can be executed with a command similar to the following:

```bash
java -jar Hadoop.jar ExtractCollations <minPmi> <relMinPmi>
```

Where <minPmi> is the minimum PMI for a collocation to be considered significant, and <relMinPmi> is the relative minimum PMI.



## Workflow Overview


### **Step1 - Frequency Count**

The primary goal is to process the input 2-gram dataset and count occurrences of words and word pairs (bigrams) for each decade. The program is designed to filter out stop words and to work with both Hebrew and English datasets. Here's a breakdown of its components:

**Mapper (Step1FreqCountMapper)**

- Input: <LineID, line> where line contains a 2-gram, the decade, and occurrence count.
- Output:
  - For each word as the first in a bigram: <"word#1 decade", "occur">
  - For each word as the second in a bigram: <"word#2 decade", "occur">
  - For each bigram: <"word1 word2 decade", "occur">

**Reducer (Step1FreqCountReducer)**

- Input:
  - For single words: <"word#i decade", list("occur")>
  - For bigrams: <"word1 word2 decade", list("occur")>
- Output:
  - For single words: <"word#i decade", "total\_occur">
  - For bigrams: <"word1 word2 decade", "total\_occur">,

    <"word#1 decade", "word1 word2 decade">, <"word#2 decade", "word1 word2 decade">

**Combiner (Step1CombinerClass)**

- Used to locally aggregate counts before shuffling, reducing network data transfer.

**Partitioner (Step1PartitionerClass)**

- Ensures data with the same key is processed by the same reducer, based on hash partitioning.

This step utilizes Hadoop counters to accumulate the total occurrences of bigrams across different decades. The use of Hadoop counters in this step focuses on a constant number of decades, leading to an O(1) memory allocation. This ensures that scalability is maintained, as the memory usage does not increase with the size of the data.

The main method sets up the job configuration, input and output formats, and paths, and finally submits the job. Upon completion, it uploads the aggregated counts for each decade to an S3 bucket.

-------------
### **Step 2 - Bigrams Aggregation**

The goal of Step 2 is to reorganize the output from Step 1, preparing the data for PMI calculation by aggregating bigrams and their occurrences.

**Mapper (Step2BigramsAggregationMapper)**

- Input: <w#i decade , <w1 w2 decade>>, <w#i decade, occur> , <w1 w2 decade, occur>.
- Output: forwarding input

**Reducer (Step2BigramsAggregationReducer)**

- Input: <w#i decade , <w1 w2 decade>>, <w#i decade, occur> , <w1 w2 decade, occur>.
- Output:
- For bigrams: forwarding input <w1 w2 decade, occur>
- For unigrams: <w1 w2 decade, wi c(wi)> | i->{1,2}

**Partitioner (Step2PartitionerClass)**

- Ensures data with the same key is processed by the same reducer, based on hash partitioning.
---
### **Step 3 - PMI Calculation**

This step is focused on calculating the Pointwise Mutual Information (PMI) for each bigram within its respective decade, using previously aggregated bigram and unigram counts. Here's an overview of how it's structured:

**Mapper (Step3PmiCalculationMapper)**

- Input: <w1 w2 decade, occur>, <w1 w2 decade, wi c(wi)>.
- Output: forwarding input

**Reducer (Step3PmiCalculationReducer)**

- Input: <w1 w2 decade, occur>, <w1 w2 decade, wi c(wi)>.
- Output: <w1 w2 decade, npmi>

**Partitioner (Step3PartitionerClass)**

- Ensures data with the same key is processed by the same reducer, based on hash partitioning.

In the reducer setup phase, the program downloads the counters.txt file from an S3 bucket, which contains the total word occurrences for each decade, and stores this information in a HashMap for efficient access during the PMI calculation process. This approach does not harm scalability as the number of decades is fixed, making the memory allocation for the HashMap O(1), or constant, ensuring efficient data processing without significant memory overhead.

---
### **Step 4 - Aggregate Pmi**

This step's primary objective is to prepare the data for calculating relative PMI (rPMI) by summing up the normalized PMI (nPMI) values for each decade and passing through individual bigram nPMI values. Here's an overview of how it's structured:

**Mapper (Step4AggregatePmiMapper)**

- Input: <w1 w2 decade, npmi >.
- Output: < decade, npmi >, <w1 w2 decade, npmi >.

**Reducer (Step4AggregatePmiReducer)**

- Input: < decade, <mid-sum-npmi> >, <w1 w2 decade, npmi >.
- Output: < decade, <sumNPmi> >, <w1 w2 decade, npmi >.

**Partitioner (Step4PartitionerClass)**

- Ensures data with the same key is processed by the same reducer, based on hash partitioning.

**Combiner (Step4CombinerClass)**

- Used to locally aggregate npmi’s before shuffling, reducing network data transfer.

-----
### **Step 5 - Display sorted Pmi**

The final step of the analysis is to sort and display the normalized Pointwise Mutual Information (NPMI) values for the bigrams, focusing on those that qualify as collocations.. Here's an overview of how it's structured:

**Mapper (Step5DisplaySortedPmiMapper)**

- Input: < decade, <sumNPmi> >, <w1 w2 decade, npmi >..
- Output: < decade Integer.MAX\_VALUE, sumNPmi >, < decade npmi, <w1 w2 npmi>>.

**Reducer (Step5DisplaySortedPmiReducer)**

- Input: < decade Integer.MAX\_VALUE, sumNPmi >, < decade npmi, <w1 w2 npmi>>.
- Output: .< w1 w2 decade, npmi > is a collocation.

**Partitioner (Step5PartitionerClass)**

- Ensures data with the same key is processed by the same reducer, based on hash partitioning.

**Comparison Class (Step5Comparison)**

- This class ensures that the output is sorted first by decade in descending order and then by NPMI values in descending order. It is crucial for displaying the results in the desired order.

This step utilizes a WritableComparator subclass to sort the mapper's output by decade and NPMI values, ensuring that the highest NPMI values are processed first by the reducer. This sorting mechanism allows for a prioritized display of bigrams, emphasizing those with higher significance in each decade.

In the reducer's setup phase, the configuration parameters for the minimum NPMI value (minPmi) and the relative minimum NPMI value (relMinPmi) are retrieved from the job's configuration. This setup is crucial for filtering out bigrams that do not meet the specified criteria. Additionally, the reducer utilizes a decade counter to limit the number of bigrams processed for each decade to the top 100, ensuring focused analysis and optimization of computational resources.
