import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

/**
 * Step5DisplaySortedPMI
 */
public class Step5DisplaySortedPMI {

    public static class Step5DisplaySortedPMIMapper extends Mapper<LongWritable, Text, Text, Text> {

        // input - < decade, sumNPmi >, <w1 w2 decade, npmi >
        // output - < decade Integer.MAX_VALUE, sumNPmi >, < decade npmi, <w1 w2
        // npmi>>

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            String[] split = value.toString().split("\t");
            String newKey = split[0];
            String data = split[1];

            if (newKey.contains(" ")) { // This is a bigram with decade
                String npmi = data;
                String[] keySplit = newKey.split(" ");
                String w1 = keySplit[0];
                String w2 = keySplit[1];
                String decade = keySplit[2];
                // Output for bigram: <decade npmi, <w1 w2 npmi>>
                context.write(new Text(decade + " " + npmi), new Text(w1 + " " + w2 + " " + npmi));
            } else { 
                // Output for sumNPmi: <decade Integer.MAX_VALUE, <sumNPmi>>
                context.write(new Text(newKey + " " + Integer.MAX_VALUE), new Text(data));
            }

        }
    }

    public static class Step5DisplaySortedPMIReducer extends Reducer<Text, Text, Text, Text> {

        private double minPmi;
        private double relMinPmi;
        private double sum = 0;
        private String curr_decade="";
        private int decade_counter=0;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);

            Configuration conf = context.getConfiguration();
            minPmi = Double.parseDouble(conf.get("minPmi"));
            relMinPmi = Double.parseDouble(conf.get("relMinPmi"));
        }

        // input - < decade Integer.MAX_VALUE, <sumNPmi> >, < decade npmi, <w1 w2 npmi>>
        // output - < w1 w2 decade, npmi > is a collocation
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            String[] keySplit = key.toString().split(" ");
            String decade = keySplit[0];
            if(decade!= null && !decade.equals(curr_decade)) {
                curr_decade = decade;
                decade_counter=0;
            }
            if(decade_counter<100){
                decade_counter++;
                if (keySplit[1].contains(".")) {
                    // key: <decade npmi>
                    double npmi = Double.parseDouble(keySplit[1]);
                    if(npmi>=1) return;
                    for (Text value : values) {
                        String[] parts = value.toString().split(" ");
                        String w1 = parts[0];
                        String w2 = parts[1];
                        double relPmi = sum > 0 ? npmi / sum : 0; // Prevent division by zero
                        if (npmi >= minPmi || relPmi >= relMinPmi) {
                            context.write(new Text(decade +" "+npmi), new Text(w1 + " " + w2));
                        }
                    }
                } else {
                    // key: <decade Integer.MAX_VALUE>
                    for (Text value : values) {
                        sum = Double.parseDouble(value.toString());
                    }
                }
            }
        }
    }

    public static class Step5PartitionerClass extends Partitioner<Text, Text> {
        @Override
        public int getPartition(Text key, Text value, int numPartitions) {
            return (key.toString().split(" ")[0].hashCode()& Integer.MAX_VALUE) % numPartitions;
        }
    }

    private static class Step5Comparison extends WritableComparator {

        protected Step5Comparison() {
            super(Text.class, true);
        }

        // Keys: < decade Integer.MAX_VALUE>, <decade npmi>
        @Override
        public int compare(WritableComparable key1, WritableComparable key2) {

            String[] words1 = key1.toString().split(" ");
            String[] words2 = key2.toString().split(" ");

            // compare decades
            int decade1 = Integer.parseInt(words1[0]);
            int decade2 = Integer.parseInt(words2[0]);
            if (decade1 != decade2) {
                return Integer.compare(decade2, decade1); // Sort decades in descending order
            }

            // compare npmi
            double npmi1 = Double.parseDouble(words1[1]);
            double npmi2 = Double.parseDouble(words2[1]);
            return Double.compare(npmi2, npmi1); // Sort nmpis values by descending order
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 5 started!");
        // Check for the correct number of arguments
        if (args.length != 3) {
            System.err.println("Usage: Step5DisplaySortedPMI <minPmi> <relMinPmi>");
            System.exit(-1);
        }
        String minPmiStr = args[1];
        String relMinPmiStr = args[2];
        Configuration conf = new Configuration();
        conf.setDouble("minPmi", Double.parseDouble(minPmiStr));
        conf.setDouble("relMinPmi", Double.parseDouble(relMinPmiStr));
        // conf.set("mapred.max.split.size", "67108864"); // 64MB in bytes, more mappers
        Job job = Job.getInstance(conf, "DisplaySortedPMI");
        job.setJarByClass(Step5DisplaySortedPMI.class);
        job.setMapperClass(Step5DisplaySortedPMIMapper.class);
        job.setReducerClass(Step5DisplaySortedPMIReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setPartitionerClass(Step5PartitionerClass.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setSortComparatorClass(Step5Comparison.class);
        FileInputFormat.addInputPath(job, new Path("s3://n-gram-analysis/output_step_4_heb"));
        FileOutputFormat.setOutputPath(job, new Path("s3://n-gram-analysis/output_step_5_heb"));

        if (job.waitForCompletion(true)) {
            System.out.println("Step 5 finished");
        } else {
            System.out.println("Step 5 failed");
        }
    }
}