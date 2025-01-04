import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class GUI {
    private JFrame frame;
    private GeneralManager generalManager;

    DefaultListModel<String> foundFilesListModel ;// Liste modeli
    JList<String> foundFilesList ;

    DefaultListModel<String> downloadingListModel ;// Liste modeli
    JList<String> downloadingList ;


    public GUI() {
        initialize();
        generalManager = new GeneralManager(this);
    }

    private void initialize() {
        // Ana pencere oluşturma
        frame = new JFrame("P2P File Sharing Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 600);
        frame.setLayout(new BorderLayout());
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Color.BLUE); // JFrame'in arka planını mavi yap

        // Ana panel
        JPanel mainPanel = new JPanel();
        System.out.println("Welcome to the P2P File Sharing Application!");
        mainPanel.setVisible(true);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(236, 233, 216)); // Ana panelin rengi krem

        // Menü Barı (Üst)
        JMenuBar menuBar = new JMenuBar();

        // Files Menüsü
        JMenu filesMenu = new JMenu("Files");
        JMenuItem connectItem = new JMenuItem("Connect");
        JMenuItem disconnectItem = new JMenuItem("Disconnect");
        JMenuItem exitItem = new JMenuItem("Exit");

        connectItem.addActionListener(e -> {
            try {
                generalManager.connect();
                JOptionPane.showMessageDialog(frame, "Connected to the overlay network.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to connect: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        // "Disconnect" butonuna PeerConnection işlevi ekleme
        disconnectItem.addActionListener(e -> {
            try {
                generalManager.disconnect();
                JOptionPane.showMessageDialog(frame, "Disconnected from the overlay network.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Failed to disconnect: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });


        exitItem.addActionListener(e -> System.exit(0));

        filesMenu.add(connectItem);
        filesMenu.add(disconnectItem);
        filesMenu.addSeparator();
        filesMenu.add(exitItem);

        // Help Menüsü
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Developer: Berke Berkay Tekçe\nVersion: 1.0"));
        helpMenu.add(aboutItem);

        // Menüleri Menü Barına ekleme
        menuBar.add(filesMenu);
        menuBar.add(helpMenu);
        frame.setJMenuBar(menuBar);

        // Krem rengi (236, 233, 216)
        Color creamColor = new Color(236, 233, 216);

        // Root of the P2P shared folder container
        JPanel rootPanel = new JPanel(new BorderLayout(5, 5));
        rootPanel.setBorder(BorderFactory.createTitledBorder("Root of the P2P shared folder"));
        rootPanel.setBackground(creamColor);
        JTextField rootTextField = new JTextField();
        rootTextField.setBackground(Color.WHITE); // Text field varsayılan beyaz
        JButton rootButton = new JButton("Set");
        rootButton.setBackground(creamColor);
        rootPanel.add(rootTextField, BorderLayout.CENTER);
        rootPanel.add(rootButton, BorderLayout.EAST);

        // Destination folder container
        JPanel destinationPanel = new JPanel(new BorderLayout(5, 5));
        destinationPanel.setBorder(BorderFactory.createTitledBorder("Destination folder"));
        destinationPanel.setBackground(creamColor);
        JTextField destinationTextField = new JTextField();
        destinationTextField.setBackground(Color.WHITE); // Text field varsayılan beyaz
        JButton destinationButton = new JButton("Set");
        destinationButton.setBackground(creamColor);
        destinationPanel.add(destinationTextField, BorderLayout.CENTER);
        destinationPanel.add(destinationButton, BorderLayout.EAST);

        // Settings container
        JPanel settingsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Settings"));
        settingsPanel.setBackground(creamColor);
        settingsPanel.setPreferredSize(new Dimension(500, 150)); // Settings küçültüldü

        // Folder exclusion (left side of settings)
        JPanel folderExclusionPanel = new JPanel(new BorderLayout());
        folderExclusionPanel.setBorder(BorderFactory.createTitledBorder("Folder exclusion"));
        folderExclusionPanel.setBackground(creamColor);
        JCheckBox checkNewFiles = new JCheckBox("Check new files only in the root");
        checkNewFiles.setBackground(creamColor);

        // "Exclude files under these folders" alt container
        JPanel excludeFoldersPanel = new JPanel(new BorderLayout());
        excludeFoldersPanel.setBorder(BorderFactory.createTitledBorder("Exclude files under these folders"));
        excludeFoldersPanel.setBackground(creamColor);
        DefaultListModel<String> excludeFolderListModel = new DefaultListModel<>();
        JList<String> excludeFolderList = new JList<>(excludeFolderListModel);
        JScrollPane excludeFolderScrollPane = new JScrollPane(excludeFolderList);
        excludeFoldersPanel.add(excludeFolderScrollPane, BorderLayout.CENTER);

        JPanel folderButtonsPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        folderButtonsPanel.setBackground(creamColor);
        JButton addFolderButton = new JButton("Add");
        addFolderButton.setBackground(creamColor);
        JButton delFolderButton = new JButton("Del");
        delFolderButton.setBackground(creamColor);
        folderButtonsPanel.add(addFolderButton);
        folderButtonsPanel.add(delFolderButton);
        excludeFoldersPanel.add(folderButtonsPanel, BorderLayout.EAST);

        folderExclusionPanel.add(checkNewFiles, BorderLayout.NORTH);
        folderExclusionPanel.add(excludeFoldersPanel, BorderLayout.CENTER);

        // Exclude files matching these masks (right side of settings)
        JPanel excludeFilesPanel = new JPanel(new BorderLayout());
        excludeFilesPanel.setBorder(BorderFactory.createTitledBorder("Exclude files matching these masks"));
        excludeFilesPanel.setBackground(creamColor);
        DefaultListModel<String> excludeListModel = new DefaultListModel<>();
        JList<String> excludeList = new JList<>(excludeListModel);
        JScrollPane excludeFilesScrollPane = new JScrollPane(excludeList);
        excludeFilesPanel.add(excludeFilesScrollPane, BorderLayout.CENTER);

        JPanel excludeButtonsPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        excludeButtonsPanel.setBackground(creamColor);
        JButton addExcludeButton = new JButton("Add");
        addExcludeButton.setBackground(creamColor);
        JButton delExcludeButton = new JButton("Del");
        delExcludeButton.setBackground(creamColor);
        excludeButtonsPanel.add(addExcludeButton);
        excludeButtonsPanel.add(delExcludeButton);
        excludeFilesPanel.add(excludeButtonsPanel, BorderLayout.SOUTH);

        settingsPanel.add(folderExclusionPanel);
        settingsPanel.add(excludeFilesPanel);

        // Downloading files container (Genişletildi)
        // Downloading files container (Listeye Çevrildi)
        JPanel downloadingPanel = new JPanel(new BorderLayout());
        downloadingPanel.setBorder(BorderFactory.createTitledBorder("Downloading files"));
        downloadingPanel.setBackground(creamColor);

// Liste modeli ve JList oluşturma
        downloadingListModel = new DefaultListModel<>();
        downloadingList = new JList<>(downloadingListModel);
        downloadingList.setBackground(Color.WHITE);

// Listeye kaydırma çubuğu ekleme
        downloadingPanel.add(new JScrollPane(downloadingList), BorderLayout.CENTER);
        downloadingPanel.setPreferredSize(new Dimension(500, 150)); // Orijinal boyut korunuyor

// Found files container (Listeye Çevrildi)
        JPanel foundFilesPanel = new JPanel(new BorderLayout());
        foundFilesPanel.setBorder(BorderFactory.createTitledBorder("Found files"));
        foundFilesPanel.setBackground(new Color(236, 233, 216));

        foundFilesListModel = new DefaultListModel<>(); // Liste modeli
        foundFilesList = new JList<>(foundFilesListModel); // JList
        foundFilesList.setBackground(Color.WHITE);

        // Listeye kaydırma çubuğu ekleme
        JScrollPane foundFilesScrollPane = new JScrollPane(foundFilesList);
        foundFilesPanel.add(foundFilesScrollPane, BorderLayout.CENTER);
        foundFilesPanel.setPreferredSize(new Dimension(500, 150)); // Boyut ayarı



        // Search area (bottom panel)
        JPanel searchPanel;
        searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBackground(creamColor);
        JButton searchButton = new JButton("Search");
        searchButton.setBackground(creamColor);
        JTextField searchField = new JTextField();
        searchField.setBackground(Color.WHITE); // Text field varsayılan beyaz
        searchPanel.add(searchButton, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Alt Menü Barı (Boş)
        JMenuBar bottomMenuBar = new JMenuBar();
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(bottomMenuBar, BorderLayout.SOUTH);
        bottomPanel.setBackground(creamColor);

        // Panelleri ana panele ekleme
        mainPanel.add(rootPanel);
        mainPanel.add(destinationPanel);
        mainPanel.add(settingsPanel);
        mainPanel.add(downloadingPanel); // Genişletildi
        mainPanel.add(foundFilesPanel); // Genişletildi
        mainPanel.add(searchPanel);

        // Ana paneli ve alt menü barını frame'e ekleme
        frame.add(mainPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Event Handlers

        rootTextField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Sadece dizinler seçilebilir
                int result = fileChooser.showOpenDialog(frame);

                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedDirectory = fileChooser.getSelectedFile();
                    rootTextField.setText(selectedDirectory.getAbsolutePath());
                    generalManager.updateFoundFilesInGUI();
                }
            }
        });

        rootButton.addActionListener(e -> {
            if (new File(rootTextField.getText()).isDirectory()) {
                if (!rootTextField.getText().isEmpty()) {
                    generalManager.sharedFolderPath(rootTextField.getText());
                }
            }
        });

        destinationTextField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Sadece dizinler seçilebilir
                int result = fileChooser.showOpenDialog(frame);

                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedDirectory = fileChooser.getSelectedFile();
                    destinationTextField.setText(selectedDirectory.getAbsolutePath());
                }
            }
        });

        destinationButton.addActionListener(e -> {
            String destPath = destinationTextField.getText();
            File f = new File(destPath);
            if (f.isDirectory()) {
                generalManager.setDestinationFolderPath(destPath);
            }
        });


        foundFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // çift tıklama
                    int index = foundFilesList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        String displayText = foundFilesListModel.getElementAt(index);
                        // Örn. "deneme.txt (hash=0827a3bc8f5f...)"
                        String extractedHash = parseHashFromDisplay(displayText);
                        System.out.println("Downloading file (hash): " + extractedHash);

                        // GeneralManager'da parametre olarak hash gönderelim
                        generalManager.requestFile(extractedHash);
                    }
                }
            }
        private String parseHashFromDisplay(String displayText) {
            // displayText = "deneme.txt (hash=0827a3bc8f5f4dddfb...)"
            int startIndex = displayText.indexOf("hash=") + 5; // 5 = length("hash=")
            if (startIndex < 5) {
                // "hash=" bulunamadı
                return null;
            }
            // Parantez kapandığı yeri bulalım
            int endIndex = displayText.indexOf(")", startIndex);
            if (endIndex < 0) {
                return null;
            }
            return displayText.substring(startIndex, endIndex).trim();
        }
        });


        searchButton.addActionListener(e -> {
            String searchText = searchField.getText().trim().toLowerCase(); // Arama metni alınır ve küçük harfe dönüştürülür

            if (searchText.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter text to search.", "Search Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            DefaultListModel<String> model = (DefaultListModel<String>) foundFilesList.getModel();
            DefaultListModel<String> filteredModel = new DefaultListModel<>();

            // Arama metni ile eşleşen dosyaları filtrele
            for (int i = 0; i < model.size(); i++) {
                String fileName = model.get(i).toLowerCase();
                if (fileName.contains(searchText)) {
                    filteredModel.addElement(model.get(i));
                }
            }

            // Eğer sonuç yoksa uyarı göster
            if (filteredModel.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "No files found matching: " + searchText, "Search Results", JOptionPane.INFORMATION_MESSAGE);
            } else {
                foundFilesList.setModel(filteredModel); // Filtrelenmiş dosyaları listeye koy
            }
        });



    }

    public void show() {
        frame.setVisible(true);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void setFrame(JFrame frame) {
        this.frame = frame;
    }

    public GeneralManager getGeneralManager() {
        return generalManager;
    }

    public void setGeneralManager(GeneralManager generalManager) {
        this.generalManager = generalManager;
    }

    public void addFileToFoundFilesList(String fileName) {
        SwingUtilities.invokeLater(() -> {
            if (!foundFilesListModel.contains(fileName)) {
                foundFilesListModel.addElement(fileName); // Yeni dosyayı ekle
                System.out.println("File added to GUI found files: " + fileName);
            }
        });
    }

    public void clearFoundFilesList() {
        SwingUtilities.invokeLater(() -> {
            foundFilesListModel.clear(); // Listeyi temizle
        });
    }
    // GUI.java


    public void updateDownloadingFile(FileManager.DownloadStatus ds) {
        SwingUtilities.invokeLater(() -> {
            // Find if ds is already in the list by name
            for (int i = 0; i < downloadingListModel.size(); i++) {
                String element = downloadingListModel.getElementAt(i);
                if (element.startsWith(ds.getFileName())) {
                    // Update the item with the new percentage
                    downloadingListModel.setElementAt(ds.toString(), i);
                    return;
                }
            }
            // If not found, add it (this covers the initial 0% case)
            downloadingListModel.addElement(ds.toString());
        });
    }



}