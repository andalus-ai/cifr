package tech.kayys.cifr.engine.loop;

import java.util.List;

/**
 * Clarifying question directed to the user.
 */
public record UserQuestion(
    String questionId,
    String text,
    List<String> options
) {
    public UserQuestion {
        options = options != null ? List.copyOf(options) : List.of();
    }

    public static UserQuestion of(String id, String text) {
        return new UserQuestion(id, text, List.of());
    }
}
