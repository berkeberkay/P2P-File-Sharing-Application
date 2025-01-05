import java.io.IOException;
import java.net.*;
import java.util.List;

public class PeerConnection {

    public static final int PORT = 9876;
    private DatagramSocket socket;
    private final GeneralManager generalManager;

    public PeerConnection(GeneralManager generalManager) {
        this.generalManager = generalManager;
        try {
            socket = new DatagramSocket(PORT);
            socket.setBroadcast(true);
            System.out.println("DatagramSocket successfully initialized on port " + PORT);
        } catch (SocketException e) {
            System.err.println("Error initializing DatagramSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void startFlooding() {
        // Peer keşfi (Discovery)
        System.out.println("Starting peer discovery... Local IP : " + getLocalIPAddress());
        sendMessage("DISCOVERY_REQUEST", "255.255.255.255");
        System.out.println("Discovery request sent");
    }

    public void startListening() {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }

        new Thread(() -> {
            System.out.println("Listening for incoming connections (UDP)...");
            try {
                byte[] buffer = new byte[1024];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    String senderAddress = packet.getAddress().getHostAddress();

                    handleIncomingMessage(message, senderAddress);
                }
            } catch (IOException e) {
                System.err.println("Error while listening: " + e.getMessage());
            }
        }).start();
    }

    private void handleIncomingMessage(String message, String senderAddress) {
        String localIPAddress = getLocalIPAddress();
        if (localIPAddress != null && localIPAddress.equals(senderAddress)) {
            return; // Kendi IP'sinden gelen mesajı yok say
        }

        String[] parts = message.split("\\|");
        String messageType = parts[0];

        switch (messageType) {
            case "DISCOVERY_REQUEST" -> {
                // Peer keşfi
                generalManager.addPeer(senderAddress);
                sendMessage("RESPONSE", senderAddress);
            }
            case "RESPONSE" -> {
                // Peer keşfi cevabı
                generalManager.addPeer(senderAddress);
            }
            case "DISCONNECT" -> {
                // Peer ayrılıyor
                generalManager.removePeer(senderAddress);
                System.out.println("Peer disconnected: " + senderAddress);
            }
            case "FILE_ANNOUNCEMENT" -> {
                // "FILE_ANNOUNCEMENT|<fileHash>|<fileName>|<totalChunks>"
                if (parts.length < 4) {
                    System.err.println("Invalid FILE_ANNOUNCEMENT message format: " + message);
                    return;
                }
                String fileHash = parts[1];
                String fileName = parts[2];
                int totalChunks = Integer.parseInt(parts[3]);
                generalManager.addFileOwner(fileHash, senderAddress);
                generalManager.saveFoundFile(fileHash, fileName, totalChunks);
            }
            case "EXCLUDE_MASK" -> {
                String excludeMask = parts[1];
                GeneralManager.addExcludeMask(excludeMask);
                break;
            }
            case "EXCLUDE_FOLDER" -> {
                // EXCLUDE_FOLDER|folderName
                String excludeFolder = parts[1];
                GeneralManager.addExcludeFolder(excludeFolder);
                break;
            }
            default -> {
                System.err.println("Unknown UDP message type received: " + message);
            }
        }
    }

    public void sendMessage(String messageType, String address, String... args) {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }

        try {
            StringBuilder messageBuilder = new StringBuilder(messageType);
            for (String arg : args) {
                messageBuilder.append("|").append(arg);
            }
            String fullMessage = messageBuilder.toString();

            byte[] buffer = fullMessage.getBytes();
            InetAddress targetAddress = InetAddress.getByName(address);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, targetAddress, PORT);
            socket.send(packet);

            System.out.println("Packet sent (UDP): " + fullMessage + " to " + address);
        } catch (IOException e) {
            System.err.println("Error sending UDP message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendExcludeMessage(String type, String value, String targetPeer) {
        String message = type + "|" + value; // Örn: EXCLUDE_MASK|.exe veya EXCLUDE_FOLDER|temp
        sendMessage(message, targetPeer);
    }


    /**
     * Dosya duyurusu (announce) için kullanabileceğiniz yardımcı metod.
     * "FILE_ANNOUNCEMENT|<hash>|<filename>"
     */
    public void sendFileAnnouncement(String fileHash, String fileName, String totalChunks, String peerAddress) {
        sendMessage("FILE_ANNOUNCEMENT|" + fileHash + "|" + fileName + "|" + totalChunks, peerAddress);
    }


    public void disconnect() {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }
        // Tüm peer'lara DISCONNECT
        List<String> allPeers = generalManager.peers();
        for (String peer : allPeers) {
            sendMessage("DISCONNECT", peer);
        }
        generalManager.peers().clear();
        System.out.println("Disconnected from the network.");
    }

    private String getLocalIPAddress() {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            return localAddress.getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("Error retrieving local IP address: " + e.getMessage());
            return null;
        }
    }
}
