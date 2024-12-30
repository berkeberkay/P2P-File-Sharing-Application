import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class SharedFiles {
    private final String sharedDirectory;

    // Constructor: Paylaşılan dosyaların bulunduğu dizini alır
    public SharedFiles(String sharedDirectory) {
        this.sharedDirectory = sharedDirectory;

        // Dizinin var olup olmadığını kontrol edin, yoksa oluşturun
        File directory = new File(sharedDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * Paylaşılan dosyaları listeleme
     *
     * @return Paylaşılan dosyaların listesi
     */
    public List<File> listFiles() {
        File directory = new File(sharedDirectory);
        File[] files = directory.listFiles();

        List<File> fileList = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) { // Sadece dosyaları ekler
                    fileList.add(file);
                }
            }
        }
        return fileList;
    }

    /**
     * Yeni bir dosya ekleme
     *
     * @param filePath Eklenmek istenen dosyanın tam yolu
     * @return Başarılı olup olmadığını belirtir
     */
    public boolean addFile(String filePath) {
        File sourceFile = new File(filePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("Hata: Dosya bulunamadı veya geçersiz.");
            return false;
        }

        File destinationFile = new File(sharedDirectory, sourceFile.getName());
        try {
            Files.copy(sourceFile.toPath(), destinationFile.toPath());
            System.out.println("Dosya başarıyla eklendi: " + destinationFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Dosyayı paylaşım dizininden siler
     *
     * @param fileName Silinmek istenen dosyanın adı
     * @return Başarılı olup olmadığını belirtir
     */
    public boolean removeFile(String fileName) {
        File file = new File(sharedDirectory, fileName);
        if (file.exists() && file.isFile()) {
            return file.delete();
        }
        System.out.println("Hata: Dosya bulunamadı.");
        return false;
    }

    /**
     * Belirli bir dosyanın hash değerini hesaplar
     *
     * @param file Hash değeri hesaplanacak dosya
     * @return Hash değeri (SHA-256)
     */
    public String calculateFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);

            // Hash'i hex formatına dönüştür
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
