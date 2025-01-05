import javax.swing.*;
import java.io.File;
import java.util.*;

public class GeneralManager {

    private final GUI gui;
    private final PeerConnection peerConnection;
    private final FileManager fileManager;

    // Paylaşılan klasör, hedef klasör (indirilecek dosyaların gideceği yer)
    private String sharedFolderPath;
    private String destinationFolderPath;

    // Peer listesi ve bilinen (duyurulan) dosyaların listesi
    private final List<String> peers = new ArrayList<>();
    private final List<String> knownFileHashes = new ArrayList<>();
    private final List<String> knownFileNames = new ArrayList<>();
    private final Map<String, Integer> knownFiles = new HashMap<>();
    // Dosyayı kimler paylaşıyor: fileHash -> Set of peer addresses
    private final Map<String, Set<String>> fileOwnersMap = new HashMap<>();
    private final Map<String, String> fileNameToHash = new HashMap<>();
    private static final List<String> excludeMasks = new ArrayList<>();
    private static final List<String> excludeFolders = new ArrayList<>();

    public GeneralManager(GUI gui) {
        this.gui = gui;
        // 1) UDP discovery için PeerConnection
        this.peerConnection = new PeerConnection(this);
        // 2) TCP file manager
        this.fileManager = new FileManager();
        // GUI'yi fileManager'a set et
        this.fileManager.setGUI(gui);
    }

    // ---------------------------
    // Peer Bağlantı (UDP) Yönetimi
    // ---------------------------
    public void connect() {
        new Thread(() -> {
            peerConnection.startFlooding();
            peerConnection.startListening();
            startFileRequestListener(); // TCP sunucu (port 6789)
        }).start();
    }

    public void disconnect() {
        new Thread(() -> {
            peerConnection.disconnect();
        }).start();
    }
    public boolean isConnected() {
        return !peers.isEmpty(); // Peer listesi boş değilse bağlıdır
    }

    public void addPeer(String senderAddress) {
        if (!peers.contains(senderAddress)) {
            peers.add(senderAddress);
        }
        System.out.println("Peer list: " + peers);
    }

    public void removePeer(String senderAddress) {
        peers.remove(senderAddress);
        System.out.println("Peer removed: " + senderAddress);
    }

    public List<String> peers() {
        return peers;
    }

    // ---------------------------
    // File Request Listener (TCP)
    // ---------------------------
    public void startFileRequestListener() {
        fileManager.startListeningForRequests();
    }

    // ---------------------------
    // Paylaşılan Dosyaları Yükleme & Duyurma
    // ---------------------------
    public GeneralManager sharedFolderPath(String path) {
        this.sharedFolderPath = path;
        fileManager.loadSharedFiles(path, excludeMasks, excludeFolders);
        announceFiles();
        return this;
    }

    public void sendExcludeMessage(String type, String value) {
        for (String peer : peers) {
            peerConnection.sendExcludeMessage(type, value, peer);
        }
    }

    public static void addExcludeMask(String mask) {
        if (!excludeMasks.contains(mask)) {
            excludeMasks.add(mask);
        }
    }

    public void removeExcludeMask(String mask) {
        excludeMasks.remove(mask);
    }

    public static void addExcludeFolder(String folder) {
        if (!excludeFolders.contains(folder)) {
            excludeFolders.add(folder);
        }
    }

    public static void removeExcludeFolder(String folder) {
        excludeFolders.remove(folder);
    }

    /**
     * Tüm peers'e paylaşılan dosyaları duyurmak için
     * (fileHash, fileName) -> FILE_ANNOUNCEMENT|<hash>|<filename>
     */
    public void announceFiles() {
        if (sharedFolderPath == null || sharedFolderPath.isEmpty()) {
            System.err.println("Shared folder path is not set.");
            return;
        }

        if (peers.isEmpty()) {
            System.err.println("No peers available to announce files.");
            return;
        }

        for (String fileHash : fileManager.getSharedFiles()) {
            File file = fileManager.getFileByHash(fileHash);
            if (file != null && file.exists()) {
                String fileName = file.getName();
                long fileSize = file.length();
                System.out.println("DEBUG: " + fileName + " size: " + fileSize + " bytes.");

                // CHUNK_SIZE = 256 * 1024 (256 KB) olduğunu varsayıyoruz.
                int totalChunks = (int) Math.ceil((double) fileSize / FileManager.CHUNK_SIZE);
                System.out.println("DEBUG: totalChunks = " + totalChunks);

                // "FILE_ANNOUNCEMENT|hash|filename|totalChunks"
                for (String peer : peers) {
                    peerConnection.sendFileAnnouncement(
                            fileHash,
                            fileName,
                            String.valueOf(totalChunks),
                            peer
                    );
                }
            } else {
                System.err.println("File with hash " + fileHash + " is null or not found on disk.");
            }
        }
    }




