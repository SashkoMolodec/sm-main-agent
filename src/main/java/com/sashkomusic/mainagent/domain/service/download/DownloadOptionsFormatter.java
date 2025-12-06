package com.sashkomusic.mainagent.domain.service.download;

import com.sashkomusic.mainagent.domain.model.DownloadOption;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DownloadOptionsFormatter {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "flac", "mp3", "wav", "m4a", "aac", "alac", "aiff", "ogg", "wma", "ape"
    );

    public String format(List<DownloadFlowHandler.OptionReport> reports, String aiSummary) {
        if (reports.isEmpty()) {
            return "😔 **на жаль, нич.**";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔎 знайдено %s варіантів:\n\n".formatted(reports.size()));

        int i = 1;
        for (var report : reports) {
            var option = report.option();
            var suitability = report.suitability();

            if (option.files().isEmpty()) {
                sb.append("%s **%s**\n"
                        .formatted(getIndexIcon(i), "`%s`".formatted(option.displayName())));
            } else {
                String format = detectFormat(option);
                int fileCount = option.files().size();

                sb.append("%s **[%s]** • %d ф. • %d MB (%s)\n"
                        .formatted(getIndexIcon(i), format, fileCount, option.totalSize(), suitability.icon));

                option.files().stream()
                        .limit(7)
                        .forEach(f -> sb.append("   📄 `%s`\n".formatted(f.displayName())));

                if (option.files().size() > 7) {
                    sb.append("   ... _та ще %d файлів_\n".formatted(option.files().size() - 7));
                }
                sb.append("\n");
            }
            i++;
        }

        if (aiSummary != null && !aiSummary.isBlank()) {
            sb.append("💡 _%s_\n".formatted(aiSummary));
        }

        return sb.toString();
    }

    private String detectFormat(DownloadOption opt) {
        return opt.files().stream()
                .map(f -> getExtension(f.filename()))
                .filter(ext -> AUDIO_EXTENSIONS.contains(ext.toLowerCase()))
                .collect(Collectors.groupingBy(
                        String::toUpperCase,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("кака");
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    private String getIndexIcon(int index) {
        return switch (index) {
            case 1 -> "1️⃣";
            case 2 -> "2️⃣";
            case 3 -> "3️⃣";
            case 4 -> "4️⃣";
            case 5 -> "5️⃣";
            case 6 -> "6️⃣";
            case 7 -> "7️⃣";
            case 8 -> "8️⃣";
            case 9 -> "9️⃣";
            case 10 -> "🔟";
            default -> index + ".";
        };
    }
}
