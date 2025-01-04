import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.*;

public class FileManager {

    // "hash -> File" şeklinde tutuyoruz
    private static final Map<String, File> sharedFiles = new HashMap<>();
    private static String downloadDirectory = "downloads";

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
    public static void setDownloadDirectory(String downloadDirPath) {
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
    public void loadSharedFiles(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Invalid shared folder path: " + directoryPath);
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                try {
                    // HashUtils ile hash hesapla
                    String fileHash = HashUtils.calculateFileHash(file.getAbsolutePath());
                    if (!sharedFiles.containsKey(fileHash)) {
                        // Aynı hash yoksa ekle
                        sharedFiles.put(fileHash, file);
                        System.out.println("Added file: " + file.getName() + " | Hash: " + fileHash);
                    } else {
                        System.out.println("Skipping (same hash) : " + file.getName());
                    }
                } catch (Exception e) {
                    System.err.println("Error calculating hash for " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("Shared files loaded (by hash): " + sharedFiles.keySet());
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
    public static File getFileByHash(String fileHash) {
        return sharedFiles.get(fileHash);
    }

    /**
     * Klasik TCP mantığında dosya gönderen taraf (sunucu)
     * “handleFileRequest” => “fileHash” üzerinden dosyayı bulur.
     * chunk’ları 256 KB boyutunda gönderir, her chunk sonrası ACK bekler.
     */
    public static void handleFileRequest(String fileHash, String requesterIP) {
        File file = getFileByHash(fileHash);

        if (file == null || !file.exists()) {
            System.err.println("Requested file not found (hash): " + fileHash);
            return;
        }

        try (Socket socket = new Socket(requesterIP, 6789);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {

            long fileSize = file.length();
            int CHUNK_SIZE = 256 * 1024;  // 256 KB
            int chunkCount = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
            byte[] buffer = new byte[CHUNK_SIZE];

            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                raf.seek((long) chunkIndex * CHUNK_SIZE);
                int bytesRead = raf.read(buffer);

                dos.writeInt(chunkIndex);       // Chunk index
                dos.writeInt(bytesRead);        // Chunk size
                dos.write(buffer, 0, bytesRead);
                dos.flush();

                // ACK al
                int ack = dis.readInt();
                if (ack != chunkIndex) {
                    System.err.println("Error: ACK mismatch for chunk " + chunkIndex);
                    return;
                }
            }

            // Son chunk = -1
            dos.writeInt(-1);
            dos.flush();
            System.out.println("File sent successfully: " + file.getName()
                    + " (hash=" + fileHash + ")");

        } catch (IOException e) {
            System.err.println("Error while sending file: " + e.getMessage());
        }
    }

    /**
     * İsteyen taraf (istemci) “fileHash” parametresiyle
     * chunk bazında dosyayı indirsin, GUI’de % güncellesin.
     */
    public void requestFile(String fileHash, String targetPeer) {
        // “targetPeer” = dosyayı barındıran peer IP
        // “fileHash” = hangi dosya (hash) isteniyor
        try (Socket socket = new Socket(targetPeer, 6789);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream());
        ) {
            // 1) Sunucuya "FILE_REQUEST|<hash>" şeklinde mesaj yollayabiliriz.
            //    (Protokolü siz belirliyorsunuz.)
            dos.writeUTF("FILE_REQUEST|" + fileHash);
            dos.flush();

            // 2) Sunucudan dosya boyutu al
            long totalSize = dis.readLong();
            if (totalSize < 0) {
                System.err.println("File not found on server (hash): " + fileHash);
                return;
            }

            // “DownloadStatus” oluştur
            // Mesela “unknownFileHash_XXXX” isim verelim.
            // Veya siz, sunucunun da orijinal dosyaAdı yollamasını sağlayabilirsiniz.
            String fileName = "download_" + fileHash.substring(0, 8);
            // “XYZ123” gibi kısaltma

            DownloadStatus ds = new DownloadStatus(fileName, totalSize);
            // %0 olarak eklemek için
            gui.updateDownloadingFile(ds);

            // 3) Dosyayı “downloadDirectory/fileName” yoluna yaz
            try (RandomAccessFile raf = new RandomAccessFile(downloadDirectory + "/" + fileName, "rw")) {
                while (true) {
                    int chunkIndex = dis.readInt();
                    if (chunkIndex == -1) {
                        // Bitti
                        break;
                    }
                    int chunkSize = dis.readInt();
                    byte[] buffer = new byte[chunkSize];
                    dis.readFully(buffer);

                    raf.seek((long) chunkIndex * (256L * 1024));
                    raf.write(buffer);

                    // İlerleme güncelle
                    ds.addDownloadedBytes(chunkSize);
                    gui.updateDownloadingFile(ds);

                    // ACK
                    dos.writeInt(chunkIndex);
                    dos.flush();
                }
                System.out.println("File received successfully: " + fileName
                        + " (hash=" + fileHash + ")");
            }

        } catch (IOException e) {
            System.err.println("Error while requesting file from " + targetPeer + ": " + e.getMessage());
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

            // 1) "FILE_REQUEST|<hash>" bekliyoruz
            String request = dis.readUTF();
            String[] parts = request.split("\\|");
            if (parts.length < 2 || !"FILE_REQUEST".equals(parts[0])) {
                System.err.println("Invalid request: " + request);
                dos.writeLong(-1); // dosya yok
                return;
            }
            String fileHash = parts[1];
            File file = getFileByHash(fileHash);
            if (file == null || !file.exists()) {
                System.err.println("Requested file not found: " + fileHash);
                dos.writeLong(-1);
                return;
            }

            // 2) Dosya boyutu
            long fileSize = file.length();
            dos.writeLong(fileSize);

            // 3) 256 KB chunk gönder
            int CHUNK_SIZE = 256 * 1024;
            int chunkCount = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
            byte[] buffer = new byte[CHUNK_SIZE];

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    raf.seek((long) chunkIndex * CHUNK_SIZE);
                    int bytesRead = raf.read(buffer);

                    dos.writeInt(chunkIndex);
                    dos.writeInt(bytesRead);
                    dos.write(buffer, 0, bytesRead);
                    dos.flush();

                    // ACK
                    int ack = dis.readInt();
                    if (ack != chunkIndex) {
                        System.err.println("ACK mismatch chunk " + chunkIndex);
                        return;
                    }
                }
            }
            dos.writeInt(-1);
            System.out.println("File sent successfully: " + file.getName()
                    + " (hash=" + fileHash + ")");

        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
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
