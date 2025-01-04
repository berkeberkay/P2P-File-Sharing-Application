import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GeneralManager {

    private final GUI gui;
    private final PeerConnection peerConnection;
    private final FileManager fileManager;
    private final HashUtils hashUtils;

    // Paylaşılan klasör, hedef klasör (indirilecek dosyaların gideceği yer)
    private String sharedFolderPath;
    private String destinationFolderPath;

    // Peer listesi ve bilinen (duyurulan) dosyaların listesi
    private final List<String> peers = new ArrayList<>();
    private final List<String> knownFileHashes = new ArrayList<>();
    private final List<String> knownFileNames = new ArrayList<>();

    public GeneralManager(GUI gui) {
        this.gui = gui;
        // 1) UDP discovery için PeerConnection
        this.peerConnection = new PeerConnection(this);
        // 2) TCP file manager
        this.fileManager = new FileManager();
        // GUI'yi fileManager'a set et
        this.fileManager.setGUI(gui);
        this.hashUtils = new HashUtils();
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
        fileManager.loadSharedFiles(path);
        announceFiles();
        return this;
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

        // fileManager.getSharedFiles() -> hash setini döndürüyor
        for (String fileHash : fileManager.getSharedFiles()) {
            // Gerçek dosya ismini bulmak isterseniz "fileManager" içinde bir "hash->file" map'i var
            // Onu "getFileByHash" gibi bir metotla alıp file.getName() yapabilirsiniz.
            // Örnek:
            //   File f = fileManager.getFileByHash(fileHash);
            //   String fileName = f.getName();
            // Veya fileName "Unknown" diye set edebilirsiniz,
            // ama mantıklı olan file.getName()'i duyurmak.

            // Burada basitçe "fileManager"da "getFileNameByHash()" gibi bir fonk olduğunu varsayalım
            // Onu ekleyelim:
            File f = fileManager.getFileByHash(fileHash);
            if (f != null) {
                String fileName = f.getName();

                // Tüm peers'e "FILE_ANNOUNCEMENT|hash|filename"
                for (String p : peers) {
                    peerConnection.sendFileAnnouncement(fileHash, fileName, p);
                }
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
    public void requestFile(String fileHash) {
        if (peers.isEmpty()) {
            System.err.println("No peers available.");
            return;
        }
        //bağlantı kurulan bütün peerlara dosya isteği gönderilir
        for (String p : peers) {
            fileManager.requestFile(fileHash, p);
        }
    }

    // ---------------------------
    // Found Files Kaydı (GUI'ye ekleme)
    // ---------------------------
    /**
     * "FILE_ANNOUNCEMENT|<fileHash>|<fileName>" mesajını alınca çağrılır
     */
    public void saveFoundFile(String fileHash, String fileName) {
        // Listeye eklemeden önce, GUI’de göstereceğimiz stringi oluşturalım:
        String displayText = fileName + " (hash=" + fileHash + ")";

        // GUI metodu
        gui.addFileToFoundFilesList(displayText);

        System.out.println("Known file hashes: " + fileHash);
    }

    /**
     * "Found files" listesi GUI'de gösterilecek
     */
    public void updateFoundFilesInGUI() {
        SwingUtilities.invokeLater(() -> {
            gui.clearFoundFilesList(); // Eski liste temizlenir
            for (String fileName : knownFileNames) {
                gui.addFileToFoundFilesList(fileName); // Yeni dosyalar GUI'ye eklenir
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
}
