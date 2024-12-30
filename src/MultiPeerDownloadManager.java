import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates chunk-based downloading from multiple peers in parallel.
 * Each chunk is requested from one peer, then all chunks are merged.
 */
public class MultiPeerDownloadManager {

    private static final int CHUNK_SIZE = 256 * 1024; // 256KB

    /**
     * Represents an active download of a specific file (identified by fileHash).
     * Tracks total size, downloaded bytes, and chunk tasks.
     */
    public static class DownloadTask {
        private final String fileHash;
        private final long totalBytes;
        private final AtomicLong downloadedBytes;  // Atomic for thread-safe increments
        private final List<ChunkDownloadTask> chunks; // All chunk tasks
        private boolean completed;

        public DownloadTask(String fileHash, long totalBytes) {
            this.fileHash = fileHash;
            this.totalBytes = totalBytes;
            this.downloadedBytes = new AtomicLong(0);
            this.chunks = new ArrayList<>();
            this.completed = false;
        }

        public String getFileHash() {
            return fileHash;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getDownloadedBytes() {
            return downloadedBytes.get();
        }

        public int getProgressPercentage() {
            if (totalBytes == 0) return 0;
            return (int) ((downloadedBytes.get() * 100) / totalBytes);
        }

        public boolean isCompleted() {
            return completed;
        }

        public synchronized void markCompleted() {
            this.completed = true;
        }

        /**
         * Adds a chunk to this task's list (so we can keep track).
         */
        public void addChunkTask(ChunkDownloadTask chunkTask) {
            chunks.add(chunkTask);
        }

        /**
         * Updates the total downloaded bytes so we can track progress.
         */
        public void updateDownloadedBytes(long bytes) {
            downloadedBytes.addAndGet(bytes);
        }
    }

    /**
     * Represents a single chunk download from a specific peer.
     * Runs in its own thread (Runnable).
     */
    public static class ChunkDownloadTask implements Runnable {
        private final DownloadTask parentTask;
        private final String peerIP;
        private final int chunkIndex;
        private final String destinationDir;

        public ChunkDownloadTask(DownloadTask parentTask, String peerIP, int chunkIndex, String destinationDir) {
            this.parentTask = parentTask;
            this.peerIP = peerIP;
            this.chunkIndex = chunkIndex;
            this.destinationDir = destinationDir;
        }

        @Override
        public void run() {
            // Each chunk is saved as <fileHash>.part<chunkIndex> in the destination folder
            File chunkFile = new File(destinationDir, parentTask.getFileHash() + ".part" + chunkIndex);

            // In a real implementation, you'd have a specific protocol to request "chunkIndex" of "fileHash"
            // and the peer would read from its local file chunk. For demonstration, we do a simple read.
            try (Socket socket = new Socket(peerIP, 6789);
                 OutputStream out = socket.getOutputStream();
                 InputStream in = socket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(chunkFile)) {

                // 1) Send a request: e.g. "GET_CHUNK fileHash chunkIndex"
                //    Here we just write a placeholder request
                String request = "GET_CHUNK " + parentTask.getFileHash() + " " + chunkIndex;
                out.write(request.getBytes());
                out.flush();

                // 2) Read chunk data
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                long totalRead = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    parentTask.updateDownloadedBytes(bytesRead); // update overall progress
                    totalRead += bytesRead;
                }
                System.out.println("Chunk #" + chunkIndex + " downloaded from " + peerIP
                        + " (" + totalRead + " bytes)");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Main method to handle a multi-peer download.
     * @param fileHash        Unique hash of the file to download
     * @param peers           List of peer IP addresses that have the file
     * @param destinationDir  Local folder to store chunks and final file
     * @throws Exception
     */
    public static DownloadTask downloadFileFromMultiplePeers(String fileHash,
                                                             List<String> peers,
                                                             String destinationDir) throws Exception {

        // 1) Determine total file size (we only do one call here to the first peer, but you can do more robust logic)
        //    In a real system, you'd do a handshake: "GET_FILE_SIZE fileHash" => return file size
        long fileSize = getFileSizeFromPeer(fileHash, peers.get(0));
        if (fileSize <= 0) {
            throw new IOException("Could not get file size from peer. File might not exist on that peer.");
        }

        // 2) Create a DownloadTask to track progress
        DownloadTask downloadTask = new DownloadTask(fileHash, fileSize);

        // 3) Calculate number of chunks
        int totalChunks = (int) Math.ceil(fileSize / (double) CHUNK_SIZE);

        // 4) Assign chunks to peers in a round-robin or any distribution
        int peerIndex = 0;
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            String assignedPeer = peers.get(peerIndex);
            ChunkDownloadTask chunkTask = new ChunkDownloadTask(downloadTask, assignedPeer, chunkIndex, destinationDir);
            downloadTask.addChunkTask(chunkTask);

            peerIndex = (peerIndex + 1) % peers.size();
        }

        // 5) Start chunk tasks in parallel
        List<Thread> threads = new ArrayList<>();
        for (ChunkDownloadTask chunk : downloadTask.chunks) {
            Thread t = new Thread(chunk);
            t.start();
            threads.add(t);
        }

        // 6) Wait for all threads to finish
        for (Thread t : threads) {
            t.join();
        }

        // 7) Merge chunk files
        List<File> chunkFiles = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            File chunk = new File(destinationDir, fileHash + ".part" + i);
            chunkFiles.add(chunk);
        }

        String finalFilePath = destinationDir + File.separator + fileHash + "_FINAL.txt";
        FileManager.mergeFiles(chunkFiles, finalFilePath);

        // Mark as completed
        downloadTask.markCompleted();
        System.out.println("Downloaded and merged file: " + finalFilePath);

        // 8) (Optional) Delete chunk files or keep them
        // for (File chunk : chunkFiles) {
        //     chunk.delete();
        // }

        return downloadTask;
    }

    /**
     * Mock method: In a real scenario, you'd query the peer over a socket
     * to request "file size for fileHash" and read the response.
     */
    private static long getFileSizeFromPeer(String fileHash, String peerIP) {
        // Placeholder: suppose the file is ~1MB on the peer
        // In a real implementation, you'd do an actual request/response handshake
        return 1_048_576; // 1 MB
    }
}
