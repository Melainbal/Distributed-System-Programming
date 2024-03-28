import java.io.IOException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
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
 * Step4AggregatePMI
 */
public class Step4AggregatePMI {

    public static class Step4AggregatePMIMapper extends Mapper<LongWritable, Text, Text, Text> {

        // input - <w1 w2 decade, npmi >
        // output - < decade, npmi >, <w1 w2 decade, npmi >

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            String[] split = value.toString().split("\t");
            String newKey = split[0];
            String npmi = split[1];

            String[] keySplit = newKey.split(" ");
            String w1 = keySplit[0];
            String w2 = keySplit[1];
            String decade = keySplit[2];
            context.write(new Text(decade), new Text(npmi)); // output- < decade, npmi >
            context.write(new Text(w1 + " " + w2 + " " + decade), new Text(npmi)); // output- <w1 w2 decade, npmi >
        }
    }

    public static class Step4AggregatePMICombiner extends Reducer<Text, Text, Text, Text> {

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            // This combiner will aggregate NPMI values only for keys that are decades
            if (!key.toString().contains(" ")) {
                double sumNPmi = 0.0;
                for (Text value : values) {
                    double npmi = Double.parseDouble(value.toString());
                    sumNPmi += npmi;
                }
                // Emit the decade and the aggregated NPMI
                context.write(key, new Text(String.valueOf(sumNPmi)));
            } else {
                // For keys that are not decades (i.e., w1 w2 decade)
                for (Text value : values) {
                    context.write(key, value);
                }
            }
        }
    }

    public static class Step4AggregatePMIReducer extends Reducer<Text, Text, Text, Text> {

        // input - < decade, <mid-sum-npmi> >, <w1 w2 decade, npmi >
        // output - < decade, <sumNPmi> >, <w1 w2 decade, npmi >
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            // Check if the key represents just a decade (no spaces present)
            if (!key.toString().contains(" ")) {
                double sumNPmi = 0.0;
                for (Text value : values) {
                    double npmi = Double.parseDouble(value.toString());
                    sumNPmi += npmi; 
                }
                context.write(key, new Text(String.valueOf(sumNPmi))); // output - <decade, sumNPmi>
            } else {
                // we pass through each <w1 w2 decade, npmi>
                for (Text value : values) {
                    context.write(key, value); // output - <w1 w2 decade, npmi> as is
                }
            }
        }
    }

    public static class Step4PartitionerClass extends Partitioner<Text, Text> {
        @Override
        public int getPartition(Text key, Text value, int numPartitions) {
            return (key.hashCode() & Integer.MAX_VALUE) % numPartitions;
        }

    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 4 started!");
        Configuration conf = new Configuration();
        // conf.set("mapred.max.split.size", "67108864"); // 64MB in bytes, more mappers
        Job job = Job.getInstance(conf, "AggregatePMI");
        job.setJarByClass(Step4AggregatePMI.class);
        job.setMapperClass(Step4AggregatePMIMapper.class);
        job.setReducerClass(Step4AggregatePMIReducer.class);
        job.setCombinerClass(Step4AggregatePMICombiner.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setPartitionerClass(Step4PartitionerClass.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setInputFormatClass(TextInputFormat.class);
        FileInputFormat.addInputPath(job, new Path("s3://n-gram-analysis/output_step_3_heb"));
        FileOutputFormat.setOutputPath(job, new Path("s3://n-gram-analysis/output_step_4_heb"));

        if (job.waitForCompletion(true)) {
            System.out.println("Step 4 finished");
        } else {
            System.out.println("Step 4 failed");
        }
    }
}