# PDF Extractor

A containerized Java Spring Boot application that uses Apache PDFBox to extract text and images from PDF files. Features a modern Tailwind CSS frontend for easy file uploads and viewing extracted content.

## Features

- **Text Extraction**: Extract all text content from PDF files
- **Image Extraction**: Extract embedded images and save them to the file system
- **File Management**: View, download, and delete uploaded PDFs
- **Modern UI**: Clean, responsive interface built with Tailwind CSS
- **Docker Ready**: Fully containerized with Docker Compose

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│    Frontend     │────▶│     Backend     │
│  (Nginx:3000)   │     │ (Spring:8080)   │
└─────────────────┘     └─────────────────┘
                              │
                              ▼
                        ┌─────────────────┐
                        │  /app/uploads   │
                        │  (Persistent)   │
                        └─────────────────┘
```

## Quick Start

### Prerequisites

- Docker
- Docker Compose

### Running the Application

1. **Clone or download the project**

2. **Build and start the containers:**
   ```bash
   docker-compose up --build
   ```

3. **Access the application:**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:3000/api

4. **Stop the application:**
   ```bash
   docker-compose down
   ```

## API Endpoints

### Upload PDF
```
POST /api/upload
Content-Type: multipart/form-data

file: <PDF file>
```

**Response:**
```json
{
  "success": true,
  "id": "uuid-string",
  "originalFileName": "document.pdf",
  "extractedText": "...",
  "imageCount": 5,
  "images": ["page1_image1.png", ...]
}
```

### List All Uploads
```
GET /api/uploads
```

**Response:**
```json
[
  {
    "id": "uuid-string",
    "hasText": true,
    "hasPdf": true,
    "imageCount": 5,
    "images": ["page1_image1.png", ...]
  }
]
```

### Get Extracted Text
```
GET /api/uploads/{id}/text
```

### Get Image
```
GET /api/uploads/{id}/images/{imageName}
```

### Get Original PDF
```
GET /api/uploads/{id}/pdf
```

### Delete Upload
```
DELETE /api/uploads/{id}
```

## Project Structure

```
pdfbox-app/
├── docker-compose.yml          # Docker Compose configuration
├── Dockerfile                  # Backend Dockerfile
├── pom.xml                     # Maven dependencies
├── src/
│   └── main/
│       ├── java/
│       │   └── com/pdfextractor/
│       │       ├── PdfExtractorApplication.java
│       │       ├── controller/
│       │       │   └── PdfController.java
│       │       └── service/
│       │           └── PdfExtractorService.java
│       └── resources/
│           └── application.properties
└── frontend/
    ├── Dockerfile              # Frontend Dockerfile
    ├── nginx.conf              # Nginx configuration
    └── index.html              # Tailwind CSS UI
```

## File Storage Structure

Uploaded files are stored in the following structure:

```
/app/uploads/
└── {uuid}/
    ├── original.pdf            # Original uploaded PDF
    ├── extracted_text.txt      # Extracted text content
    └── images/
        ├── page1_image1.png    # Extracted images
        ├── page1_image2.jpg
        └── ...
```

## Development

### Building Manually

**Backend:**
```bash
mvn clean package -DskipTests
java -jar target/pdfbox-app-1.0.0.jar
```

**Frontend:**
Serve the `frontend/index.html` file with any web server and configure proxy to backend.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_UPLOADS_DIR` | `/app/uploads` | Directory for uploaded files |
| `JAVA_OPTS` | `-Xmx512m` | JVM options |

### Application Properties

Located in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
app.uploads.dir=/app/uploads
```

## Technologies Used

- **Backend:**
  - Java 17
  - Spring Boot 3.2
  - Apache PDFBox 3.0.1
  - Maven

- **Frontend:**
  - HTML5
  - Tailwind CSS (via CDN)
  - Vanilla JavaScript

- **Infrastructure:**
  - Docker
  - Docker Compose
  - Nginx (reverse proxy)

## License

MIT License
