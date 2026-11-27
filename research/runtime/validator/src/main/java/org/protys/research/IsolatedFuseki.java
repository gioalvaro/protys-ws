package org.protys.research;

import java.nio.file.*;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.tdb2.TDB2;
import org.apache.jena.tdb2.TDB2Factory;

/** Fresh private TDB2 dataset, matching the union-default-graph production policy. */
public class IsolatedFuseki {
  public static void main(String[] args) throws Exception {
    Dataset dataset = TDB2Factory.connectDataset(args[1]);
    dataset.getContext().set(TDB2.symUnionDefaultGraph, true);
    FusekiServer server =
        FusekiServer.create().port(0).loopback(true).add("/academic", dataset).build();
    server.start();
    Files.writeString(Path.of(args[0]), Integer.toString(server.getPort()));
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  server.stop();
                  dataset.close();
                }));
    Thread.currentThread().join();
  }
}
