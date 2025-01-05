import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class FileManager {

    // "hash -> File" şeklinde tutuyoruz
    private static final Map<String, File> sharedFiles = new HashMap<>();
    private static String downloadDirectory = "downloads";
    public static final int CHUNK_SIZE = 256 * 1024; // 256 KB
    private final List<String> peers = new ArrayList<>();


    // İstenirse buradan HashUtils import edilerek kullanılabilir
    // import your.package.HashUtils;

    // GUI referansı
    private GUI gui;

    public FileManager() {
        // Varsayılan "downloads" klasörünü oluştur
        File downloadDir = new File(downloadDirectory);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
    }

    /**
     * İndirme klasörünü ayarla (destination folder).
     */
    public void setDownloadDirectory(String downloadDirPath) {
        downloadDirectory = downloadDirPath;
        File downloadDir = new File(downloadDirectory);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
    }

    public void setGUI(GUI gui) {
        this.gui = gui;
    }

    /**
     * Paylaşılan dosyaları yükler (hash tabanlı).
     * Aynı hash’e sahip dosya tekrar eklenmez.
     */
    public void loadSharedFiles(String directoryPath, List<String> excludeMasks, List<String> excludeFolders) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Invalid shared folder path: " + directoryPath);
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            // Klasör dışlama
            if (isExcludedFolder(file, excludeFolders)) {
                System.out.println("Skipping folder: " + file.getName() + " due to exclusion.");
                continue;
            }

            // Alt klasörse recursive çağırma
            if (file.isDirectory()) {
                loadSharedFiles(file.getAbsolutePath(), excludeMasks, excludeFolders);
                continue;
            }

            // Dosya maskesi dışlama
            if (isExcludedByMask(file.getName(), excludeMasks)) {
                System.out.println("Skipping file: " + file.getName() + " due to mask exclusion.");
                continue;
            }

            // Dosyayı hash'le ve paylaşım listesine ekle
            try {
                String fileHash = HashUtils.calculateFileHash(file.getAbsolutePath());
                if (!sharedFiles.containsKey(fileHash)) {
                    sharedFiles.put(fileHash, file);
                    System.out.println("Added file: " + file.getName() + " | Hash: " + fileHash);
                }
            } catch (Exception e) {
                System.err.println("Error calculating hash for " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private boolean isExcludedFolder(File file, List<String> excludeFolders) {
        if (!file.isDirectory()) return false;
        for (String folderName : excludeFolders) {
            if (file.getName().equalsIgnoreCase(folderName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludedByMask(String fileName, List<String> excludeMasks) {
        for (String mask : excludeMasks) {
            if (fileName.toLowerCase().endsWith(mask.toLowerCase())) {
                return true;
            }
        }
        return false;
    }




    /**
     * Paylaşılan dosyaları döndürür (hash seti)
     */
    public Set<String> getSharedFiles() {
        return sharedFiles.keySet();
    }

    /**
     * Dosyayı hash üzerinden bulma
     */
    static File getFileByHash(String fileHash) {
        return sharedFiles.get(fileHash);
    }


    public int requestChunk(String fileHash, int chunkIndex, String targetPeer) {
        int downloadedBytes = -1;  // Başta -1 dönecek (hata durumu)
        try (Socket socket = new Socket(targetPeer, 6789);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("CHUNK_REQUEST|" + fileHash + "|" + chunkIndex);
            dos.flush();

            int chunkSize = dis.readInt();
            if (chunkSize > 0) {
                byte[] buffer = new byte[chunkSize];
                dis.readFully(buffer);

                File chunkFile = new File(downloadDirectory, fileHash + ".part" + chunkIndex);
                try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                    fos.write(buffer);
                }
                System.out.println("Chunk " + chunkIndex + " downloaded successfully from " + targetPeer);
                downloadedBytes = chunkSize;  // İndirilen byte sayısı
            } else {
                System.err.println("Failed to download chunk " + chunkIndex + " from " + targetPeer);
            }
        } catch (IOException e) {
            System.err.println("Error while requesting chunk " + chunkIndex + " from " + targetPeer + ": " + e.getMessage());
        }
        return downloadedBytes;
    }

    public void mergeChunks(String fileHash, int totalChunks) {
        File outputFile = new File(downloadDirectory, fileHash + "_merged");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < totalChunks; i++) {
                File chunkFile = new File(downloadDirectory, fileHash + ".part" + i);
                try (FileInputStream fis = new FileInputStream(chunkFile)) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
            System.out.println("File merged successfully: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error merging chunks: " + e.getMessage());
        }
    }


    /**
     * “isListening” bayrağı ile 2. kez 6789 portunu açmayı engelliyoruz.
     * Bu sayede "Address already in use" hatasını önlüyoruz.
     */
    private boolean isListening = false;
    private ServerSocket serverSocket;

    /**
     * startListeningForRequests(): Klasik sunucu yaklaşımı.
     * Gelen bağlantıda, ilk etapta "FILE_REQUEST|<hash>" okuyabilir,
     * ardından "dos.writeLong(fileSize)" vs.
     *
     * Ama isterseniz “Ters TCP” yapıyorsanız bu metot chunk okuyan kısma dönüştürülebilir.
     */
    public void startListeningForRequests() {
        if (isListening) {
            System.out.println("Already listening on port 6789...");
            return;
        }
        isListening = true;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(6789);
                System.out.println("Listening for file requests on port 6789...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Incoming TCP connection from " + clientSocket.getInetAddress());

                    // Ayrı bir thread veya inline handle
                    new Thread(() -> handleClientRequest(clientSocket)).start();
                }

            } catch (IOException e) {
                System.err.println("Error starting file request listener: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Sunucu tarafında, istemcinin "FILE_REQUEST|<hash>" mesajını okur,
     * paylaşılan dosyayı bulur, chunk gönderir.
     */
    private void handleClientRequest(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            String request = dis.readUTF();
            String[] parts = request.split("\\|");

            if (parts.length < 2) {
                System.err.println("Invalid request format: " + request);
                dos.writeInt(-1); // Geçersiz istek
                return;
            }

            String requestType = parts[0];
            String fileHash = parts[1];

            // Dosyayı hash'e göre bul
            File file = FileManager.getFileByHash(fileHash);
            if (file == null || !file.exists()) {
                System.err.println("Requested file not found: " + fileHash);
                dos.writeInt(-1); // Dosya bulunamadı
                return;
            }

            if ("CHUNK_REQUEST".equals(requestType)) {
                int chunkIndex = Integer.parseInt(parts[2]);
                int CHUNK_SIZE = 256 * 1024;
                long offset = (long) chunkIndex * CHUNK_SIZE;

                if (offset >= file.length()) {
                    System.err.println("Requested chunk index out of range: " + chunkIndex);
                    dos.writeInt(-1);
                    return;
                }

                try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                    raf.seek(offset);
                    int bytesToRead = (int) Math.min(CHUNK_SIZE, file.length() - offset);
                    byte[] buffer = new byte[bytesToRead];
                    raf.readFully(buffer);

                    dos.writeInt(bytesToRead);
                    dos.write(buffer);
                    dos.flush();
                    System.out.println("Chunk " + chunkIndex + " sent successfully for file: " + file.getName());
                }
            } else {
                dos.writeInt(-1); // Geçersiz istek
            }
        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }



    private void handleChunkRequest(String[] parts, File file, DataOutputStream dos) {
        if (parts.length < 3) {
            System.err.println("Invalid CHUNK_REQUEST format: " + String.join("|", parts));
            try {
                dos.writeInt(-1); // Geçersiz istek
            } catch (IOException e) {
                System.err.println("Error sending response: " + e.getMessage());
            }
            return;
        }

        try {
            int chunkIndex = Integer.parseInt(parts[2]);
            int CHUNK_SIZE = 256 * 1024;
            long offset = (long) chunkIndex * CHUNK_SIZE;

            if (offset >= file.length()) {
                System.err.println("Requested chunk index out of range: " + chunkIndex);
                dos.writeInt(-1); // Geçersiz chunk
                return;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                int bytesToRead = (int) Math.min(CHUNK_SIZE, file.length() - offset);
                byte[] buffer = new byte[bytesToRead];
                raf.readFully(buffer);

                dos.writeInt(bytesToRead); // Chunk boyutu
                dos.write(buffer);         // Chunk verisi
                dos.flush();
                System.out.println("Chunk " + chunkIndex + " sent successfully for file: " + file.getName());
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid chunk index format: " + parts[2]);
            try {
                dos.writeInt(-1); // Hatalı chunk
            } catch (IOException ioException) {
                System.err.println("Error sending response: " + ioException.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Error processing chunk request: " + e.getMessage());
        }

    }




    // DownloadStatus iç sınıf (yüzde takibi)
    public class DownloadStatus {
        private final String fileName;
        private final long totalSize;
        private long downloadedSize;

        public DownloadStatus(String fileName, long totalSize) {
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.downloadedSize = 0;
        }

        public void addDownloadedBytes(long chunkBytes) {
            this.downloadedSize += chunkBytes;
        }

        public int getPercentage() {
            if (totalSize <= 0) return 0;
            return (int) ((downloadedSize * 100) / totalSize);
        }

        public String getFileName() {
            return fileName;
        }

        @Override
        public String toString() {
            return fileName + " - " + getPercentage() + "%";
        }
    }
}
