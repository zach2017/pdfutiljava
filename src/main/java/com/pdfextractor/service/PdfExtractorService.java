package com.pdfextractor.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfExtractorService {

    /**
     * Extracts all text content from a PDF file
     * @param pdfFile the PDF file to extract text from
     * @return the extracted text content
     */
    public String extractText(File pdfFile) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            return textStripper.getText(document);
        }
    }

    /**
     * Extracts all images from a PDF file and saves them to the specified directory
     * @param pdfFile the PDF file to extract images from
     * @param outputDir the directory to save extracted images
     * @return list of extracted image file names
     */
    public List<String> extractImages(File pdfFile, Path outputDir) throws IOException {
        List<String> extractedImages = new ArrayList<>();
        
        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageNumber = 0;
            int imageCount = 0;
            
            for (PDPage page : document.getPages()) {
                pageNumber++;
                PDResources resources = page.getResources();
                
                if (resources == null) {
                    continue;
                }
                
                for (COSName xObjectName : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(xObjectName);
                    
                    if (xObject instanceof PDImageXObject) {
                        PDImageXObject image = (PDImageXObject) xObject;
                        imageCount++;
                        
                        BufferedImage bufferedImage = image.getImage();
                        String suffix = image.getSuffix();
                        
                        // Default to PNG if suffix is unknown
                        if (suffix == null || suffix.isEmpty()) {
                            suffix = "png";
                        }
                        
                        String imageName = String.format("page%d_image%d.%s", pageNumber, imageCount, suffix);
                        File outputFile = outputDir.resolve(imageName).toFile();
                        
                        // Write image to file
                        ImageIO.write(bufferedImage, suffix, outputFile);
                        extractedImages.add(imageName);
                    }
                }
            }
        }
        
        return extractedImages;
    }

    /**
     * Processes a PDF file - extracts text and images
     * @param pdfFile the PDF file to process
     * @param baseOutputDir the base directory for outputs
     * @param uniqueId unique identifier for this upload
     * @return ProcessingResult containing text and image paths
     */
    public ProcessingResult processPdf(File pdfFile, Path baseOutputDir, String uniqueId) throws IOException {
        Path uploadDir = baseOutputDir.resolve(uniqueId);
        Path imagesDir = uploadDir.resolve("images");
        
        Files.createDirectories(uploadDir);
        Files.createDirectories(imagesDir);
        
        // Extract text
        String extractedText = extractText(pdfFile);
        
        // Save text to file
        Path textFilePath = uploadDir.resolve("extracted_text.txt");
        Files.writeString(textFilePath, extractedText);
        
        // Copy original PDF
        Path pdfCopyPath = uploadDir.resolve("original.pdf");
        Files.copy(pdfFile.toPath(), pdfCopyPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        // Extract images
        List<String> imageNames = extractImages(pdfFile, imagesDir);
        
        return new ProcessingResult(uniqueId, extractedText, imageNames, textFilePath.toString(), pdfCopyPath.toString());
    }

    /**
     * Result class for PDF processing
     */
    public static class ProcessingResult {
        private final String id;
        private final String extractedText;
        private final List<String> imageNames;
        private final String textFilePath;
        private final String pdfFilePath;

        public ProcessingResult(String id, String extractedText, List<String> imageNames, 
                               String textFilePath, String pdfFilePath) {
            this.id = id;
            this.extractedText = extractedText;
            this.imageNames = imageNames;
            this.textFilePath = textFilePath;
            this.pdfFilePath = pdfFilePath;
        }

        public String getId() { return id; }
        public String getExtractedText() { return extractedText; }
        public List<String> getImageNames() { return imageNames; }
        public String getTextFilePath() { return textFilePath; }
        public String getPdfFilePath() { return pdfFilePath; }
    }
}
