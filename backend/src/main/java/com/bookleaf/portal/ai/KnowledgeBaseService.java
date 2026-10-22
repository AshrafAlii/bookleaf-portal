package com.bookleaf.portal.ai;

import com.bookleaf.portal.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Loads the BookLeaf knowledge base and retrieves only the sections relevant to a
 * given ticket.
 *
 * <p><b>Why retrieval matters for cost:</b> the full knowledge base is roughly
 * 1,200 tokens. Sending all of it on every draft would dominate the prompt.
 * Instead we score each section against the ticket text and send the top 2–3
 * sections plus the (short, always-relevant) tone guidelines — typically ~350
 * tokens instead of 1,200.</p>
 *
 * <p>The matching is intentionally simple and dependency-free: a lexical overlap
 * score with title hits weighted above body hits. It is deterministic, costs
 * nothing to run, and is easy to justify in a code review. Swapping in a vector
 * store later only requires changing this class.</p>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** Always included in a drafting prompt: it defines how we speak, not what we know. */
    private static final String TONE_SECTION = "Communication Tone Guidelines";

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "with", "that", "this", "have", "has", "was", "were", "are", "you", "your",
            "our", "we", "they", "their", "but", "not", "can", "will", "would", "should", "could", "from",
            "about", "into", "than", "them", "then", "there", "these", "those", "when", "what", "which",
            "who", "how", "why", "been", "being", "get", "got", "also", "any", "all", "its", "i'm",
            "i've", "my", "me", "am", "so", "if", "do", "did", "does", "done", "just", "very", "much",
            "because", "after", "before", "still", "over", "under", "out", "up", "down", "one", "two"));

    private final List<KnowledgeSection> sections;
    private final String version;

    public KnowledgeBaseService(AppProperties properties, ResourceLoader resourceLoader) {
        this.sections = parse(load(resourceLoader, properties.getAi().getKnowledgeBasePath()));
        this.version = Integer.toHexString(sections.stream()
                .mapToInt(section -> section.body().hashCode())
                .reduce(17, (a, b) -> a * 31 + b));
        log.info("Loaded knowledge base: {} sections (~{} tokens total), version {}",
                sections.size(), totalTokens(), version);
    }

    private String load(ResourceLoader resourceLoader, String path) {
        try (InputStream in = resourceLoader.getResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read knowledge base from " + path, ex);
        }
    }

    /**
     * Parses the markdown knowledge base into sections. A section starts at a
     * {@code ## } heading; everything up to the next heading is its body.
     */
    private List<KnowledgeSection> parse(String markdown) {
        List<KnowledgeSection> parsed = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (String rawLine : markdown.split("\\R")) {
            String line = rawLine.strip();
            if (line.startsWith("## ")) {
                if (currentTitle != null) {
                    parsed.add(buildSection(currentTitle, currentBody.toString()));
                }
                currentTitle = line.substring(3).strip();
                currentBody.setLength(0);
                continue;
            }
            if (currentTitle == null || line.startsWith("# ")) {
                continue;
            }
            currentBody.append(line).append('\n');
        }
        if (currentTitle != null) {
            parsed.add(buildSection(currentTitle, currentBody.toString()));
        }
        return List.copyOf(parsed);
    }

    private KnowledgeSection buildSection(String title, String body) {
        String cleaned = body.strip();
        return new KnowledgeSection(title, cleaned, approxTokens(cleaned), extractKeywords(cleaned));
    }

    /** Rough token estimate (~4 characters per token) — good enough for budgeting. */
    public static int approxTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }

    private List<String> extractKeywords(String body) {
        Set<String> keywords = new HashSet<>();
        for (String token : body.toLowerCase(Locale.ROOT).split("[^a-z0-9₹]+")) {
            if (token.length() < 4 || STOP_WORDS.contains(token)) {
                continue;
            }
            keywords.add(token);
        }
        return List.copyOf(keywords);
    }

    /**
     * Selects the sections most relevant to the supplied text.
     *
     * @param text        ticket subject + description
     * @param maxSections how many policy sections to return (the tone guide is added on top)
     */
    public Retrieval retrieve(String text, int maxSections) {
        List<String> queryTerms = tokenize(text);

        List<Scored> scored = new ArrayList<>();
        for (KnowledgeSection section : sections) {
            // The tone guide is added separately, and the calibration examples are
            // passed to the model as their own block — neither belongs in the
            // retrieved policy context.
            if (TONE_SECTION.equals(section.title()) || section.title().toLowerCase(Locale.ROOT).startsWith("example - ")) {
                continue;
            }
            int score = score(section, queryTerms);
            if (score > 0) {
                scored.add(new Scored(section, score));
            }
        }

        // Highest score first; ties broken by title so the result is deterministic.
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.section().title()));

        List<KnowledgeSection> selected = scored.stream()
                .limit(Math.max(1, maxSections))
                .map(Scored::section)
                .toList();

        List<KnowledgeSection> withTone = new ArrayList<>(selected);
        sections.stream()
                .filter(section -> TONE_SECTION.equals(section.title()))
                .findFirst()
                .ifPresent(withTone::add);

        int tokens = withTone.stream().mapToInt(KnowledgeSection::approxTokens).sum();
        return new Retrieval(withTone, tokens, version);
    }

    /** Title hits count for more than body hits — the heading names the policy area. */
    private int score(KnowledgeSection section, List<String> queryTerms) {
        String lowerTitle = section.title().toLowerCase(Locale.ROOT);
        String lowerBody = section.body().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : queryTerms) {
            if (lowerTitle.contains(term)) {
                score += 4;
            }
            if (lowerBody.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9₹]+")) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            terms.add(token);
        }
        return terms;
    }

    /**
     * Finds the calibration example for a category, if the knowledge base has one.
     * Example section titles start with {@code "Example - "} followed by the
     * category label, e.g. {@code "Example - Royalty & Payments - Payment Overdue"}.
     */
    public List<KnowledgeSection> calibrationExamples(String categoryLabel) {
        if (categoryLabel == null || categoryLabel.isBlank()) {
            return List.of();
        }
        String needle = categoryLabel.toLowerCase(Locale.ROOT);
        return sections.stream()
                .filter(section -> section.title().toLowerCase(Locale.ROOT).startsWith("example - "))
                .filter(section -> section.title().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    public List<KnowledgeSection> allSections() {
        return sections;
    }

    public int totalTokens() {
        return sections.stream().mapToInt(KnowledgeSection::approxTokens).sum();
    }

    public String version() {
        return version;
    }

    private record Scored(KnowledgeSection section, int score) {
    }

    /**
     * @param sections retrieved sections
     * @param tokens   approximate prompt cost of the retrieved context
     * @param version  content hash of the KB, used as part of the AI cache key
     */
    public record Retrieval(List<KnowledgeSection> sections, int tokens, String version) {

        public String render() {
            StringBuilder sb = new StringBuilder();
            for (KnowledgeSection section : sections) {
                sb.append("### ").append(section.title()).append('\n');
                sb.append(section.body()).append("\n\n");
            }
            return sb.toString().strip();
        }
    }
}
