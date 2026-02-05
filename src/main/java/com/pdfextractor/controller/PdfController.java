package com.pdfextractor.controller;

import com.pdfextractor.service.PdfExtractorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PdfController {

    private final PdfExtractorService pdfExtractorService;
    private final Path uploadsDir;

    public PdfController(PdfExtractorService pdfExtractorService, 
                        @Value("${app.uploads.dir:/app/uploads}") String uploadsDirPath) {
        this.pdfExtractorService = pdfExtractorService;
        this.uploadsDir = Paths.get(uploadsDirPath);
        
        // Ensure uploads directory exists
        try {
            Files.createDirectories(uploadsDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create uploads directory", e);
        }
    }

    /**
     * Upload and process a PDF file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadPdf(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        if (file.isEmpty()) {
            response.put("success", false);
            response.put("error", "Please select a PDF file to upload");
            return ResponseEntity.badRequest().body(response);
        }
        
        if (!file.getContentType().equals("application/pdf")) {
            response.put("success", false);
            response.put("error", "Only PDF files are allowed");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            // Generate unique ID for this upload
            String uniqueId = UUID.randomUUID().toString();
            
            // Save uploaded file temporarily
            File tempFile = Files.createTempFile("pdf_upload_", ".pdf").toFile();
            file.transferTo(tempFile);
            
            // Process the PDF
            PdfExtractorService.ProcessingResult result = 
                pdfExtractorService.processPdf(tempFile, uploadsDir, uniqueId);
            
            // Clean up temp file
            tempFile.delete();
            
            // Build response
            response.put("success", true);
            response.put("id", result.getId());
            response.put("originalFileName", file.getOriginalFilename());
            response.put("extractedText", result.getExtractedText());
            response.put("imageCount", result.getImageNames().size());
            response.put("images", result.getImageNames());
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to process PDF: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get list of all processed uploads
     */
    @GetMapping("/uploads")
    public ResponseEntity<List<Map<String, Object>>> listUploads() {
        List<Map<String, Object>> uploads = new ArrayList<>();
        
        try {
            if (!Files.exists(uploadsDir)) {
                return ResponseEntity.ok(uploads);
            }
            
            try (Stream<Path> paths = Files.list(uploadsDir)) {
                List<Path> directories = paths.filter(Files::isDirectory).collect(Collectors.toList());
                
                for (Path dir : directories) {
                    Map<String, Object> upload = new HashMap<>();
                    upload.put("id", dir.getFileName().toString());
                    
                    // Check for text file
                    Path textFile = dir.resolve("extracted_text.txt");
                    upload.put("hasText", Files.exists(textFile));
                    
                    // Check for images
                    Path imagesDir = dir.resolve("images");
                    if (Files.exists(imagesDir)) {
                        try (Stream<Path> images = Files.list(imagesDir)) {
                            List<String> imageNames = images
                                .filter(p -> !Files.isDirectory(p))
                                .map(p -> p.getFileName().toString())
                                .collect(Collectors.toList());
                            upload.put("images", imageNames);
                            upload.put("imageCount", imageNames.size());
                        }
                    } else {
                        upload.put("images", Collections.emptyList());
                        upload.put("imageCount", 0);
                    }
                    
                    // Check for original PDF
                    Path pdfFile = dir.resolve("original.pdf");
                    upload.put("hasPdf", Files.exists(pdfFile));
                    
                    // Get creation time
                    if (Files.exists(dir)) {
                        upload.put("createdAt", Files.getLastModifiedTime(dir).toMillis());
                    }
                    
                    uploads.add(upload);
                }
            }
            
            // Sort by creation time (newest first)
            uploads.sort((a, b) -> Long.compare(
                (Long) b.getOrDefault("createdAt", 0L),
                (Long) a.getOrDefault("createdAt", 0L)
            ));
            
            return ResponseEntity.ok(uploads);
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(uploads);
        }
    }

    /**
     * Get extracted text for a specific upload
     */
    @GetMapping("/uploads/{id}/text")
    public ResponseEntity<Map<String, Object>> getText(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path textFile = uploadsDir.resolve(id).resolve("extracted_text.txt");
            
            if (!Files.exists(textFile)) {
                response.put("success", false);
                response.put("error", "Text file not found");
                return ResponseEntity.notFound().build();
            }
            
            String text = Files.readString(textFile);
            response.put("success", true);
            response.put("text", text);
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to read text: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get a specific image from an upload
     */
    @GetMapping("/uploads/{id}/images/{imageName}")
    public ResponseEntity<Resource> getImage(@PathVariable String id, @PathVariable String imageName) {
        try {
            Path imagePath = uploadsDir.resolve(id).resolve("images").resolve(imageName);
            
            if (!Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new UrlResource(imagePath.toUri());
            String contentType = Files.probeContentType(imagePath);
            
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imageName + "\"")
                .body(resource);
                
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get the original PDF file
     */
    @GetMapping("/uploads/{id}/pdf")
    public ResponseEntity<Resource> getPdf(@PathVariable String id) {
        try {
            Path pdfPath = uploadsDir.resolve(id).resolve("original.pdf");
            
            if (!Files.exists(pdfPath)) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new UrlResource(pdfPath.toUri());
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"original.pdf\"")
                .body(resource);
                
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Delete an upload
     */
    @DeleteMapping("/uploads/{id}")
    public ResponseEntity<Map<String, Object>> deleteUpload(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Path uploadDir = uploadsDir.resolve(id);
            
            if (!Files.exists(uploadDir)) {
                response.put("success", false);
                response.put("error", "Upload not found");
                return ResponseEntity.notFound().build();
            }
            
            // Delete directory recursively
            try (Stream<Path> walk = Files.walk(uploadDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            
            response.put("success", true);
            response.put("message", "Upload deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            response.put("success", false);
            response.put("error", "Failed to delete upload: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
