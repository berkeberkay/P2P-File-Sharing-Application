import javax.swing.*;
import java.io.File;
import java.util.*;

public class GeneralManager {

    private final GUI gui;
    private final PeerConnection peerConnection;
    private final FileManager fileManager;
    private String sharedFolderPath;
    private String destinationFolderPath;
    private final List<String> peers = new ArrayList<>();   // birbirini bilen peerlar
    private final Map<String, Integer> knownFiles = new HashMap<>();
    private final Map<String, Set<String>> fileOwnersMap = new HashMap<>();  // dosya hashi ve sahipleri
    private final Map<String, String> fileNameToHash = new HashMap<>();
    private static final List<String> excludeMasks = new ArrayList<>();
    private static final List<String> excludeFolders = new ArrayList<>();
    private String checkboxExclusionFlag = "false"; // başlangıçta false olmalı guiden kontrol ediliyor


    public GeneralManager(GUI gui) {
        this.gui = gui;
        this.peerConnection = new PeerConnection(this);
        this.fileManager = new FileManager();
        this.fileManager.setGUI(gui);
    }

    public void connect() {
        new Thread(() -> {
            peerConnection.startFlooding();
            peerConnection.startListening();
            startFileRequestListener(); // sunucu dinlemeye başlar
        }).start();
    }

    public void disconnect() {
        new Thread(() -> {
            peerConnection.disconnect();
        }).start();
    }

