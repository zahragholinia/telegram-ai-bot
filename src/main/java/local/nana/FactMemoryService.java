
package local.nana;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactMemoryService {

    private final FactRepository factRepository;

    public FactMemoryService(FactRepository factRepository) {
        this.factRepository = factRepository;
    }

    @Transactional
    public long saveDirectFact(
            long personId,
            String factKey,
            String factValueJson,
            String humanReadableContent,
            long sourceId) {

        validate(personId, sourceId, factKey, factValueJson,
                humanReadableContent);

        return saveOrVersion(
                personId,
                factKey,
                factValueJson,
                humanReadableContent,
                BigDecimal.ONE,
                "direct",
                sourceId
        );
    }

    @Transactional
    public long saveExtractedFact(
            long personId,
            MemoryExtractionService.ExtractedFact fact,
            long sourceId) {

        if (fact == null) {
            throw new IllegalArgumentException("fact is required");
        }

        validate(personId, sourceId, fact.key(), fact.valueJson(),
                fact.content());

        if (fact.confidence() < 0 || fact.confidence() > 1
                || !Double.isFinite(fact.confidence())) {
            throw new IllegalArgumentException("Invalid confidence");
        }

        if (!List.of("direct", "reported", "inferred")
                .contains(fact.evidenceType())) {
            throw new IllegalArgumentException("Invalid evidence type");
        }

        return saveOrVersion(
                personId,
                fact.key(),
                fact.valueJson(),
                fact.content(),
                BigDecimal.valueOf(fact.confidence()),
                fact.evidenceType(),
                sourceId
        );
    }

    private long saveOrVersion(
            long personId,
            String rawKey,
            String valueJson,
            String content,
            BigDecimal confidence,
            String evidenceType,
            long sourceId) {

        String key = normalizeKey(rawKey);

        // Serializes memory updates for this person within the transaction.
        factRepository.lockPersonFacts(personId);

        List<FactRepository.ActiveFact> activeFacts =
                factRepository.findActiveFacts(personId);

        FactRepository.ActiveFact sameKeyFact = null;

        for (FactRepository.ActiveFact active : activeFacts) {
            if (!normalizeKey(active.factKey()).equals(key)) {
                continue;
            }

            if (sameJsonValue(active.factValueJson(), valueJson)) {
                // An identical fact already exists. Do not create a duplicate.
                return active.id();
            }

            // Keep the latest matching active fact as the predecessor.
            sameKeyFact = active;
        }

        if (sameKeyFact != null) {
            factRepository.expireFact(sameKeyFact.id());
        }

        return factRepository.saveFactVersion(
                personId,
                key,
                valueJson,
                content,
                confidence,
                evidenceType,
                "source_group_only",
                sourceId,
                sameKeyFact == null ? null : sameKeyFact.id()
        );
    }

    private String normalizeKey(String rawKey) {
        String key = rawKey == null
                ? ""
                : rawKey.strip().toLowerCase();

        return switch (key) {
            case "programming_experience_java",
                 "java_experience",
                 "java_experience_years" -> "java_experience_years";
            default -> key;
        };
    }

    private boolean sameJsonValue(String existingJson, String newJson) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readTree(existingJson)
                    .equals(mapper.readTree(newJson));
        } catch (Exception e) {
            return existingJson.equals(newJson);
        }
    }

    private void validate(
            long personId,
            long sourceId,
            String key,
            String valueJson,
            String content) {

        if (personId <= 0 || sourceId <= 0) {
            throw new IllegalArgumentException(
                    "Person ID and source ID must be positive");
        }

        if (key == null || key.isBlank() || key.length() > 150) {
            throw new IllegalArgumentException("Invalid fact key");
        }

        if (valueJson == null || valueJson.isBlank()) {
            throw new IllegalArgumentException("Fact value is required");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Fact content is required");
        }
    }
}
