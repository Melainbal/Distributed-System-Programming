package com.example;

import java.util.Properties;
import edu.stanford.nlp.pipeline.*;


public class SentimentAnalysisHandler {
    private StanfordCoreNLP pipeline;

    public SentimentAnalysisHandler() {
        // Properties for Sentiment Analysis
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,sentiment");
        this.pipeline = new StanfordCoreNLP(props);
    }

    public String analyzeSentiment(String text) {
        // Perform sentiment analysis on the text
        CoreDocument doc = new CoreDocument(text);
        pipeline.annotate(doc);
        // Assuming we want the sentiment of the first sentence
        return doc.sentences().get(0).sentiment();
    }
}
