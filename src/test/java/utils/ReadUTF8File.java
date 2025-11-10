package utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ReadUTF8File {
    public static void main(String[] args) {
        Path filePath = Paths.get("D:\\SKARUNASOFT\\ACE\\kpi-missing-files\\available\\8633226746677040030\\YEJSO_110535_tud_ACE.docx_single_actor_KPI.txt");

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
