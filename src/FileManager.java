import java.io.*;
import java.util.List;

public class FileManager {
    private static final int CHUNK_SIZE = 256 * 1024; // 256KB

    // Dosyayı parçalama metodu
    public static void splitFile(String filePath, String outputDir) throws IOException {
        File inputFile = new File(filePath);
        File outputDirectory = new File(outputDir);

        // Klasör mevcut değilse oluştur
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        try (FileInputStream fis = new FileInputStream(inputFile)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int partCounter = 1;

            while ((bytesRead = fis.read(buffer)) > 0) {
                File chunk = new File(outputDir, inputFile.getName() + ".part" + partCounter++);
                try (FileOutputStream fos = new FileOutputStream(chunk)) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // Parçaları birleştirerek dosyayı oluşturma metodu
    public static void mergeFiles(List<File> chunkFiles, String outputFilePath) throws IOException {
        File outputFile = new File(outputFilePath);

        // Hedef klasörü kontrol et ve oluştur
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (File chunk : chunkFiles) {
                try (FileInputStream fis = new FileInputStream(chunk)) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
        System.out.println("File merged successfully: " + outputFilePath);
    }
}
