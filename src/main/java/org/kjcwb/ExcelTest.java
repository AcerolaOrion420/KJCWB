package org.kjcwb;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.kjcwb.Packages.Services.MongoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExcelTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelTest.class);

    public static void generateExcelReport(String id, String outputPath) {
        Workbook workbook = null;
        FileOutputStream outputStream = null;

        try {
            // Initialize MongoDB connection
            MongoService.initialize("Report");

            // Query the document by ID
            Document result = MongoService.find("_id", id);

            if (result == null) {
                LOGGER.error("No document found with ID: {}", id);
                return;
            }

            // Create a new workbook and sheet
            workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Report");

            // Create header row
            Row headerRow = sheet.createRow(0);
            List<String> headers = new ArrayList<>(result.keySet());
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
            }

            // Create data row
            Row dataRow = sheet.createRow(1);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = dataRow.createCell(i);
                Object value = result.get(headers.get(i));
                if (value != null) {
                    cell.setCellValue(value.toString());
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Ensure the directory exists
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (!parentDir.exists()) {
                LOGGER.info("Creating directory: {}", parentDir.getAbsolutePath());
                if (!parentDir.mkdirs()) {
                    throw new IOException("Failed to create directory: " + parentDir.getAbsolutePath());
                }
            }

            // Write the workbook to a file
            outputStream = new FileOutputStream(outputFile);
            workbook.write(outputStream);

            LOGGER.info("Excel report generated successfully: {}", outputPath);

        } catch (IOException e) {
            LOGGER.error("IO Error generating Excel report: {}", e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Error generating Excel report: {}", e.getMessage(), e);
        } finally {
            // Close resources
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing output stream: {}", e.getMessage(), e);
                }
            }
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing workbook: {}", e.getMessage(), e);
                }
            }
            MongoService.close();
        }
    }

    public static void main(String[] args) {
        generateExcelReport("BS2007202422BCAB36161216","/home/omy/IdeaProjects/KJCWB/src/main/java/org/kjcwb/Reports");

    }
}