package ai.wexa.benchmark.dataset;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fetches the MUSAE Facebook Large Page-Page Network dataset edge list.
 * Described at https://snap.stanford.edu/data/facebook-large-page-page-network.html
 * (22,470 nodes, 171,002 edges) but SNAP's own download link for it is dead
 * ("Not available" on the dataset page as of this writing); mirrored, verified
 * byte-for-byte-matching-count (171,002 edges) at the original paper authors'
 * repo below. Cite Rozemberczki et al. 2019 per the SNAP dataset page.
 */
public final class DatasetDownloader {

    private static final String EDGES_CSV_URL =
            "https://raw.githubusercontent.com/benedekrozemberczki/MUSAE/master/input/edges/facebook_edges.csv";

    private DatasetDownloader() {}

    public static void downloadEdgesCsv(Path destination) throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(EDGES_CSV_URL)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode() + " from " + EDGES_CSV_URL);
        }
    }
}
