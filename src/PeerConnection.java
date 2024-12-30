import java.io.*;
import java.net.*;
import java.util.*;

public class PeerConnection {
    private static final int PORT = 9876; // UDP iletişim portu
    private static final int MAX_PACKETS = 1; // Gönderilen mesaj sayısı sınırı

    private List<String> peerList; // Keşfedilen peer'ların IP adresleri
    private DatagramSocket socket; // UDP iletişimi için tek bir soket

    // Mesaj türleri için enum
    private enum MessageType {
        DISCOVERY_REQUEST,
        RESPONSE,
        DISCONNECT
    }

    public PeerConnection() {
        peerList = Collections.synchronizedList(new ArrayList<>());
        try {
            socket = new DatagramSocket(PORT); // DatagramSocket başlatılır
            socket.setBroadcast(true);        // Broadcast ayarı yapılır
            System.out.println("DatagramSocket successfully initialized on port " + PORT);
        } catch (SocketException e) {
            System.err.println("Error initializing DatagramSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * UDP Flood gönderimi başlatır (Discovery Request).
     */
    public void startFlooding() {

        System.out.println("Starting peer discovery... Local IP : " + getLocalIPAddress() );
        sendMessage(MessageType.DISCOVERY_REQUEST, "255.255.255.255");
        System.out.println("Discovery request sent");

    }


    /**
     * Gelen bağlantıları dinler ve mesajları işler.
     */
    public void startListening() {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }

        new Thread(() -> {
            if (socket == null || socket.isClosed()) {
                System.err.println("DatagramSocket is not initialized or already closed.");
                return;
            }

            System.out.println("Listening for incoming connections...");
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


    /**
     * Gelen mesajı işler.
     *
     * @param message       Gelen mesaj
     * @param senderAddress Mesajı gönderen peer'ın adresi
     */
    private void handleIncomingMessage(String message, String senderAddress) {

        String localIPAddress = getLocalIPAddress(); // Yerel IP adresini alın

        // Kendine gelen mesajları işlememek için kontrol
        if (localIPAddress != null && localIPAddress.equals(senderAddress)) {
            return; // Kendi IP'sinden gelen mesajı yok say
        }

        System.out.println("Message received: " + message + " from " + senderAddress);

        if (message.equals("DISCOVERY_REQUEST")) {
            // Gönderen peer'ı peerList'e ekle
            if (!peerList.contains(senderAddress)) {
                peerList.add(senderAddress);
                System.out.println("New peer discovered: " + senderAddress);
            }

        } else if (message.equals("DISCONNECT")) {
            // Disconnect mesajı alındığında peer'ı listeden çıkar
            peerList.remove(senderAddress);
            System.out.println("Peer disconnected: " + senderAddress);
        } else if (message.equals("FILE_ANNOUNCE")) {
            // Format :  FILE_ANNOUNCE|fileName|
        }

        System.out.println(peerList);

    }

    /**
     * Genel mesaj gönderme metodu.
     *
     * @param type    Mesaj türü
     * @param address Hedef adres
     */
    private void sendMessage(MessageType type, String address) {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }

        try {
            String message = switch (type) {
                case DISCOVERY_REQUEST -> "DISCOVERY_REQUEST";
                case RESPONSE -> "RESPONSE";
                case DISCONNECT -> "DISCONNECT";
            };

            byte[] buffer = message.getBytes();
            InetAddress targetAddress = InetAddress.getByName(address);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, targetAddress, PORT);

            // Paket gönder
            for (int i = 0; i < MAX_PACKETS; i++) {
                socket.send(packet);
                System.out.println("Packet sent: " + message + " to " + address);
            }
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Disconnect olur ve diğer peer'lara bilgi gönderir.
     */
    public void disconnect() {
        if (socket == null || socket.isClosed()) {
            System.err.println("DatagramSocket is not initialized or already closed.");
            return;
        }

        System.out.println("Disconnecting from the network...");
        // Tüm peer'lara DISCONNECT mesajı gönder
        for (String peer : peerList) {
            sendMessage(MessageType.DISCONNECT, peer);
        }

        peerList.clear();
        System.out.println("Disconnected from the network.");
    }

    /**
     * Peer listesi döner.
     *
     * @return Peer IP listesi
     */
    public List<String> getPeerList() {
        return peerList;
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
