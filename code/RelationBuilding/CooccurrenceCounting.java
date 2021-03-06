package RelationBuilding;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;
import org.apache.hadoop.util.GenericOptionsParser;


class CooccurrenceMapper extends Mapper<Object, Text, Text, IntWritable> {
    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
        String[] names = value.toString().split(" ");
        HashSet<String> names_set = new HashSet<>(Arrays.asList(names)); // 段落中所有人名的集合，目的是去重。
        // 双重for循环，生成所有不同人名对。
        for (String name1 : names_set) {
            for (String name2 : names_set) {
                if (name1 != name2) { // 这里不需要使用!equals()，只要!=就可以保证。
                    context.write(new Text("<" + name1 + "," + name2 + ">"), new IntWritable(1));
                }
            }
        }
    }
}

// 我觉得不需要Partition
// class CooccurrencePartitioner extends HashPartitioner<Text, IntWritable> {
//     public int getPartition(Text key, IntWritable value, int numPartitions) {
//         String term = key.toString().split(",")[0];
//         return super.getPartition(new Text(term), value, numPartitions);
//     }
// }


class CooccurrenceReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
    public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
        int sum = 0;
        //对人名对的同现次数进行求和统计。
        for (IntWritable value : values) {
            sum += value.get();
        }
        context.write(key, new IntWritable(sum));
    }
}


public class CooccurrenceCounting {
    public static void main(String []args) throws Exception {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (otherArgs.length != 2) {
            System.err.println("Usage: CooccurrenceCounting <in> <out>");
            System.exit(2);
        }

        Job job = Job.getInstance(conf, "CooccurrenceCounting");
        job.setJarByClass(CooccurrenceCounting.class);
        job.setMapperClass(CooccurrenceMapper.class);
        // 使用reducer作为combiner，进行优化。
        job.setCombinerClass(CooccurrenceReducer.class);
        job.setReducerClass(CooccurrenceReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        job.setNumReduceTasks(10);

        FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
        FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));

        System.exit(job.waitForCompletion(true)? 0:1);
    }
}
