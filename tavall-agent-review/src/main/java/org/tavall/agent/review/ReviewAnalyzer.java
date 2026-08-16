package org.tavall.agent.review;

import java.util.List;
import java.util.Set;

public interface ReviewAnalyzer {
    String id();

    Set<ReviewCategory> categories();

    List<ReviewFinding> analyze(ReviewContext context);
}