    public boolean isConnected() {
        return !peers.isEmpty();
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


    public void startFileRequestListener() {
        fileManager.startListeningForRequests();
    }

    // burdaki no usage ancak kullanılıyor.Burayı çöz
    public void sharedFolderPath(String path) throws Exception{
        addSubFoldersToExcludeList(path);
        this.sharedFolderPath = path;
        fileManager.loadSharedFiles(path, excludeMasks, excludeFolders);
        announceFiles();
        addSubFoldersToExcludeList(path);
    }

    private void addSubFoldersToExcludeList(String path) {
        File folder = new File(path);
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                excludeFolders.add(file.getAbsolutePath());
                addSubFoldersToExcludeList(file.getAbsolutePath());
            }
        }
        updateExcludedFolderList();
    }

    private void updateExcludedFolderList() {
        SwingUtilities.invokeLater(() -> {
            this.gui.clearExcludedFolderList();

            for (String folderPath : excludeFolders) {
                File folder = new File(folderPath);
                if (folder.exists() && folder.isDirectory()) {
                    String folderName = folder.getName();
                    this.gui.addFolderToExcludedFolderList(folderName);
                    System.out.println("DEBUG: Excluded folder added to GUI: " + folderName);
                }
            }
        });
    }

    public void sendExcludeMessage(String type, String value) {
        for (String peer : peers) {
            peerConnection.sendExcludeMessage(type, value, peer);
        }
    }

    public void addExcludeMask(String mask) {
        if (!excludeMasks.contains(mask)) {
            excludeMasks.add(mask);
        }
        this.updateFoundFilesInGUI();
    }

    public void removeExcludeMask(String mask) {
        excludeMasks.remove(mask);
        updateFoundFilesInGUI();
    }

    public static void addExcludeFolder(String folder) {
        if (!excludeFolders.contains(folder)) {
            excludeFolders.add(folder);
        }
    }

    public static void removeExcludeFolder(String folder) {
        excludeFolders.remove(folder);
    }


    public void announceFiles() {
        if (sharedFolderPath == null || sharedFolderPath.isEmpty()) {
            System.err.println("Shared folder path is not set.");
            return;
        }

        if (peers.isEmpty()) {
            System.err.println("No peers available to announce files.");
            return;
        }

        File sharedFolder = new File(sharedFolderPath);
        if (!sharedFolder.exists() || !sharedFolder.isDirectory()) {
            System.err.println("Invalid shared folder path: " + sharedFolderPath);
            return;
        }

        File[] files = sharedFolder.listFiles();
        if (files == null) return;

        // yanlışsa sadece root doğruysa alt klasörlerdeki dosyaları paylaş
        if (checkboxExclusionFlag.equals("true")) {
            // Sadece root klasördeki dosyaları paylaş
            for (String fileHash : fileManager.getSharedFiles()) {
                File file = fileManager.getFileByHash(fileHash);
                if (file != null && file.getParentFile().equals(sharedFolder)) {
                    announceFile(fileHash, file);
                }
            }
        } else {
            for (String fileHash : fileManager.getSharedFiles()) {
                File file = fileManager.getFileByHash(fileHash);
                if (file != null) {
                    File parentFolder = file.getParentFile();
                    if (parentFolder.equals(sharedFolder) || parentFolder.getParentFile().equals(sharedFolder)) {
                        announceFile(fileHash, file);
                    }
                }
            }
        }
    }

    private void announceFile(String fileHash, File file) {
        try {
            String fileName = file.getName();
            long fileSize = file.length();
            int totalChunks = (int) Math.ceil((double) fileSize / FileManager.CHUNK_SIZE);
            System.out.println("totalChunks = " + totalChunks);

            // bütün peerlara dosya duyurusu yap
            for (String peer : peers) {
                peerConnection.sendFileAnnouncement(
                        fileHash,
                        fileName,
                        String.valueOf(totalChunks),
                        peer
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to announce file: " + file.getName() + " - " + e.getMessage());
        }
    }


    public void setDestinationFolderPath(String destinationPath) {
        this.destinationFolderPath = destinationPath;
        fileManager.setDownloadDirectory(destinationPath);
    }


    public void requestFile(String fileHash, int totalChunks) {
        Set<String> owners = fileOwnersMap.get(fileHash);
        if (owners == null || owners.isEmpty()) {
            System.err.println("No peers own this file: " + fileHash);
            return;
        }

        List<String> ownersList = new ArrayList<>(owners);
        String fileName = getFileNameByHash(fileHash);
        long totalSize = (long) totalChunks * FileManager.CHUNK_SIZE;
        FileManager.DownloadStatus ds = fileManager.new DownloadStatus(fileName + "_merged", totalSize);
        gui.updateDownloadingFile(ds);

        // toplam chunk sayısı kadar thread oluştur
        List<Thread> downloadThreads = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            // rr algoritması ile peer seç
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
        // threadleri bitir. burası birleştirme işlemi
        for (Thread thread : downloadThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        fileManager.mergeChunks(fileHash, totalChunks);
    }


    public void saveFoundFile(String fileHash, String fileName, int totalChunks) {
        knownFiles.put(fileHash, totalChunks);
        fileNameToHash.put(fileName, fileHash);
        gui.addFileToFoundFilesList(fileName);
    }

    public void addFileOwner(String fileHash, String ownerPeer) {
        fileOwnersMap.putIfAbsent(fileHash, new HashSet<>());
        fileOwnersMap.get(fileHash).add(ownerPeer);
    }


    public void updateFoundFilesInGUI() {
        SwingUtilities.invokeLater(() -> {
            this.gui.clearFoundFilesList();

            for (Map.Entry<String, String> entry : this.fileNameToHash.entrySet()) {
                String fileName = entry.getKey();

                // Mask kontrolü
                boolean isMasked = false;
                for (String mask : excludeMasks) {
                    if (fileName.endsWith(mask)) {
                        isMasked = true;
                        break;
                    }
                }
                // Eğer maskelenmemişse GUI'ye ekle
                if (!isMasked) {
                    gui.addFileToFoundFilesList(fileName);
                }
            }
        });
    }

    public void excludeFilesUnderFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder path: " + folderPath);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                String fileHash = fileNameToHash.get(file.getName());
                if (fileHash != null) {
                    // Diğer peer'lara dosyayı paylaşmayı durdurduğunu bildir
                    for (String peer : peers) {
                        peerConnection.sendExcludeMessage("EXCLUDE_FILE", fileHash, peer);
                    }
                    System.out.println("Excluded file from folder: " + file.getName() + " (hash=" + fileHash + ")");
                }
            }
        }
    }

    public void reannounceFilesUnderFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder path: " + folderPath);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                String fileHash = fileNameToHash.get(file.getName());
                if (fileHash != null) {
                    long fileSize = file.length();
                    int totalChunks = (int) Math.ceil((double) fileSize / FileManager.CHUNK_SIZE);

                    // Diğer peer'lara dosyayı yeniden duyur
                    for (String peer : peers) {
                        peerConnection.sendFileAnnouncement(fileHash, file.getName(), String.valueOf(totalChunks), peer);
                    }
                    System.out.println("Reannounced file from folder: " + file.getName() + " (hash=" + fileHash + ")");
                }
            }
        }
    }

    public void removeFileOwner(String fileHash, String ownerPeer) {
        if (fileOwnersMap.containsKey(fileHash)) {
            fileOwnersMap.get(fileHash).remove(ownerPeer);
            if (fileOwnersMap.get(fileHash).isEmpty()) {
                fileOwnersMap.remove(fileHash);
            }
        }
    }
    public boolean isFileOwnedByAnyPeer(String fileHash) {
        return fileOwnersMap.containsKey(fileHash) && !fileOwnersMap.get(fileHash).isEmpty();
    }

    public void removeFoundFile(String fileHash) {
        String fileName = getFileNameByHash(fileHash);
        if (fileName != null) {
            fileNameToHash.remove(fileName);
            knownFiles.remove(fileHash);
            gui.removeFileFromFoundFilesList(fileName);
            System.out.println("File removed from found files: " + fileName);
        }
    }


    // GET SET METHODS

    public int getTotalChunksForFile(String fileHash) {
        return knownFiles.getOrDefault(fileHash, 0);
    }

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

    public void setCheckNewFilesOnly(boolean selected) {
        this.checkboxExclusionFlag = selected ? "true" : "false";
    }


    public List<String> peers() {
        return peers;
    }
}
