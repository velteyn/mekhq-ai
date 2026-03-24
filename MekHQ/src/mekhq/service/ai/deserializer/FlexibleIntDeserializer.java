package mekhq.service.ai.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * A flexible deserializer for integer fields that can handle strings like "Medium", "5-7", or "High" 
 * by mapping them to reasonable integer values.
 */
public class FlexibleIntDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            // Try standard integer parsing first
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            // Map common string descriptions to values
            String lower = value.toLowerCase();
            if (lower.contains("very low") || lower.contains("easy")) return 1;
            if (lower.contains("low")) return 3;
            if (lower.contains("medium") || lower.contains("average")) return 5;
            if (lower.contains("high") || lower.contains("hard")) return 7;
            if (lower.contains("very high") || lower.contains("extreme")) return 9;
            
            // Handle ranges like "5-7" by taking the average
            if (value.contains("-")) {
                String[] parts = value.split("-");
                try {
                    int start = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                    int end = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));
                    return (start + end) / 2;
                } catch (Exception ex) {
                    // Fallback
                }
            }
            
            return 5; // Default fallback for unknown strings
        }
    }
}
