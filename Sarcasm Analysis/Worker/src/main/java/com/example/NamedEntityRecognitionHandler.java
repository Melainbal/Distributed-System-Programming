package com.example;

import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;

public class NamedEntityRecognitionHandler {

    private StanfordCoreNLP NERpipeline;

    public NamedEntityRecognitionHandler() {

    // Properties for Named Entity Recognition
    Properties props = new Properties();
    props.put("annotators", "tokenize , ssplit, pos, lemma, ner");
    this.NERpipeline = new StanfordCoreNLP(props);
    }

    public String printEntities(String review){
        Annotation document = new Annotation(review);
        NERpipeline.annotate(document);
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);
        String retunedValue="";
        for(CoreMap sentence: sentences) {

            for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(TextAnnotation.class);
                // this is the NER label of the token
                String ne = token.get(NamedEntityTagAnnotation.class);
                if(ne.equals("PERSON")) retunedValue+=("\t-" + word + ":" + ne);
                if(ne.equals("ORGANIZATION")) retunedValue+=("\t-" + word + ":" + ne);
                if(ne.equals("LOCATION")) retunedValue+=("\t-" + word + ":" + ne);
            }
        }
        return retunedValue;
    }
}

// public static void main( String[] args )
// {
//     NamedEntityRecognitionHandler ner = new NamedEntityRecognitionHandler();
//     List<String> urls = new ArrayList<>();//What we need to give to S3
//     try {
//         for (String line : Files.readAllLines(Paths.get("/Users/inbalmelamed/Desktop/Distributed System Programming/aws-assignment1/input2.txt"))) {
//             // Process each line as a separate JSON object
//             JSONObject jsonObject = new JSONObject(line);
//             JSONArray reviews = jsonObject.getJSONArray("reviews");

//             for (int i = 0; i < reviews.length(); i++) {
//                 JSONObject review = reviews.getJSONObject(i);
//                 String url = review.getString("link");
//                 String text = review.getString("text");
//                 urls.add(url);
//                 ner.printEntities(text);
//             }
//         }
//     } catch (IOException e) {
//         // TODO Auto-generated catch block
//         e.printStackTrace();
//     }

// }

