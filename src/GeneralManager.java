import java.util.List;

public class GeneralManager {

    private GUI gui ;

    private PeerConnection peerConnection;

    private String sharedFolderPath ;

    private List<String> foundFiles ;


    public GeneralManager(GUI gui) {
        peerConnection = new PeerConnection();
        this.gui = gui;
    }

    public void connect() {
        new Thread(() -> {
            peerConnection.startFlooding();
            peerConnection.startListening();
        }).start();
    }

    public void disconnect() {
        new Thread(() -> {
            peerConnection.disconnect();
        }).start();
    }

    public void doAction(String input) {
        sharedFolderPath = input;
        gui.getirFrame().koyTitle("xxx");
    }


}
