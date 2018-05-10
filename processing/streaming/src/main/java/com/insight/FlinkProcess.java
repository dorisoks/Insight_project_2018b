package com.insight;


import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
//import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.WindowedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.util.Collector;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.connectors.cassandra.CassandraSink;

import static java.util.concurrent.TimeUnit.*;


public class FlinkProcess {
    public static void main(String[] args) throws Exception {
        // create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        // start kafka producer !!!!!!!!! using python producer
        //KProducer kp = new KProducer();
        //kp.kafkaProducer();

        // create new property of Flink
        // set zookeeper and flink url
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "ec2-50-112-36-122.us-west-2.compute.amazonaws.com:9092");
        properties.setProperty("zookeeper.connect", "ec2-50-112-36-122.us-west-2.compute.amazonaws.com:2181");
        //properties.setProperty("group.id", "flink_consumer");


        // read 'topic' from Kafka producer
        FlinkKafkaConsumer010<String> kafkaConsumer = new FlinkKafkaConsumer010<String>("ctest",
                new SimpleStringSchema(), properties);

        // convert kafka stream to data stream
        DataStream<String> rawInputStream = env.addSource(kafkaConsumer);

//        // inputs are 'revision', 'article_id', 'rev_id', 'article title', 'timestamp',  'username', 'userid','realtime'
//        DataStream<Tuple2<String, Long>> windowedoutput = rawInputStream
//                .map(line -> line.split(" "))
//                .flatMap(new FlatMapFunction<String[], Tuple2<String, Long>>() {
//                    @Override
//                    public void flatMap(String[] s, Collector<Tuple2<String, Long>> collector) throws Exception {
//                        collector.collect(new Tuple2<String, Long>(s[5], 1L));
//                    }
//                })
//                .keyBy(0)
//                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
//                .sum(1);
//                //.window(TumblingEventTimeWindows.of(Time.seconds(1)))


        // input data: ['revision', 'article_id', 'rev_id', 'article_title', 'event_time', 'username', 'userid', 'proc_date', 'proc_time']
        // output data: ['partition id', 'proc_time', 'count']
        // transformation: count all input within 0.5s time window
        // save output to Cassandra table: allInputCountSecond
        // table:
        DataStream<Tuple3<String, String, Long>> windowedoutput = rawInputStream
                .map(line -> line.split(" "))
                .flatMap(new FlatMapFunction<String[], Tuple3<String, String, Long>>() {
                    @Override
                    public void flatMap(String[] s, Collector<Tuple3<String,String, Long>> collector) throws Exception {
                        collector.collect(new Tuple3<String,String, Long>("a", s[8], 1L));

                    }
                })
                .keyBy(0, 1)
                .window(TumblingProcessingTimeWindows.of(Time.milliseconds(500)))
                .sum(2);


        CassandraSink.addSink(windowedoutput)
                .setQuery("INSERT INTO ks.totalInputCountSecond (global_id, proc_time, count) " +
                        "values (?, ?, ?);")
                .setHost("ec2-50-112-36-122.us-west-2.compute.amazonaws.com")
                .build();


        // input data: ['revision', 'article_id', 'rev_id', 'article_title', 'event_time', 'username', 'userid', 'proc_date', 'proc_time']
        // output data: ['username', 'proc_time', 'count']
        // transformation: count all input within 0.5s time window
        // save output to Cassandra table: singleUserCountSecond
        // table:
        DataStream<Tuple3<String, String, Long>> singleUserCount = rawInputStream
                .map(line -> line.split(" "))
                .flatMap(new FlatMapFunction<String[], Tuple3<String, String, Long>>() {
                    @Override
                    public void flatMap(String[] s, Collector<Tuple3<String,String, Long>> collector) throws Exception {
                        collector.collect(new Tuple3<String,String, Long>(s[5], s[8], 1L));

                    }
                })
                .keyBy(0, 1)
                .window(TumblingProcessingTimeWindows.of(Time.milliseconds(500)))
                .sum(2);


        CassandraSink.addSink(singleUserCount)
                .setQuery("INSERT INTO ks.singleUserCount (username, proc_time, count) " +
                        "values (?, ?, ?);")
                .setHost("ec2-50-112-36-122.us-west-2.compute.amazonaws.com")
                .build();


        // input data: ['revision', 'article_id', 'rev_id', 'article_title', 'event_time', 'username', 'userid', 'proc_date', 'proc_time']
        // output data: ['global_id', 'proc_time', 'username', 'count']
        // transformation: count all input within 0.5s time window
        // save output to Cassandra table: flaggedUser
        // table:
        DataStream<Tuple4<String, String, String, Long>> flaggedUser = rawInputStream
                .map(line -> line.split(" "))
                .flatMap(new FlatMapFunction<String[], Tuple4<String, String, String, Long>>() {
                    @Override
                    public void flatMap(String[] s, Collector<Tuple4<String, String, String, Long>> collector) throws Exception {
                        collector.collect(new Tuple4<String,String, String, Long>("a", s[8], s[5], 1L));

                    }
                })
                .keyBy(0, 1, 2)
                .window(TumblingProcessingTimeWindows.of(Time.milliseconds(500)))
                .sum(3)
                .filter(a -> a.f3 > 50);
//                .filter(new FilterFunction<Tuple3<String, String, Long>>() {
//                    @Override
//                    public boolean filter(Tuple3<String, String, Long> stringStringLongTuple3) throws Exception {
//                        return stringStringLongTuple3.f2 > 100;
//                    }
//                });


