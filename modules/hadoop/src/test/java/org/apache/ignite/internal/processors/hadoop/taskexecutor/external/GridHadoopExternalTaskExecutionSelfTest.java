/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.hadoop.taskexecutor.external;

import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;
import org.apache.ignite.*;
import org.apache.ignite.igfs.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.processors.hadoop.*;
import org.apache.ignite.internal.util.typedef.*;

import java.io.*;
import java.util.*;

import static org.apache.ignite.internal.processors.hadoop.GridHadoopUtils.*;

/**
 * Job tracker self test.
 */
public class GridHadoopExternalTaskExecutionSelfTest extends GridHadoopAbstractSelfTest {
    /** {@inheritDoc} */
    @Override protected boolean igfsEnabled() {
        return true;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        startGrids(gridCount());
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override public GridHadoopConfiguration hadoopConfiguration(String gridName) {
        GridHadoopConfiguration cfg = super.hadoopConfiguration(gridName);

        cfg.setExternalExecution(true);

        return cfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testSimpleTaskSubmit() throws Exception {
        String testInputFile = "/test";

        prepareTestFile(testInputFile);

        Configuration cfg = new Configuration();

        setupFileSystems(cfg);

        Job job = Job.getInstance(cfg);

        job.setMapperClass(TestMapper.class);
        job.setCombinerClass(TestReducer.class);
        job.setReducerClass(TestReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        job.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(job, new Path("igfs://:" + getTestGridName(0) + "@/" + testInputFile));
        FileOutputFormat.setOutputPath(job, new Path("igfs://:" + getTestGridName(0) + "@/output"));

        job.setJarByClass(getClass());

        IgniteInternalFuture<?> fut = grid(0).hadoop().submit(new GridHadoopJobId(UUID.randomUUID(), 1),
            createJobInfo(job.getConfiguration()));

        fut.get();
    }

    /**
     * @throws Exception If failed.
     */
    public void testMapperException() throws Exception {
        String testInputFile = "/test";

        prepareTestFile(testInputFile);

        Configuration cfg = new Configuration();

        setupFileSystems(cfg);

        Job job = Job.getInstance(cfg);

        job.setMapperClass(TestFailingMapper.class);
        job.setCombinerClass(TestReducer.class);
        job.setReducerClass(TestReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        job.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(job, new Path("igfs://:" + getTestGridName(0) + "@/" + testInputFile));
        FileOutputFormat.setOutputPath(job, new Path("igfs://:" + getTestGridName(0) + "@/output"));

        job.setJarByClass(getClass());

        IgniteInternalFuture<?> fut = grid(0).hadoop().submit(new GridHadoopJobId(UUID.randomUUID(), 1),
            createJobInfo(job.getConfiguration()));

        try {
            fut.get();
        }
        catch (IgniteCheckedException e) {
            IOException exp = X.cause(e, IOException.class);

            assertNotNull(exp);
            assertEquals("Test failure", exp.getMessage());
        }
    }

    /**
     * @param filePath File path to prepare.
     * @throws Exception If failed.
     */
    private void prepareTestFile(String filePath) throws Exception {
        IgniteFs igfs = grid(0).fileSystem(igfsName);

        try (IgfsOutputStream out = igfs.create(new IgfsPath(filePath), true)) {
            PrintWriter wr = new PrintWriter(new OutputStreamWriter(out));

            for (int i = 0; i < 1000; i++)
                wr.println("Hello, world: " + i);

            wr.flush();
        }
    }

    /**
     *
     */
    private static class TestMapper extends Mapper<Object, Text, Text, IntWritable> {
        /** One constant. */
        private IntWritable one = new IntWritable(1);

        /** Line constant. */
        private Text line = new Text("line");

        @Override protected void map(Object key, Text val, Context ctx) throws IOException, InterruptedException {
            ctx.write(line, one);
        }
    }

    /**
     * Failing mapper.
     */
    private static class TestFailingMapper extends Mapper<Object, Text, Text, IntWritable> {
        @Override protected void map(Object key, Text val, Context c) throws IOException, InterruptedException {
            throw new IOException("Test failure");
        }
    }

    /**
     *
     */
    private static class TestReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        /** Line constant. */
        private Text line = new Text("line");

        @Override protected void setup(Context ctx) throws IOException, InterruptedException {
            super.setup(ctx);
        }

        /** {@inheritDoc} */
        @Override protected void reduce(Text key, Iterable<IntWritable> values, Context ctx)
            throws IOException, InterruptedException {
            int s = 0;

            for (IntWritable val : values)
                s += val.get();

            System.out.println(">>>> Reduced: " + s);

            ctx.write(line, new IntWritable(s));
        }
    }
}
