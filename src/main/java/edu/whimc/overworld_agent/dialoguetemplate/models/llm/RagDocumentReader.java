package edu.whimc.overworld_agent.dialoguetemplate.models.llm;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Extracts plain text from RAG source files ({@code .txt}, {@code .md}, {@code .doc}, {@code .docx}).
 */
public final class RagDocumentReader {

    private RagDocumentReader() {}

    public static String readText(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IOException("No file extension: " + name);
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "txt", "md" -> Files.readString(file, StandardCharsets.UTF_8);
            case "docx" -> readDocx(file);
            case "doc" -> readDoc(file);
            default -> throw new IOException("Unsupported extension: " + ext);
        };
    }

    private static String readDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
                XWPFDocument document = new XWPFDocument(in);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String readDoc(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
                HWPFDocument document = new HWPFDocument(in);
                WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
}
