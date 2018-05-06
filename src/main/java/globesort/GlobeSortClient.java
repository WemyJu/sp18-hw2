package globesort;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.lang.RuntimeException;
import java.lang.Exception;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;

public class GlobeSortClient {

    private final ManagedChannel serverChannel;
    private final GlobeSortGrpc.GlobeSortBlockingStub serverStub;

	private static int MAX_MESSAGE_SIZE = 100 * 1024 * 1024;

    private String serverStr;

    public GlobeSortClient(String ip, int port) {
        this.serverChannel = ManagedChannelBuilder.forAddress(ip, port)
				.maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .usePlaintext(true).build();
        this.serverStub = GlobeSortGrpc.newBlockingStub(serverChannel);

        this.serverStr = ip + ":" + port;
    }

    public void ping() throws Exception {
        System.out.println("Pinging " + serverStr + "...");
        long curr_time = new Date().getTime();
        serverStub.ping(Empty.newBuilder().build());
        double latency = (double)(new Date().getTime()-curr_time)/1000.0/2;
        System.out.println("Ping successful.  Latency: "+latency+" s.");
    }

    public void sort(Integer[] values) throws Exception {
        System.out.println("Requesting server to sort array");
        // System.out.println(Arrays.toString(values));
        long curr_time = new Date().getTime();
        SortArrayInfo sort_request = SortArrayInfo.newBuilder().addAllValues(Arrays.asList(values)).build();
        SortArrayInfo sort_response = serverStub.sortIntegers(sort_request);
        Integer[] sort_result = sort_response.getValuesList().toArray(new Integer[sort_response.getValuesCount()]);
        long total_time = new Date().getTime()-curr_time;
        long sorting_time = sort_response.getSortingTime();
        double one_way_throughput = (double)(total_time-sorting_time)/1000.0/2.0;
        System.out.println("Sorted array");
        // System.out.println(Arrays.toString(sort_result));
        System.out.println("Application throught: "+(double)(total_time)/1000.0+" s.");
        System.out.println("One-way network throughput: "+one_way_throughput+" s.");
    }

    public void shutdown() throws InterruptedException {
        serverChannel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    }

    private static Integer[] genValues(int numValues) {
        ArrayList<Integer> vals = new ArrayList<Integer>();
        Random randGen = new Random();
        for(int i : randGen.ints(numValues).toArray()){
            vals.add(i);
        }
        return vals.toArray(new Integer[vals.size()]);
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("GlobeSortClient").build()
                .description("GlobeSort client");
        parser.addArgument("server_ip").type(String.class)
                .help("Server IP address");
        parser.addArgument("server_port").type(Integer.class)
                .help("Server port");
        parser.addArgument("num_values").type(Integer.class)
                .help("Number of values to sort");

        Namespace res = null;
        try {
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        Namespace cmd_args = parseArgs(args);
        if (cmd_args == null) {
            throw new RuntimeException("Argument parsing failed");
        }

        Integer[] values = genValues(cmd_args.getInt("num_values"));

        GlobeSortClient client = new GlobeSortClient(cmd_args.getString("server_ip"), cmd_args.getInt("server_port"));
        try {
            client.ping();
            client.ping();
            client.sort(values);
        } finally {
            client.shutdown();
        }
    }
}