        CassandraSink.addSink(flaggedUser)
                .setQuery("INSERT INTO ks.flaggedUser (global_id, proc_time, username, count) " +
                        "values (?,?, ?, ?);")
                .setHost("ec2-50-112-36-122.us-west-2.compute.amazonaws.com")
                .build();


//        // count the total number of processed line in window of 5 sec
//        DataStream<Tuple2<Long, Long>> winOutput = rawInputStream
//                .map(line -> line.split(" "))
//                .flatMap(new FlatMapFunction<String[], Tuple2<Long, Long>>() {
//
//                    // Create an accumulator
//                    private long linesNum = 0;
//
//
//                    @Override
//                    public void flatMap(String[] lines, Collector<Tuple2<Long, Long>> out) throws Exception {
//                        out.collect(new Tuple2<Long, Long>(linesNum++, 1L));
//
//                    }
//                })
//                .assignTimestampsAndWatermarks(new RegionInfoTimeStampGenerator())
//                .keyBy(0)
////                .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
//                .window(TumblingEventTimeWindows.of(Time.milliseconds(50)))
//                .reduce((a,b) -> new Tuple2<>(a.f0, a.f1+b.f1));


//        // read in raw input, then parse it to save only 3 elements: article title, timestamp, username
//        DataStream<Tuple3< String, String, String>> output = rawInputStream
//                .map(line -> line.split(" "))
//                .flatMap(new FlatMapFunction<String[], Tuple3<String, String, String>>() {
//                    @Override
//                    public void flatMap(String[] s, Collector<Tuple3< String, String, String>> collector) throws Exception {
//                        collector.collect(new Tuple3<String, String, String>(s[3], s[4], s[5]));
//                    }
//                });


//        // Tuple2 save two elements pair
//        DataStream<Tuple2<Long, String>> result =
//                // split up the lines in pairs (2-tuples) containing: (word,1)
//                rawInputStream.flatMap(new FlatMapFunction<String, Tuple2<Long, String>>() {
//                    //@Override
//                    public void flatMap(String value, Collector<Tuple2<Long, String>> out) {
//                        // normalize and split the line
//                        String[] words = value.toLowerCase().split("\\W+");
//
//                        // emit the pairs
//                        for (String word : words) {
//                            //Do not accept empty word, since word is defined as primary key in C* table
//                            if (!word.isEmpty()) {
//                                out.collect(new Tuple2<Long, String>(1L, word));
//                            }
//                        }
//                    }
//                });


//        //Update the results to C* sink


//        // update output to cassandra:
//                CassandraSink.addSink(output)
//                .setQuery("INSERT INTO playground.testTable2 ( arttitle, time, username) " +
//                        "values (?, ?, ?);")
//                .setHost("ec2-50-112-36-122.us-west-2.compute.amazonaws.com")
//                .build();


//        // save window aggregation results:
//        CassandraSink.addSink(winOutput)
//                .setQuery("INSERT INTO playground.testTable3 (id, count) " +
//                        "values (?, ?);")
//                .setHost("ec2-50-112-36-122.us-west-2.compute.amazonaws.com")
//                .build();


        env.execute();

    }

    private static class SumAggregation  implements AggregateFunction<Tuple2<String, Long>, Tuple2<String, Long>, Tuple2<String, Long>> {

        // Similar to WinRateAggregator, but also count the number of occurences.
        // Input: <hero_id:String,win/lose:Integer,start_time:Long>
        // Accumulator: <hero_id:String,total_win:Long,total_played:Long,latest_start_time:Long>
        // Output: <hero_id:String,win_rate:Float,count:Long,latest_start_time:Long>

        @Override
        public Tuple2<String, Long> createAccumulator() {
            return new Tuple2<>("", 0L);
        }

        @Override
        public Tuple2<String, Long> add(Tuple2<String, Long> value, Tuple2<String,  Long> accumulator) {
            // Update accumulator with the new value. Here we update the start_time to be the latest
            return new Tuple2<>(value.f0,
                    accumulator.f1 + value.f1);

        }

        @Override
        public Tuple2<String, Long> getResult(Tuple2<String, Long> stringLongTuple2) {
            return null;
        }

        @Override
        public Tuple2<String, Long> merge(Tuple2<String, Long> stringLongTuple2, Tuple2<String, Long> acc1) {
            return null;
        }


    }

}