    // ---------------------------
    // Destination Folder (indirilecek dosyalar)
    // ---------------------------
    public void setDestinationFolderPath(String destinationPath) {
        this.destinationFolderPath = destinationPath;
        fileManager.setDownloadDirectory(destinationPath);
    }

    // ---------------------------
    // Dosya İndirme (TCP)
    // ---------------------------
    /**
     * GUI'den çift tıklamada: "requestFile(hash, targetPeer)"
     */
    public void requestFile(String fileHash, int totalChunks) {
        // 1) Bu dosyayı paylaşan peer'ları bul
        Set<String> owners = fileOwnersMap.get(fileHash);
        if (owners == null || owners.isEmpty()) {
            System.err.println("No peers own this file: " + fileHash);
            return;
        }

        List<String> ownersList = new ArrayList<>(owners);
        String fileName = getFileNameByHash(fileHash);
        // Artık round-robin'i ownersList üzerinde yapacağız, "peers" değil.

        // 2) DownloadStatus ve benzeri GUI hazırlığı...
        long totalSize = (long) totalChunks * FileManager.CHUNK_SIZE;
        FileManager.DownloadStatus ds = fileManager.new DownloadStatus(fileName + "_merged", totalSize);
        gui.updateDownloadingFile(ds);

        // 3) Thread’ler veya basit for döngüsü
        List<Thread> downloadThreads = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            // Round-robin: chunkIndex'e göre ownersList seç
            String targetPeer = ownersList.get(chunkIndex % ownersList.size());
            int finalChunkIndex = chunkIndex;

            Thread downloadThread = new Thread(() -> {
                int downloadedBytes = fileManager.requestChunk(fileHash, finalChunkIndex, targetPeer);
                if (downloadedBytes > 0) {
                    ds.addDownloadedBytes(downloadedBytes);
                    gui.updateDownloadingFile(ds);
                }
            });
            downloadThreads.add(downloadThread);
            downloadThread.start();
        }

        // 4) Join
        for (Thread thread : downloadThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 5) mergeChunks
        fileManager.mergeChunks(fileHash, totalChunks);
    }






    // ---------------------------
    // Found Files Kaydı (GUI'ye ekleme)
    // ---------------------------
    /**
     * "FILE_ANNOUNCEMENT|<fileHash>|<fileName>" mesajını alınca çağrılır
     */
    public void saveFoundFile(String fileHash, String fileName, int totalChunks) {
        knownFiles.put(fileHash, totalChunks);
        fileNameToHash.put(fileName, fileHash); // dosya adından hash'e gidelim
        gui.addFileToFoundFilesList(fileName);
    }

    public void addFileOwner(String fileHash, String ownerPeer) {
        fileOwnersMap.putIfAbsent(fileHash, new HashSet<>());
        fileOwnersMap.get(fileHash).add(ownerPeer);
        // Örneğin debug log:
        System.out.println("File " + fileHash + " is owned by " + fileOwnersMap.get(fileHash));
    }



    /**
     * "Found files" listesi GUI'de gösterilecek
     */
    public void updateFoundFilesInGUI() {
        SwingUtilities.invokeLater(() -> {
            // Basit şekilde: "hash + fileName" gösterelim
            for (int i = 0; i < knownFileHashes.size(); i++) {
                String h = knownFileHashes.get(i);
                String n = knownFileNames.get(i);
                // Örnek: "n + " (" + h.substring(0,8) + ")"
                String displayName = n + " (hash=" + h.substring(0,8) + ")";
                gui.addFileToFoundFilesList(displayName);
            }
        });
    }

    // ---------------------------
    // Getters
    // ---------------------------
    public String getSharedFolderPath() {
        return sharedFolderPath;
    }

    public String getDestinationFolderPath() {
        return destinationFolderPath;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public int getTotalChunksForFile(String fileHash) {
        return knownFiles.getOrDefault(fileHash, 0);
    }
    // Dosya adından hash'e dönüş
    public String getFileHashByFileName(String fileName) {
        return fileNameToHash.get(fileName);
    }

    public String getFileNameByHash(String fileHash) {
        for (Map.Entry<String, String> entry : fileNameToHash.entrySet()) {
            if (entry.getValue().equals(fileHash)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
